package com.precise.test.web.service;

import com.precise.test.analyze.service.ApiDefinitionService;
import com.precise.test.analyze.service.CodeUnitService;
import com.precise.test.analyze.util.CodeFetcher;
import com.precise.test.casegen.service.TestCaseService;
import com.precise.test.mapping.service.CaseCodeMappingService;
import com.precise.test.task.service.GenTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * 异步项目导入服务
 * <p>创建项目后异步执行「接口分析 → 用例生成 → 用例-代码关联」，
 * 通过 gen_task 记录进度（queued/running/success/failed），前端轮询查询。
 * 解决大项目 clone/扫描耗时导致的同步请求超时问题。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncImportService {

    private final ApiDefinitionService apiDefinitionService;
    private final TestCaseService testCaseService;
    private final CodeUnitService codeUnitService;
    private final CaseCodeMappingService caseCodeMappingService;
    private final GenTaskService genTaskService;

    /**
     * 异步执行项目导入分析流水线（完整：扫描+生成+关联）
     *
     * @param taskId    生成任务 ID（进度记录）
     * @param projectId 项目 ID
     * @param gitUrl    被测仓库地址
     * @param branch    分支
     */
    @Async
    public void runImportPipeline(Long taskId, Long projectId, String gitUrl, String branch) {
        try {
            log.info("异步导入流水线开始: taskId={}, projectId={}", taskId, projectId);

            // 1. 接口分析（clone + 扫描），进度 10-50
            genTaskService.updateProgress(taskId, 10);
            Path sourceRoot = CodeFetcher.fetch(projectId, gitUrl, branch);
            genTaskService.updateProgress(taskId, 40);
            int apiCount = apiDefinitionService.importFromProject(projectId, sourceRoot);
            genTaskService.updateProgress(taskId, 55);

            // 2. 用例生成，进度 55-80
            int caseCount = testCaseService.generateForProject(projectId);
            genTaskService.updateProgress(taskId, 80);

            // 3. 代码单元 + 用例-代码关联，进度 80-99
            codeUnitService.importFromProject(projectId);
            int mappingCount = caseCodeMappingService.buildMappingForProject(projectId);
            genTaskService.updateProgress(taskId, 99);

            genTaskService.finishTask(taskId, true, null);
            log.info("异步导入流水线完成: taskId={}, 接口={}, 用例={}, 映射={}",
                    taskId, apiCount, caseCount, mappingCount);
        } catch (Exception e) {
            log.error("异步导入流水线失败: taskId={}, projectId={}", taskId, projectId, e);
            genTaskService.finishTask(taskId, false, null);
        }
    }

    /**
     * 异步执行接口分析（仅 clone + 扫描，供「分析接口」按钮使用）
     *
     * @param taskId    生成任务 ID
     * @param projectId 项目 ID
     * @param gitUrl    被测仓库地址
     * @param branch    分支
     */
    @Async
    public void runAnalyzePipeline(Long taskId, Long projectId, String gitUrl, String branch) {
        try {
            log.info("异步接口分析开始: taskId={}, projectId={}", taskId, projectId);

            genTaskService.updateProgress(taskId, 10);
            Path sourceRoot = CodeFetcher.fetch(projectId, gitUrl, branch);
            genTaskService.updateProgress(taskId, 50);
            int apiCount = apiDefinitionService.importFromProject(projectId, sourceRoot);
            genTaskService.updateProgress(taskId, 99);

            genTaskService.finishTask(taskId, true, null);
            log.info("异步接口分析完成: taskId={}, 接口={}", taskId, apiCount);
        } catch (Exception e) {
            log.error("异步接口分析失败: taskId={}, projectId={}", taskId, projectId, e);
            genTaskService.finishTask(taskId, false, null);
        }
    }
}
