package com.precise.test.analyze.model;

/**
 * 接口支持的 HTTP 方法枚举（与 Spring 的 RequestMethod 一一对应）。
 *
 * <p>接口定义实体的 {@code httpMethod} 字段存储其 {@code name()}（如 "GET"），
 * 与统一数据模型中的约定保持一致。
 */
public enum ApiHttpMethod {

    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS,
    TRACE;

    /**
     * 从字符串解析 HTTP 方法（大小写不敏感），常用于解析
     * {@code @RequestMapping(method = RequestMethod.GET)} 中的枚举常量名。
     *
     * @param name 方法名，如 "GET"、"get"、"RequestMethod.GET"
     * @return 对应枚举；无法识别时返回 {@code null}
     */
    public static ApiHttpMethod fromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        // 兼容 "RequestMethod.GET" 这类全限定枚举常量写法
        String segment = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
        try {
            return valueOf(segment.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
