package com.precise.test.common.exception;

import com.precise.test.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常：携带业务状态码，由 {@link GlobalExceptionHandler} 统一转换为 {@code Result}
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码 */
    private final int code;

    public BusinessException(String message) {
        this(ResultCode.ERROR, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
