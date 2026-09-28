package com.shadow.backend.admin;

import com.shadow.backend.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminApplicationTest {

    @Test
    void applicationAnnotations_limitScanningToSharedAndAdminPackages() {
        SpringBootApplication application = AdminApplication.class.getAnnotation(SpringBootApplication.class);
        assertThat(application).isNotNull();
        assertThat(application.scanBasePackages()).containsExactlyInAnyOrder(
                "com.shadow.backend.common",
                "com.shadow.backend.account",
                "com.shadow.backend.user",
                "com.shadow.backend.admin"
        );
        assertThat(application.scanBasePackageClasses()).isEmpty();

        MapperScan mappers = AdminApplication.class.getAnnotation(MapperScan.class);
        assertThat(mappers).isNotNull();
        assertThat(mappers.value()).containsExactlyInAnyOrder(
                UserMapper.class.getPackageName(), "com.shadow.backend.admin.**.mapper"
        );
        assertThat(mappers.basePackages()).isEmpty();
        assertThat(mappers.basePackageClasses()).isEmpty();

        ConfigurationPropertiesScan properties = AdminApplication.class.getAnnotation(ConfigurationPropertiesScan.class);
        assertThat(properties).isNotNull();
        assertThat(properties.basePackages()).containsExactlyInAnyOrder(
                "com.shadow.backend.common.config", "com.shadow.backend.admin.config"
        );
        assertThat(properties.value()).isEmpty();
        assertThat(properties.basePackageClasses()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "com.shadow.backend.auth.controller.AuthController",
            "com.shadow.backend.app.user.controller.UserController",
            "com.shadow.backend.app.config.AppSaTokenConfigure",
            "com.shadow.backend.app.AppApplication"
    })
    void runtimeClasspath_excludesAppControllersAndConfiguration(String className) {
        // 仅验证当前模块类路径，不启动 Spring 上下文或初始化类。
        assertThatThrownBy(() -> Class.forName(className, false, AdminApplication.class.getClassLoader()))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
