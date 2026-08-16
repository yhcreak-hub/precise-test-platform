package com.precise.test.web.controller;

import com.precise.test.common.api.Result;
import com.precise.test.common.api.ResultCode;
import com.precise.test.common.constant.CommonConstants;
import com.precise.test.common.exception.BusinessException;
import com.precise.test.repo.dto.ProjectCreateRequest;
import com.precise.test.repo.entity.Project;
import com.precise.test.repo.service.ProjectService;
import com.precise.test.task.entity.GenTask;
import com.precise.test.task.service.GenTaskService;
import com.precise.test.web.service.AsyncImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 项目导入与分析接口
 * <p>导入仅做「项目创建 + Git 仓库可达性检查」，不自动分析；
 * 接口分析与用例生成由项目列表页的按钮手动触发（分析为异步，避免大项目超时）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ImportController {

    private final ProjectService projectService;
    private final GenTaskService genTaskService;
    private final AsyncImportService asyncImportService;

    /**
     * 导入项目：创建项目 + 检查 Git 仓库是否存在（git ls-remote）
     *
     * @param request 项目信息
     * @return { project, message }（不自动分析，分析由项目列表按钮触发）
     */
    @PostMapping("/import")
    public Result<Map<String, Object>> importProject(@Validated @RequestBody ProjectCreateRequest request) {
        // 1. Git 仓库可达性检查（导入前验证，避免录入无效仓库）
        String gitUrl = request.getGitUrl() == null ? "" : request.getGitUrl().trim();
        if (!StringUtils.hasText(gitUrl)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Git 仓库地址不能为空");
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "ls-remote", "--heads", gitUrl);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "Git 仓库不存在或不可访问: " + gitUrl + "（" + output.trim() + "）");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Git 仓库检查失败: " + e.getMessage());
        }

        // 2. 创建项目
        Project project = new Project();
        project.setName(request.getName());
        project.setGitUrl(gitUrl);
        project.setBranch(StringUtils.hasText(request.getBranch()) ? request.getBranch() : "master");
        project.setBuildType(StringUtils.hasText(request.getBuildType()) ? request.getBuildType() : "maven");
        project.setBaseUrl(request.getBaseUrl() == null ? "" : request.getBaseUrl().trim());
        project.setStatus(CommonConstants.STATUS_ENABLED);
        projectService.save(project);
        log.info("导入项目（已校验仓库可达）: id={}, name={}, url={}", project.getId(), project.getName(), gitUrl);

        Map<String, Object> data = new HashMap<>();
        data.put("project", project);
        data.put("message", "项目导入成功，请在项目列表点击「分析接口」开始接口识别");
        return Result.success(data);
    }

    /**
     * 异步分析接口（clone + 扫描，避免大项目超时）
     *
     * @param id 项目 ID
     * @return { taskId }，前端轮询进度
     */
    @PostMapping("/{id}/analyze")
    public Result<Map<String, Object>> analyzeProject(@PathVariable("id") Long id) {
        Project project = projectService.getById(id);
        if (project == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目不存在");
        }
        GenTask task = genTaskService.createTask(id, "scan");
        asyncImportService.runAnalyzePipeline(task.getId(), id, project.getGitUrl(), project.getBranch());

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        data.put("message", "接口分析已开始（后台执行）");
        return Result.success(data);
    }

    /**
     * 查询任务进度
     *
     * @param taskId 生成任务 ID
     * @return { status, progress, finishedAt, logUrl }
     */
    @GetMapping("/import-status/{taskId}")
    public Result<Map<String, Object>> importStatus(@PathVariable("taskId") Long taskId) {
        GenTask task = genTaskService.getById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "任务不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        data.put("status", task.getStatus());
        data.put("progress", task.getProgress());
        data.put("finishedAt", task.getFinishedAt());
        data.put("logUrl", task.getLogUrl());
        return Result.success(data);
    }
}
