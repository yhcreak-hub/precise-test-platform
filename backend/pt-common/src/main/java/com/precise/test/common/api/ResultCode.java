package com.precise.test.common.api;

/**
 * 统一业务状态码
 * <p>与前端响应拦截器约定一致：code=200 成功，其余为业务失败。</p>
 */
public final class ResultCode {

    /** 成功 */
    public static final int SUCCESS = 200;
    /** 参数错误 */
    public static final int PARAM_ERROR = 400;
    /** 未登录 / 登录过期 */
    public static final int UNAUTHORIZED = 401;
    /** 系统异常 */
    public static final int ERROR = 500;

    private ResultCode() {
    }
}
