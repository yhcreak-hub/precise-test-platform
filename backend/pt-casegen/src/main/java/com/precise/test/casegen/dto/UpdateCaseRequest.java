package com.precise.test.casegen.dto;

import lombok.Data;

/**
 * 编辑用例请求
 */
@Data
public class UpdateCaseRequest {

    /** 用例标题（可空，空则不修改） */
    private String title;

    /** 请求参数 JSON（可空，空则不修改） */
    private String requestJson;

    /** 断言 JSON（可空，空则不修改） */
    private String assertsJson;

    /** 请求头 JSON（可空，空则不修改） */
    private String headersJson;
}
