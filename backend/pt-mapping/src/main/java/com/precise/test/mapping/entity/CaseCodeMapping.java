package com.precise.test.mapping.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用例-代码关联实体（表 case_code_mapping）
 *
 * <p>字段与统一数据模型一致：
 * id / testCaseId / codeUnitId / relationType / confidence / createdAt</p>
 *
 * <p>M4 TODO: 由 pt-mapping 建立用例与代码单元之间的关联关系。</p>
 */
@Data
@TableName("case_code_mapping")
public class CaseCodeMapping {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用例 ID */
    private Long testCaseId;

    /** 代码单元 ID */
    private Long codeUnitId;

    /** 关联类型：direct直接 / called调用链 */
    private String relationType;

    /** 置信度：static静态分析 / dynamic动态覆盖 */
    private String confidence;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
