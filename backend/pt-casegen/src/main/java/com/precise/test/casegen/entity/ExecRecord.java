package com.precise.test.casegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用例执行记录（批次）
 * <p>记录批量执行（如变更分析后的精准回归）的整体结果与统计。</p>
 */
@Data
@TableName("exec_record")
public class ExecRecord {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 ID */
    private Long projectId;

    /** 触发来源：manual / change_analysis / all */
    private String source;

    /** 基线版本（变更分析触发时） */
    private String baseVersion;

    /** 当前版本（变更分析触发时） */
    private String nowVersion;

    /** 用例总数 */
    private Integer total;

    /** 通过数 */
    private Integer passed;

    /** 失败数 */
    private Integer failed;

    /** 错误数 */
    private Integer errorCount;

    /** 总耗时 ms */
    private Long costMs;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
