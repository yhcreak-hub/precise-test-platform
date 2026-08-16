package com.precise.test.mapping.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.precise.test.analyze.entity.ApiDefinition;
import com.precise.test.analyze.entity.CodeUnit;
import com.precise.test.analyze.mapper.ApiDefinitionMapper;
import com.precise.test.analyze.mapper.CodeUnitMapper;
import com.precise.test.analyze.util.CodeFetcher;
import com.precise.test.analyze.util.GitDiffAnalyzer;
import com.precise.test.analyze.util.MethodCallGraphBuilder;
import com.precise.test.casegen.entity.TestCase;
import com.precise.test.casegen.mapper.TestCaseMapper;
import com.precise.test.mapping.entity.CaseCodeMapping;
import com.precise.test.mapping.mapper.CaseCodeMappingMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 版本变更分析与用例筛选服务（M5 增强：调用链影响面）
 * <p>核心链路：</p>
 * <pre>
 *   1. 完整 clone 被测仓库，切换两个版本
 *   2. git diff 提取变更 Java 文件
 *   3. 对每个变更类：
 *      a. Controller 类 → 直接命中（变更的就是接口本身）
 *      b. Service/DAO/Util 等 → 方法调用图反向 BFS → 找到受影响 Controller
 *   4. Controller 方法 ↔ code_unit（className.methodName 匹配）
 *   5. 反查 case_code_mapping → 命中 test_case 列表
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeAnalysisService {

    private final CodeUnitMapper codeUnitMapper;
    private final CaseCodeMappingMapper caseCodeMappingMapper;
    private final TestCaseMapper testCaseMapper;
    private final ApiDefinitionMapper apiDefinitionMapper;

    /**
     * 分析两个版本间的代码变更，筛选需要回归的用例
     *
     * @param projectId    项目 ID
     * @param gitUrl       被测仓库地址
     * @param baseVersion  基线版本（分支/commit）
     * @param nowVersion   当前版本
     * @return 变更分析结果（变更文件 / 受影响接口 / 命中用例）
     */
    public ChangeAnalysisResult analyze(Long projectId, String gitUrl, String baseVersion, String nowVersion) {
        try {
            // 1. 完整 clone + 对比两个版本
            Path repoRoot = CodeFetcher.fetchFull(projectId, gitUrl);
            List<GitDiffAnalyzer.ChangedFile> changedFiles = GitDiffAnalyzer.diffJavaFiles(repoRoot, baseVersion, nowVersion);

            // 2. 切换到 nowVersion，构建方法调用关系图
            CodeFetcher.checkout(repoRoot, nowVersion);
            MethodCallGraphBuilder callGraph = new MethodCallGraphBuilder();
            callGraph.build(repoRoot);

            // 3. 解析变更类 → 确定受影响 Controller 方法（类.方法）
            Set<String> affectedControllerMethods = new LinkedHashSet<>();
            Map<String, String> changedClassInfo = new LinkedHashMap<>(); // 类 -> 变更类型
            for (GitDiffAnalyzer.ChangedFile cf : changedFiles) {
                // 解析变更类全名（含非 Controller 类）
                String className = resolveClassName(repoRoot, cf);
                if (className == null) {
                    continue;
                }
                changedClassInfo.putIfAbsent(className, cf.getChangeType());

                if (callGraph.isControllerClass(className)) {
                    // a. 变更类本身是 Controller → 该类全部方法（接口入口）
                    List<String> methods = GitDiffAnalyzer.resolveChangedMethods(repoRoot, cf, baseVersion, nowVersion);
                    if (methods.isEmpty()) {
                        // 无法定位方法，按类处理：查找该类的全部 code_unit
                        addAllControllerMethods(className, affectedControllerMethods, callGraph, methods);
                    } else {
                        for (String m : methods) {
                            affectedControllerMethods.add(className + "." + m);
                        }
                    }
                } else {
                    // b. 普通类变更 → 调用链反向找受影响 Controller
                    List<String> changedMethods = GitDiffAnalyzer.resolveChangedMethods(repoRoot, cf, baseVersion, nowVersion);
                    List<String> affected = callGraph.findAffectedControllers(className, changedMethods, 5);
                    affectedControllerMethods.addAll(affected);
                }
            }

            // 4. 受影响 Controller 方法 ↔ code_unit 匹配
            List<CodeUnit> matchedUnits = new ArrayList<>();
            for (String controllerMethod : affectedControllerMethods) {
                String cls = controllerMethod.substring(0, controllerMethod.lastIndexOf('.'));
                String method = controllerMethod.substring(controllerMethod.lastIndexOf('.') + 1);
                List<CodeUnit> units = codeUnitMapper.selectList(new LambdaQueryWrapper<CodeUnit>()
                        .eq(CodeUnit::getProjectId, projectId)
                        .eq(CodeUnit::getClassName, cls)
                        .eq(CodeUnit::getMethodName, method));
                matchedUnits.addAll(units);
            }

            // 4.5 构建受影响接口列表（含用例覆盖状态，用于前端展示与补用例）
            Map<String, List<ApiDefinition>> affectedApis = new LinkedHashMap<>();
            for (String controllerMethod : affectedControllerMethods) {
                String cls = controllerMethod.substring(0, controllerMethod.lastIndexOf('.'));
                String method = controllerMethod.substring(controllerMethod.lastIndexOf('.') + 1);
                List<ApiDefinition> apis = apiDefinitionMapper.selectList(new LambdaQueryWrapper<ApiDefinition>()
                        .eq(ApiDefinition::getProjectId, projectId)
                        .eq(ApiDefinition::getControllerClass, cls)
                        .eq(ApiDefinition::getControllerMethod, method));
                if (!apis.isEmpty()) {
                    affectedApis.put(controllerMethod, apis);
                }
            }

            // 5. 反查映射 → 命中用例（去重）
            Map<Long, TestCase> matchedCases = new LinkedHashMap<>();
            for (CodeUnit unit : matchedUnits) {
                List<CaseCodeMapping> mappings = caseCodeMappingMapper.selectList(
                        new LambdaQueryWrapper<CaseCodeMapping>().eq(CaseCodeMapping::getCodeUnitId, unit.getId()));
                for (CaseCodeMapping m : mappings) {
                    TestCase tc = testCaseMapper.selectById(m.getTestCaseId());
                    if (tc != null) {
                        matchedCases.putIfAbsent(tc.getId(), tc);
                    }
                }
            }

            ChangeAnalysisResult result = new ChangeAnalysisResult();
            result.setBaseVersion(baseVersion);
            result.setNowVersion(nowVersion);
            result.setChangedFileCount(changedFiles.size());
            result.setChangedClasses(new ArrayList<>(changedClassInfo.keySet()));
            result.setAffectedControllerMethods(new ArrayList<>(affectedControllerMethods));
            result.setMatchedUnits(matchedUnits);
            result.setMatchedCases(new ArrayList<>(matchedCases.values()));
            result.setAffectedApis(affectedApis);
            log.info("变更分析完成: {}..{} 变更文件={} 变更类={} 受影响接口方法={} 命中用例={}",
                    baseVersion, nowVersion, changedFiles.size(), changedClassInfo.size(),
                    affectedControllerMethods.size(), matchedCases.size());
            return result;
        } catch (Exception e) {
            log.error("变更分析失败: projectId={}, {}..{}", projectId, baseVersion, nowVersion, e);
            throw new RuntimeException("变更分析失败: " + e.getMessage(), e);
        }
    }

    /** 解析变更文件对应的类全名（Controller 或普通类） */
    private String resolveClassName(Path repoRoot, GitDiffAnalyzer.ChangedFile cf) {
        // 优先用 GitDiffAnalyzer 的 Controller 解析（含删除文件推断）
        String controller = GitDiffAnalyzer.resolveControllerClass(repoRoot, cf);
        if (controller != null) {
            return controller;
        }
        // 非 Controller 类：从文件路径推断（删除文件无内容，需推断）
        return inferClassFromPath(cf.getFilePath());
    }

    /** 变更方法未知时：把类下全部方法加入（通过 code_unit 类名匹配） */
    private void addAllControllerMethods(String className, Set<String> affected,
                                         MethodCallGraphBuilder callGraph, List<String> methods) {
        if (methods.isEmpty()) {
            // 无法定位方法：按类处理（下方 code_unit 匹配会退化为类匹配）
            affected.add(className + ".#");
        } else {
            for (String m : methods) {
                affected.add(className + "." + m);
            }
        }
    }

    /** 从文件路径推断类全名 */
    private static String inferClassFromPath(String filePath) {
        int idx = filePath.indexOf("/java/");
        if (idx < 0) {
            return null;
        }
        String path = filePath.substring(idx + "/java/".length());
        if (path.endsWith(".java")) {
            path = path.substring(0, path.length() - ".java".length());
        }
        return path.replace('/', '.');
    }

    /** 变更分析结果 */
    @Data
    public static class ChangeAnalysisResult {
        /** 基线版本 */
        private String baseVersion;
        /** 当前版本 */
        private String nowVersion;
        /** 变更的 Java 文件数 */
        private int changedFileCount;
        /** 变更的类全名列表 */
        private List<String> changedClasses;
        /** 受影响（直接或经调用链）的 Controller 方法列表 */
        private List<String> affectedControllerMethods;
        /** 受影响接口：Controller方法 -> 接口定义列表 */
        private Map<String, List<ApiDefinition>> affectedApis;
        /** 匹配到的代码单元 */
        private List<CodeUnit> matchedUnits;
        /** 命中用例（需回归的用例） */
        private List<TestCase> matchedCases;
    }
}
