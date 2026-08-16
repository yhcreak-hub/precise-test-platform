package com.precise.test.auth.controller;

import com.precise.test.auth.dto.LoginRequest;
import com.precise.test.auth.dto.LoginResponse;
import com.precise.test.auth.service.AuthService;
import com.precise.test.common.api.Result;
import com.precise.test.common.constant.CommonConstants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录（白名单接口，无需携带 token）
     *
     * @param request 登录请求 {username, password}
     * @return {token, username, role}
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Validated @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 获取当前登录用户信息（由 JWT 拦截器从 token 解析后写入 request attribute）
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        Map<String, Object> user = new HashMap<>(4);
        user.put("username", request.getAttribute(CommonConstants.REQUEST_ATTR_USERNAME));
        user.put("role", request.getAttribute(CommonConstants.REQUEST_ATTR_ROLE));
        return Result.success(user);
    }
}
