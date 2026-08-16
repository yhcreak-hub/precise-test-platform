package com.precise.test.auth.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precise.test.common.api.Result;
import com.precise.test.common.api.ResultCode;
import com.precise.test.common.constant.CommonConstants;
import com.precise.test.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器
 * <p>校验除登录接口外的所有 {@code /api/**} 请求头 {@code Authorization: Bearer <token>}，
 * 解析成功后把用户名 / 角色写入 request attribute 供 Controller 使用；失败返回 401。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CORS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader(CommonConstants.TOKEN_HEADER);
        if (StringUtils.hasText(authorization) && authorization.startsWith(CommonConstants.TOKEN_PREFIX)) {
            String token = authorization.substring(CommonConstants.TOKEN_PREFIX.length());
            if (jwtUtil.validateToken(token)) {
                Claims claims = jwtUtil.parseToken(token);
                request.setAttribute(CommonConstants.REQUEST_ATTR_USERNAME, claims.getSubject());
                request.setAttribute(CommonConstants.REQUEST_ATTR_ROLE, claims.get("role", String.class));
                return true;
            }
        }

        // 未登录或 token 失效：返回 401
        log.warn("接口未授权访问: uri={}, method={}", request.getRequestURI(), request.getMethod());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(ResultCode.UNAUTHORIZED, "未登录或登录已过期")));
        return false;
    }
}
