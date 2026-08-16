package com.precise.test.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应：token + 用户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** JWT 令牌 */
    private String token;

    /** 用户名 */
    private String username;

    /** 角色 */
    private String role;
}
