package com.precise.test.repo.dto;

import com.precise.test.repo.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目导入响应（M5 增强）
 * <p>创建项目后自动完成：接口分析 → 用例生成 → 用例-代码关联，返回各步结果。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectImportResult {

    /** 创建的项目 */
    private Project project;

    /** 识别出的接口数量 */
    private int apiCount;

    /** 生成的用例数量 */
    private int caseCount;

    /** 建立的用例-代码关联数量 */
    private int mappingCount;

    /** 提示信息（如分析失败原因） */
    private String message;
}
