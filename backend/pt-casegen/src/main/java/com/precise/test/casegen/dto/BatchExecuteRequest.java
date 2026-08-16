package com.precise.test.casegen.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量执行用例请求
 */
@Data
public class BatchExecuteRequest {

    /** 用例 ID 列表 */
    private List<Long> caseIds;

    /** 触发来源：manual / change_analysis / all */
    private String source = "manual";

    /** 基线版本（变更分析触发时） */
    private String baseVersion;

    /** 当前版本（变更分析触发时） */
    private String nowVersion;
}
