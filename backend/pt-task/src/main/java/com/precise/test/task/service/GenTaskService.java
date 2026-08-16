package com.precise.test.task.service;

import com.precise.test.task.entity.GenTask;

/**
 * 生成任务服务（占位，M2 实现）
 * <p>目标：承载「接口识别 → 用例生成 → 用例-代码映射」异步任务的生命周期管理。</p>
 */
public interface GenTaskService {

    /** 创建任务 */
    GenTask createTask(Long projectId, String type);

    /** 根据任务 ID 查询 */
    GenTask getById(Long id);

    /**
     * 更新任务进度
     *
     * @param taskId   任务 ID
     * @param progress 进度 0-100
     */
    void updateProgress(Long taskId, int progress);

    /**
     * 完成任务（成功/失败），记录日志地址
     *
     * @param taskId   任务 ID
     * @param success  是否成功
     * @param logUrl   任务日志地址（可空）
     */
    void finishTask(Long taskId, boolean success, String logUrl);
}
