package com.precise.test.repo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 被测项目实体（表 project）
 *
 * <p>字段与统一数据模型一致：id / name / gitUrl / branch / buildType / status / createdAt</p>
 */
@Data
@TableName("project")
public class Project {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名称 */
    private String name;

    /** Git 仓库地址 */
    private String gitUrl;

    /** 默认分支 */
    private String branch;

    /** 构建类型：MAVEN / GRADLE / NPM */
    private String buildType;

    /** 状态：active 启用 / disabled 停用（统一模型 VARCHAR 枚举） */
    private String status;

    /** 被测服务地址（执行用例时拼接 apiPath 发起真实请求） */
    private String baseUrl;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
