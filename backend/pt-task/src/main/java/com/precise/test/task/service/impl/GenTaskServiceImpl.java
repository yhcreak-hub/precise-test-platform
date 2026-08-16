package com.precise.test.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.precise.test.task.entity.GenTask;
import com.precise.test.task.mapper.GenTaskMapper;
import com.precise.test.task.service.GenTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 生成任务服务实现
 * <p>承载「接口识别 → 用例生成 → 用例-代码映射」异步任务的生命周期管理：
 * queued（排队）→ running（执行）→ success/failed（终态）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenTaskServiceImpl implements GenTaskService {

    private final GenTaskMapper genTaskMapper;

    @Override
    public GenTask createTask(Long projectId, String type) {
        GenTask task = new GenTask();
        task.setProjectId(projectId);
        task.setType(type);
        task.setStatus("queued");
        task.setProgress(0);
        genTaskMapper.insert(task);
        log.info("创建生成任务: id={}, projectId={}, type={}", task.getId(), projectId, type);
        return task;
    }

    @Override
    public GenTask getById(Long id) {
        return genTaskMapper.selectById(id);
    }

    @Override
    public void updateProgress(Long taskId, int progress) {
        genTaskMapper.update(null, new LambdaUpdateWrapper<GenTask>()
                .eq(GenTask::getId, taskId)
                .set(GenTask::getStatus, "running")
                .set(GenTask::getProgress, Math.min(progress, 99)));
    }

    @Override
    public void finishTask(Long taskId, boolean success, String logUrl) {
        genTaskMapper.update(null, new LambdaUpdateWrapper<GenTask>()
                .eq(GenTask::getId, taskId)
                .set(GenTask::getStatus, success ? "success" : "failed")
                .set(GenTask::getProgress, success ? 100 : 0)
                .set(GenTask::getLogUrl, logUrl)
                .set(GenTask::getFinishedAt, LocalDateTime.now()));
        log.info("生成任务完成: id={}, success={}", taskId, success);
    }
}
