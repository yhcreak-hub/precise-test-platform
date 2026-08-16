package com.precise.test.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统用户实体（表 sys_user）
 *
 * <p>字段与统一数据模型一致：id / username / passwordHash / role / status</p>
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    private String username;

    /** 密码哈希（BCrypt） */
    private String passwordHash;

    /** 角色：ADMIN / USER */
    private String role;

    /** 状态：active 启用 / disabled 禁用（统一模型 VARCHAR 枚举） */
    private String status;
}
