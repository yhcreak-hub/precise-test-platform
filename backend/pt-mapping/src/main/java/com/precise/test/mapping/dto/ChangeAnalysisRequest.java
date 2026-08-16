package com.precise.test.mapping.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 版本变更分析请求
 */
@Data
public class ChangeAnalysisRequest {

    /** 基线版本（分支/commit，如 master） */
    @NotBlank(message = "基线版本不能为空")
    private String baseVersion;

    /** 当前版本（分支/commit，如 dev_xxx） */
    @NotBlank(message = "当前版本不能为空")
    private String nowVersion;
}
