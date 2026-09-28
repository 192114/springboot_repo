package com.shadow.backend.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.shadow.backend.common",
        "com.shadow.backend.account",
        "com.shadow.backend.user",
        "com.shadow.backend.admin"
})
@MapperScan({"com.shadow.backend.user.mapper", "com.shadow.backend.admin.**.mapper"})
@ConfigurationPropertiesScan(basePackages = {
        "com.shadow.backend.common.config",
        "com.shadow.backend.admin.config"
})
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
