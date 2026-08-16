package com.precise.test.common.constant;

/**
 * 通用常量
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** 认证请求头名称 */
    public static final String TOKEN_HEADER = "Authorization";

    /** Bearer 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** JWT 拦截器写入 request attribute：登录用户名 */
    public static final String REQUEST_ATTR_USERNAME = "loginUsername";

    /** JWT 拦截器写入 request attribute：角色 */
    public static final String REQUEST_ATTR_ROLE = "loginRole";

    /** 默认管理员用户名（启动时自动创建） */
    public static final String DEFAULT_ADMIN_USERNAME = "admin";

    /** 默认管理员密码（启动时自动创建，BCrypt 加密存储） */
    public static final String DEFAULT_ADMIN_PASSWORD = "123456";

    /** 实体状态：启用（统一模型 VARCHAR 枚举，见 sql/init.sql 约定） */
    public static final String STATUS_ENABLED = "active";

    /** 实体状态：停用 */
    public static final String STATUS_DISABLED = "disabled";

    /** 默认管理员角色 */
    public static final String ROLE_ADMIN = "admin";
}
