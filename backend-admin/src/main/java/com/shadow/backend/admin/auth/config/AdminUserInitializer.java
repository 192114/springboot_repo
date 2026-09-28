package com.shadow.backend.admin.auth.config;

import com.shadow.backend.admin.auth.entity.AdminUser;
import com.shadow.backend.admin.auth.mapper.AdminUserMapper;
import com.shadow.backend.admin.config.AdminSecurityProperties;
import com.shadow.backend.common.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Admin 种子用户初始化器
 * <p>
 * 首次启动时自动创建默认管理员账号（如不存在）：用户名 admin，口令取自
 * {@code app.security.initial-admin-password}（dev 默认 admin123；prod 由环境变量
 * ADMIN_INITIAL_PASSWORD 注入，缺失时由启动校验拦截）。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminUserInitializer implements CommandLineRunner {

    private final AdminUserMapper adminUserMapper;
    private final PasswordUtil passwordUtil;
    private final AdminSecurityProperties securityProperties;

    @Override
    public void run(String... args) {
        if (adminUserMapper.existsByUsernameIncludingDeleted("admin")) {
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setUsername("admin");
        admin.setPassword(passwordUtil.hash(securityProperties.getInitialAdminPassword()));
        admin.setNickname("超级管理员");
        admin.setEmail("admin@shadow.com");
        admin.setStatus(1);
        try {
            adminUserMapper.insert(admin);
        } catch (DuplicateKeyException ex) {
            if (!adminUserMapper.existsByUsernameIncludingDeleted("admin")) {
                throw ex;
            }
            return;
        }
        log.warn("已创建默认管理员账号: admin（口令来自初始配置，请尽快修改密码）");
    }
}
