package com.precise.test.auth.service;

import com.precise.test.auth.dto.LoginRequest;
import com.precise.test.auth.dto.LoginResponse;

/**
 * 认证服务
 */
public interface AuthService {

    /**
     * 登录：校验用户名密码，成功返回 token + 用户信息
     *
     * @param request 登录请求
     * @return 登录响应
     */
    LoginResponse login(LoginRequest request);
}
