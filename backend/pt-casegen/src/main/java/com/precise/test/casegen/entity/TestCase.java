package com.precise.test.casegen.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试用例实体（表 test_case）
 *
 * <p>字段与统一数据模型一致：
 * id / projectId / apiDefinitionId / title / requestJson / assertsJson /
 * scenarioType / source / confidence / status / createdAt / updatedAt</p>
 *
 * <p>M3 TODO: 由 pt-casegen 基于接口 Schema 自动生成用例后写入本表。</p>
 */
@Data
@TableName("test_case")
public class TestCase {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    private Long projectId;

    /** 关联接口定义 ID */
    private Long apiDefinitionId;

    /** 用例标题 */
    private String title;

    /** 请求报文（JSON 字符串） */
    private String requestJson;

    /** 断言（JSON 字符串） */
    private String assertsJson;

    /** 请求头（JSON 字符串，可选：如 {"token":"xxx"}） */
    private String headersJson;

    /** 场景类型：normal/required/boundary/exception/business */
    private String scenarioType;

    /** 来源：rule/ai/manual */
    private String source;

    /** 置信度：high/medium/low */
    private String confidence;

    /** 状态：draft/active/deprecated */
    private String status;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（插入/更新时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
