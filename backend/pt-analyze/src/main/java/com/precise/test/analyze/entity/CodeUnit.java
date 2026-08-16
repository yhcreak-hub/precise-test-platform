package com.precise.test.analyze.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 代码单元实体（表 code_unit）
 *
 * <p>字段与统一数据模型一致：
 * id / projectId / className / methodName / signature / filePath / lineNo / codeHash</p>
 *
 * <p>M2 TODO: 由 pt-analyze 解析被测项目源码，提取方法级代码单元后写入本表。</p>
 */
@Data
@TableName("code_unit")
public class CodeUnit {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    private Long projectId;

    /** 类全名 */
    private String className;

    /** 方法名 */
    private String methodName;

    /** 方法签名 */
    private String signature;

    /** 源码文件路径 */
    private String filePath;

    /** 方法起始行号 */
    private Integer lineNo;

    /** 代码哈希（用于变更检测） */
    private String codeHash;
}
