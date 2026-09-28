package com.shadow.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /**
     * 是否信任反向代理转发头（X-Forwarded-For / X-Real-IP）获取客户端真实 IP，仅在部署于可信代理之后开启。
     */
    private boolean trustedProxy = false;
}

