package com.precise.test.repo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.precise.test.analyze.entity.ApiDefinition;
import com.precise.test.analyze.entity.CodeUnit;
import com.precise.test.analyze.service.ApiDefinitionService;
import com.precise.test.analyze.service.CodeUnitService;
import com.precise.test.analyze.util.CodeFetcher;
import com.precise.test.casegen.dto.BatchExecuteRequest;
import com.precise.test.casegen.dto.CreateCaseRequest;
import com.precise.test.casegen.dto.UpdateCaseRequest;
import com.precise.test.casegen.engine.CaseExecutor;
import com.precise.test.casegen.entity.ExecRecord;
import com.precise.test.casegen.entity.TestCase;
import com.precise.test.casegen.service.ExecRecordService;
import com.precise.test.casegen.service.TestCaseService;
import com.precise.test.common.api.Result;
import com.precise.test.common.api.ResultCode;
import com.precise.test.common.constant.CommonConstants;
import com.precise.test.common.exception.BusinessException;
import com.precise.test.mapping.dto.ChangeAnalysisRequest;
import com.precise.test.mapping.entity.CaseCodeMapping;
import com.precise.test.mapping.service.CaseCodeMappingService;
import com.precise.test.mapping.service.ChangeAnalysisService;
import com.precise.test.repo.dto.PipelineTriggerResponse;
import com.precise.test.repo.dto.ProjectCreateRequest;
import com.precise.test.repo.dto.ProjectImportResult;
import com.precise.test.repo.dto.ProjectPageQuery;
import com.precise.test.repo.entity.Project;
import com.precise.test.repo.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 被测项目管理接口
 * <p>M2：pipeline 从占位升级为真实执行——拉取被测项目源码 → 接口识别引擎扫描 → 接口清单落库。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ApiDefinitionService apiDefinitionService;
    private final TestCaseService testCaseService;
    private final ExecRecordService execRecordService;
    private final CodeUnitService codeUnitService;
    private final CaseCodeMappingService caseCodeMappingService;
    private final ChangeAnalysisService changeAnalysisService;

    /**
     * 新增项目
     *
     * @param request 项目信息：name / gitUrl / branch / buildType
     * @return 创建后的项目实体
     */
    @PostMapping
    public Result<Project> create(@Validated @RequestBody ProjectCreateRequest request) {
        Project project = new Project();
        project.setName(request.getName());
        project.setGitUrl(request.getGitUrl());
        project.setBranch(StringUtils.hasText(request.getBranch()) ? request.getBranch() : "master");
        project.setBuildType(StringUtils.hasText(request.getBuildType()) ? request.getBuildType() : "maven");
        project.setBaseUrl(request.getBaseUrl() == null ? "" : request.getBaseUrl().trim());
        project.setStatus(CommonConstants.STATUS_ENABLED);
        projectService.save(project);
        log.info("新增项目: id={}, name={}", project.getId(), project.getName());
        return Result.success(project);
    }

    /**
     * 项目分页列表
     *
     * @param query page / size / name（可选模糊搜索）
     * @return 分页结果 Page&lt;Project&gt;：records / total / current / size
     */
    @GetMapping
    public Result<Page<Project>> page(ProjectPageQuery query) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .like(StringUtils.hasText(query.getName()), Project::getName, query.getName())
                .orderByDesc(Project::getId);
        Page<Project> page = projectService.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return Result.success(page);
    }

    /**
     * 删除项目（级联清理：项目 + 其接口定义）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        boolean removed = projectService.removeById(id);
        if (!removed) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在或已删除");
        }
        // TODO(M3): 级联删除 api_definition / test_case / code_unit / case_code_mapping
        log.info("删除项目: id={}", id);
        return Result.success();
    }

    /**
     * 触发流水线（M2 真实实现，同步执行）
     * <p>步骤：拉取被测项目源码(git clone) → JavaParser 接口识别扫描 → 去重落库 api_definition。</p>
     * <p>TODO(M3): 接入 pt-task 异步任务，改为异步执行并支持任务进度查询。</p>
     *
     * @param id 项目 ID
     * @return 导入结果：importedCount 新增数 / totalCount 识别总数
     */
    @PostMapping("/{id}/pipeline")
    public Result<PipelineTriggerResponse> triggerPipeline(@PathVariable("id") Long id) {
        Project project = projectService.getById(id);
        if (project == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        try {
            // 1. 拉取被测项目源码
            Path sourceRoot = CodeFetcher.fetch(id, project.getGitUrl(), project.getBranch());

            // 2. 接口识别 + 去重落库
            List<ApiDefinition> existing = apiDefinitionService.listByProject(id);
            int imported = apiDefinitionService.importFromProject(id, sourceRoot);

            log.info("流水线执行完成: projectId={}, 新增={}, 已有={}", id, imported, existing.size());
            return Result.success(PipelineTriggerResponse.builder()
                    .genTaskId(0L)
                    .importedCount(imported)
                    .totalCount(existing.size() + imported)
                    .message("接口识别完成")
                    .build());
        } catch (Exception e) {
            log.error("流水线执行失败: projectId={}", id, e);
            throw new BusinessException(ResultCode.ERROR, "接口识别失败: " + e.getMessage());
        }
    }

    /**
     * 查询项目的接口清单（接口识别产物）
     *
     * @param id 项目 ID
     * @return 接口定义列表（api_path / http_method / controller / 参数结构等）
     */
    @GetMapping("/{id}/apis")
    public Result<List<ApiDefinition>> listApis(@PathVariable("id") Long id) {
        if (projectService.getById(id) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        return Result.success(apiDefinitionService.listByProject(id));
    }

    /**
     * 测试空洞检测（M3）：统计项目下有接口定义但无用例覆盖的接口
     *
     * @param id 项目 ID
     * @return 空洞报告：totalApis / gapApis / gapRate
     */
    @GetMapping("/{id}/gap")
    public Result<TestCaseService.GapReport> detectGap(@PathVariable("id") Long id) {
        if (projectService.getById(id) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        return Result.success(testCaseService.detectGap(id));
    }

    /**
     * 为项目下所有空洞接口生成契约用例（M3，规则引擎）
     * <p>生成后自动建立用例-代码关联，确保变更分析可反查。</p>
     *
     * @param id 项目 ID
     * @return 本次生成的用例数量
     */
    @PostMapping("/{id}/generate-cases")
    public Result<Long> generateCases(@PathVariable("id") Long id) {
        if (projectService.getById(id) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        Long count = (long) testCaseService.generateForProject(id);
        if (count > 0) {
            caseCodeMappingService.buildMappingForProject(id);
        }
        return Result.success(count);
    }

    /**
     * 为单个接口生成契约用例（M5 增强：变更分析中无覆盖用例的接口可补用例）
     * <p>生成后自动建立用例-代码关联，确保再次变更分析能反查到新用例。</p>
     *
     * @param id    项目 ID
     * @param apiId 接口定义 ID
     * @return 本次生成的用例数量
     */
    @PostMapping("/{id}/apis/{apiId}/generate-cases")
    public Result<Long> generateCasesForApi(@PathVariable("id") Long id, @PathVariable("apiId") Long apiId) {
        if (projectService.getById(id) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        Long count = testCaseService.generateCases(apiId, null);
        // 生成后即时建立用例-代码关联（幂等），确保变更分析可反查
        if (count > 0) {
            caseCodeMappingService.buildMappingForApi(id, apiId);
        }
        return Result.success(count);
    }

    /**
     * 分页查询项目下的用例（M3）
     *
     * @param id   项目 ID
     * @param page 页码（从 1 开始）
     * @param size 每页条数
     * @return 分页用例列表
     */
    @GetMapping("/{id}/cases")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<TestCase>> pageCases(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (projectService.getById(id) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        return Result.success(testCaseService.pageByProject(id, page, size));
    }

    /**
     * 按接口分组查询用例（M4）：apiDefinitionId -> 用例列表
     *
     * @param id 项目 ID
     * @return 分组用例
     */
    @GetMapping("/{id}/cases/grouped")
    public Result<Map<Long, List<TestCase>>> groupedCases(@PathVariable("id") Long id) {
        if (projectService.getById(id) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        return Result.success(testCaseService.groupByApi(id));
    }

    /**
     * 编辑用例（M4）：修改标题 / 入参 / 断言 / 请求头
     *
     * @param id 用例 ID
     * @param request 编辑内容（title / requestJson / assertsJson / headersJson，空字段不修改）
     * @return 编辑后的用例
     */
    @PutMapping("/cases/{id}")
    public Result<TestCase> updateCase(@PathVariable("id") Long id,
                                       @RequestBody UpdateCaseRequest request) {
        TestCase updated = testCaseService.updateCase(id, request.getTitle(), request.getRequestJson(),
                request.getAssertsJson(), request.getHeadersJson());
        if (updated == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用例不存在");
        }
        return Result.success(updated);
    }

    /**
     * 手动新增用例（M6）：source 固定为 manual
     * <p>创建后自动建立用例-代码关联，重新生成用例时不会被覆盖（生成仅针对空洞接口）。</p>
     *
     * @param id      项目 ID
     * @param request 用例内容（apiDefinitionId/title/requestJson/assertsJson/headersJson/scenarioType）
     * @return 创建的用例
     */
    @PostMapping("/{id}/cases")
    public Result<TestCase> createCase(@PathVariable("id") Long id,
                                       @Validated @RequestBody CreateCaseRequest request) {
        Project project = projectService.getById(id);
        if (project == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        TestCase created = testCaseService.createManualCase(id, request.getApiDefinitionId(),
                request.getTitle(), request.getRequestJson(), request.getAssertsJson(),
                request.getHeadersJson(), request.getScenarioType());
        // 自动建立用例-代码关联（幂等），确保变更分析可反查
        caseCodeMappingService.buildMappingForApi(id, request.getApiDefinitionId());
        return Result.success(created);
    }

    /**
     * 手动执行用例（M4）：向被测服务发起真实 HTTP 请求并校验断言
     *
     * @param id 用例 ID
     * @return 执行结果（PASS/FAIL/ERROR + 响应体 + 断言明细）
     */
    @PostMapping("/cases/{id}/execute")
    public Result<CaseExecutor.ExecuteResult> executeCase(@PathVariable("id") Long id) {
        TestCase tc = testCaseService.getById(id);
        if (tc == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用例不存在");
        }
        Project project = projectService.getById(tc.getProjectId());
        if (project == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用例所属项目不存在");
        }
        if (project.getBaseUrl() == null || project.getBaseUrl().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目未配置被测服务地址(baseUrl)，无法执行");
        }
        return Result.success(testCaseService.executeCase(id, project.getBaseUrl()));
    }

    /**
     * 批量执行用例并记录结果（M6：执行记录）
     * <p>变更分析命中用例可批量执行，结果落库 exec_record 供查看报告。</p>
     *
     * @param id      项目 ID
     * @param request caseIds + source（manual/change_analysis）+ 版本信息
     * @return 批次执行记录（含统计）
     */
    @PostMapping("/{id}/execute-batch")
    public Result<ExecRecord> executeBatch(@PathVariable("id") Long id,
                                           @RequestBody BatchExecuteRequest request) {
        Project project = projectService.getById(id);
        if (project == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        if (request.getCaseIds() == null || request.getCaseIds().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要执行的用例");
        }
        return Result.success(execRecordService.executeBatch(id, project.getBaseUrl(),
                request.getCaseIds(), request.getSource(), request.getBaseVersion(), request.getNowVersion()));
    }

    /**
     * 分页查询项目的执行记录（M6）
     */
    @GetMapping("/{id}/exec-records")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<ExecRecord>> pageExecRecords(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (projectService.getById(id) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        return Result.success(execRecordService.pageByProject(id, page, size));
    }

    /**
     * 查询执行记录明细（详情报告）
     */
    @GetMapping("/exec-records/{recordId}/detail")
    public Result<Map<String, Object>> execRecordDetail(@PathVariable("recordId") Long recordId) {
        ExecRecord record = execRecordService.getById(recordId);
        if (record == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "执行记录不存在");
        }
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("record", record);
        data.put("details", execRecordService.listDetail(recordId));
        return Result.success(data);
    }

    /**
     * 触发「用例-代码关联」建立（M4）：提取代码单元 → 建立用例↔代码映射
     *
     * @param id 项目 ID
     * @return {codeUnits: 代码单元数, mappings: 关联数}
     */
    @PostMapping("/{id}/build-mapping")
    public Result<Map<String, Integer>> buildMapping(@PathVariable("id") Long id) {
        if (projectService.getById(id) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        int codeUnits = codeUnitService.importFromProject(id);
        int mappings = caseCodeMappingService.buildMappingForProject(id);
        Map<String, Integer> result = new java.util.HashMap<>();
        result.put("codeUnits", codeUnits);
        result.put("mappings", mappings);
        return Result.success(result);
    }

    /**
     * 查询项目的代码单元清单（M4）
     *
     * @param id 项目 ID
     * @return 代码单元列表（Controller 类.方法）
     */
    @GetMapping("/{id}/code-units")
    public Result<List<CodeUnit>> listCodeUnits(@PathVariable("id") Long id) {
        if (projectService.getById(id) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        return Result.success(codeUnitService.listByProject(id));
    }

    /**
     * 查询用例关联的代码单元（M4）：用例 → 接口 → 代码方法
     *
     * @param id 用例 ID
     * @return 该用例关联的代码单元列表
     */
    @GetMapping("/cases/{id}/mapping")
    public Result<List<CodeUnit>> listCaseMapping(@PathVariable("id") Long id) {
        if (testCaseService.getById(id) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用例不存在");
        }
        List<CaseCodeMapping> mappings = caseCodeMappingService.listByTestCase(id);
        List<CodeUnit> units = mappings.stream()
                .map(m -> codeUnitService.getById(m.getCodeUnitId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        return Result.success(units);
    }

    /**
     * 版本变更分析（M5）：对比两个版本的代码差异，筛选需回归的用例
     *
     * @param id      项目 ID
     * @param request baseVersion（基线）/ nowVersion（当前）
     * @return 变更分析结果（变更文件数 / 变更类 / 命中用例）
     */
    @PostMapping("/{id}/analyze-change")
    public Result<ChangeAnalysisService.ChangeAnalysisResult> analyzeChange(
            @PathVariable("id") Long id,
            @Validated @RequestBody ChangeAnalysisRequest request) {
        Project project = projectService.getById(id);
        if (project == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        return Result.success(changeAnalysisService.analyze(id, project.getGitUrl(),
                request.getBaseVersion(), request.getNowVersion()));
    }
}
