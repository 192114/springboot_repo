package com.shadow.backend.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生产环境启动校验：必需配置缺失时直接终止启动，避免带弱配置上线。
 */
@Profile("prod")
@Component
@RequiredArgsConstructor
public class ProdStartupValidator implements InitializingBean {

    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        Map<String, String> requiredProperties = new LinkedHashMap<>();
        requiredProperties.put("spring.datasource.password", "DB_PASSWORD");
        requiredProperties.put("spring.data.redis.password", "REDIS_PASSWORD");
        requiredProperties.put("app.cors.allowed-origins", "CORS_ALLOWED_ORIGINS");

        for (Map.Entry<String, String> entry : requiredProperties.entrySet()) {
            String value = environment.getProperty(entry.getKey());
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("生产环境缺少必需配置: " + entry.getKey()
                        + "（请设置环境变量 " + entry.getValue() + "）");
            }
        }
    }
}
