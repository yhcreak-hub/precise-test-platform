package com.precise.test.mapping.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.precise.test.analyze.entity.CodeUnit;
import com.precise.test.analyze.mapper.CodeUnitMapper;
import com.precise.test.analyze.util.CodeFetcher;
import com.precise.test.analyze.util.GitDiffAnalyzer;
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
import java.util.List;
import java.util.Map;

/**
 * 版本变更分析与用例筛选服务（M5）
 * <p>核心链路：</p>
 * <pre>
 *   1. 完整 clone 被测仓库，切换两个版本
 *   2. git diff 提取变更 Java 文件 → 解析变更的 Controller 类
 *   3. 变更类 ↔ code_unit（className 匹配）
 *   4. 反查 case_code_mapping → 命中 test_case 列表
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeAnalysisService {

    private final CodeUnitMapper codeUnitMapper;
    private final CaseCodeMappingMapper caseCodeMappingMapper;
    private final TestCaseMapper testCaseMapper;

    /**
     * 分析两个版本间的代码变更，筛选需要回归的用例
     *
     * @param projectId    项目 ID
     * @param gitUrl       被测仓库地址
     * @param baseVersion  基线版本（分支/commit）
     * @param nowVersion   当前版本
     * @return 变更分析结果（变更文件 / 变更类 / 命中用例）
     */
    public ChangeAnalysisResult analyze(Long projectId, String gitUrl, String baseVersion, String nowVersion) {
        try {
            // 1. 完整 clone + 对比两个版本
            Path repoRoot = CodeFetcher.fetchFull(projectId, gitUrl);
            List<GitDiffAnalyzer.ChangedFile> changedFiles = GitDiffAnalyzer.diffJavaFiles(repoRoot, baseVersion, nowVersion);

            // 2. 解析变更的 Controller 类（切换到 nowVersion 读取内容）
            CodeFetcher.checkout(repoRoot, nowVersion);
            Map<String, String> changedClasses = new LinkedHashMap<>(); // 类全名 -> 变更类型
            for (GitDiffAnalyzer.ChangedFile cf : changedFiles) {
                String className = GitDiffAnalyzer.resolveControllerClass(repoRoot, cf);
                if (className != null) {
                    changedClasses.putIfAbsent(className, cf.getChangeType());
                }
            }

            // 3. 变更类 ↔ code_unit 匹配
            List<CodeUnit> matchedUnits = new ArrayList<>();
            for (String className : changedClasses.keySet()) {
                List<CodeUnit> units = codeUnitMapper.selectList(new LambdaQueryWrapper<CodeUnit>()
                        .eq(CodeUnit::getProjectId, projectId)
                        .eq(CodeUnit::getClassName, className));
                matchedUnits.addAll(units);
            }

            // 4. 反查映射 → 命中用例（去重）
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
            result.setChangedClasses(new ArrayList<>(changedClasses.keySet()));
            result.setMatchedUnits(matchedUnits);
            result.setMatchedCases(new ArrayList<>(matchedCases.values()));
            log.info("变更分析完成: {}..{} 变更文件={} 变更类={} 命中用例={}",
                    baseVersion, nowVersion, changedFiles.size(), changedClasses.size(), matchedCases.size());
            return result;
        } catch (Exception e) {
            log.error("变更分析失败: projectId={}, {}..{}", projectId, baseVersion, nowVersion, e);
            throw new RuntimeException("变更分析失败: " + e.getMessage(), e);
        }
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
        /** 变更的 Controller 类全名列表 */
        private List<String> changedClasses;
        /** 匹配到的代码单元 */
        private List<CodeUnit> matchedUnits;
        /** 命中用例（需回归的用例） */
        private List<TestCase> matchedCases;
    }
}
