package com.precise.test.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（基于 jjwt 0.12.x 链式 API）
 *
 * <p>生成与解析 token：subject 为用户名，附带 role 声明；签名算法由密钥长度自动推导（HS256）。</p>
 * <p>注意：配置项 {@code precise.jwt.secret} 必须 ≥ 32 字节，否则 HS256 签名会抛出异常。</p>
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${precise.jwt.secret}") String secret,
                   @Value("${precise.jwt.expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    /** 生成 token */
    public String generateToken(String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token，返回 Claims
     *
     * @throws JwtException  签名不合法 / token 已过期
     * @throws IllegalArgumentException  token 为空或格式错误
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 校验 token 是否有效（签名与过期时间） */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
