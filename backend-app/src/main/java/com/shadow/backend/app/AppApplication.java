package com.shadow.backend.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.shadow.backend.common",
        "com.shadow.backend.account",
        "com.shadow.backend.user",
        "com.shadow.backend.auth",
        "com.shadow.backend.app"
})
@MapperScan({"com.shadow.backend.user.mapper", "com.shadow.backend.auth.mapper"})
@ConfigurationPropertiesScan(basePackages = {
        "com.shadow.backend.common.config",
        "com.shadow.backend.app.config"
})
public class AppApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppApplication.class, args);
    }
}
