package com.precise.test.common.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应结构
 *
 * <pre>
 * {
 *   "code": 200,          // 200 成功 / 400 参数错误 / 401 未登录 / 500 系统异常
 *   "msg":  "success",    // 提示信息
 *   "data": { ... }       // 业务数据
 * }
 * </pre>
 *
 * @param <T> 业务数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码 */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 业务数据 */
    private T data;

    /** 成功（无数据） */
    public static <T> Result<T> success() {
        return success(null);
    }

    /** 成功（带数据） */
    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(ResultCode.SUCCESS)
                .msg("success")
                .data(data)
                .build();
    }

    /** 失败（指定状态码与信息） */
    public static <T> Result<T> error(int code, String msg) {
        return Result.<T>builder()
                .code(code)
                .msg(msg)
                .build();
    }

    /** 失败（默认 500） */
    public static <T> Result<T> error(String msg) {
        return error(ResultCode.ERROR, msg);
    }
}
