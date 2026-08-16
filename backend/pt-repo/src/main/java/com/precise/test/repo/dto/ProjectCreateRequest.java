package com.precise.test.repo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新增项目请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreateRequest {

    /** 项目名称 */
    @NotBlank(message = "项目名称不能为空")
    private String name;

    /** Git 仓库地址 */
    @NotBlank(message = "Git 仓库地址不能为空")
    private String gitUrl;

    /** 默认分支 */
    private String branch;

    /** 构建类型：MAVEN / GRADLE / NPM */
    private String buildType;

    /** 被测服务地址（可选，如 http://localhost:8899） */
    private String baseUrl;
}
