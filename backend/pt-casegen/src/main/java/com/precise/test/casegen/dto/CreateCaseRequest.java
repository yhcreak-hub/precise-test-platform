package com.precise.test.casegen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 手动新增用例请求
 */
@Data
public class CreateCaseRequest {

    /** 所属接口定义 ID */
    @NotNull(message = "接口不能为空")
    private Long apiDefinitionId;

    /** 用例标题 */
    @NotBlank(message = "用例标题不能为空")
    private String title;

    /** 请求参数 JSON */
    private String requestJson;

    /** 断言 JSON */
    private String assertsJson;

    /** 请求头 JSON（可选） */
    private String headersJson;

    /** 场景类型：normal/required/boundary/exception/business */
    private String scenarioType;
}
