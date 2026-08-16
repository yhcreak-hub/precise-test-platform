package com.precise.test.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 认证相关配置
 */
@Configuration
public class AuthConfig {

    /**
     * BCrypt 密码编码器（Spring Security Crypto）
     * <p>登录校验与管理员初始化共用同一个 Bean。</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
