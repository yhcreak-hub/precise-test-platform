package com.precise.test.analyze;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JSON 序列化工具（包内私有，仅供识别引擎内部使用）。
 *
 * <p>统一持有 {@link ObjectMapper} 实例。序列化失败时返回兜底值 {@code "{}"}，
 * 保证"参数/返回结构序列化异常"不会中断接口识别主流程（容错策略与扫描一致）。
 */
final class JsonUtils {

    private static final Logger LOG = Logger.getLogger(JsonUtils.class.getName());

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    /**
     * 对象 → JSON 字符串。
     *
     * @param value 待序列化对象（Map / List / 基本类型等）
     * @return JSON 字符串；序列化失败返回 {@code "{}"} 并记录错误日志
     */
    static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            LOG.log(Level.WARNING, "JSON 序列化失败，返回兜底值: " + e.getMessage(), e);
            return "{}";
        }
    }

    /**
     * JSON 字符串 → 对象（供测试与其他模块解析 schema 使用）。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T>  目标泛型
     * @return 反序列化对象
     * @throws IllegalArgumentException JSON 非法时抛出
     */
    static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 解析失败: " + e.getMessage(), e);
        }
    }
}
