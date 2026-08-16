package com.precise.test.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.precise.test.auth.dto.LoginRequest;
import com.precise.test.auth.dto.LoginResponse;
import com.precise.test.auth.entity.SysUser;
import com.precise.test.auth.mapper.SysUserMapper;
import com.precise.test.auth.service.AuthService;
import com.precise.test.common.api.ResultCode;
import com.precise.test.common.constant.CommonConstants;
import com.precise.test.common.exception.BusinessException;
import com.precise.test.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现
 * <p>密码使用 Spring Security 的 BCryptPasswordEncoder 校验。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));

        // 用户不存在 / 被禁用 / 密码不匹配：统一提示，避免泄露账号信息
        if (user == null || user.getStatus() == null
                || !CommonConstants.STATUS_ENABLED.equals(user.getStatus())
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        log.info("用户登录成功: username={}, role={}", user.getUsername(), user.getRole());
        return LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}
