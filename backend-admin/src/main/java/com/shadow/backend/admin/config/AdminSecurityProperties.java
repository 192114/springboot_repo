package com.shadow.backend.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.security")
public class AdminSecurityProperties {

    private String initialAdminPassword = "admin123";
}
