package com.precise.test.auth.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.precise.test.auth.entity.SysUser;
import com.precise.test.auth.mapper.SysUserMapper;
import com.precise.test.common.constant.CommonConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 默认管理员初始化（幂等）：
 * <p>应用启动时，若 sys_user 表中不存在 admin 用户，则自动插入 admin / 123456（BCrypt 加密）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, CommonConstants.DEFAULT_ADMIN_USERNAME));
        if (count != null && count > 0) {
            log.info("默认管理员账号已存在，跳过初始化: {}", CommonConstants.DEFAULT_ADMIN_USERNAME);
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername(CommonConstants.DEFAULT_ADMIN_USERNAME);
        admin.setPasswordHash(passwordEncoder.encode(CommonConstants.DEFAULT_ADMIN_PASSWORD));
        admin.setRole(CommonConstants.ROLE_ADMIN);
        admin.setStatus(CommonConstants.STATUS_ENABLED);
        sysUserMapper.insert(admin);
        log.info("已自动创建默认管理员账号: {} / {}", CommonConstants.DEFAULT_ADMIN_USERNAME, CommonConstants.DEFAULT_ADMIN_PASSWORD);
    }
}
