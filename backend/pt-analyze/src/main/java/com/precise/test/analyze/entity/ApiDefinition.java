package com.precise.test.analyze.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口定义实体（表 api_definition）
 *
 * <p>字段与统一数据模型一致：
 * id / projectId / apiPath / httpMethod / paramSchemaJson / responseSchemaJson /
 * controllerClass / controllerMethod / filePath / lineNo / status / createdAt</p>
 *
 * <p>M2 TODO: 由 pt-analyze 实现被测项目源码解析后写入本表。</p>
 */
@Data
@TableName("api_definition")
public class ApiDefinition {

    /** 状态：待确认（接口识别引擎扫描产物默认置为待确认） */
    public static final String STATUS_PENDING = "pending";

    /** 状态：已确认 */
    public static final String STATUS_CONFIRMED = "confirmed";

    /** 状态：已忽略 */
    public static final String STATUS_IGNORED = "ignored";

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    private Long projectId;

    /** 接口路径 */
    private String apiPath;

    /** HTTP 方法：GET / POST / PUT / DELETE */
    private String httpMethod;

    /** 请求参数 Schema（JSON 字符串） */
    private String paramSchemaJson;

    /** 响应 Schema（JSON 字符串） */
    private String responseSchemaJson;

    /** 所属 Controller 类全名 */
    private String controllerClass;

    /** Controller 方法名 */
    private String controllerMethod;

    /** 源码文件路径 */
    private String filePath;

    /** 接口定义行号 */
    private Integer lineNo;

    /** 状态：pending待确认 / confirmed已确认 / ignored已忽略 */
    private String status;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
