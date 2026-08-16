package com.precise.test.repo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流水线触发响应
 * <p>M2 实现：同步执行「拉取代码 → 接口识别 → 落库」，返回导入结果。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineTriggerResponse {

    /** 生成任务 ID（当前为同步执行，暂不涉及异步任务表） */
    private Long genTaskId;

    /** 本次新导入的接口数量 */
    private int importedCount;

    /** 识别到的接口总数（含已存在） */
    private int totalCount;

    /** 提示信息 */
    private String message;
}
