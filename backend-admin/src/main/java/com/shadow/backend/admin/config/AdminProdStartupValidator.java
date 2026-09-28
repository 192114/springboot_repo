package com.shadow.backend.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Profile("prod")
@Component
@RequiredArgsConstructor
public class AdminProdStartupValidator implements InitializingBean {

    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        String password = environment.getProperty("app.security.initial-admin-password");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("生产环境缺少必需配置: app.security.initial-admin-password"
                    + "（请设置环境变量 ADMIN_INITIAL_PASSWORD）");
        }
    }
}
