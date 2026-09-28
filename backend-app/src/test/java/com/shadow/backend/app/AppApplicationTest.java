package com.shadow.backend.app;

import com.shadow.backend.auth.mapper.SmsLogMapper;
import com.shadow.backend.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppApplicationTest {

    @Test
    void applicationAnnotations_limitScanningToSharedAndAppPackages() {
        SpringBootApplication application = AppApplication.class.getAnnotation(SpringBootApplication.class);
        assertThat(application).isNotNull();
        assertThat(application.scanBasePackages()).containsExactlyInAnyOrder(
                "com.shadow.backend.common",
                "com.shadow.backend.account",
                "com.shadow.backend.user",
                "com.shadow.backend.auth",
                "com.shadow.backend.app"
        );
        assertThat(application.scanBasePackageClasses()).isEmpty();

        MapperScan mappers = AppApplication.class.getAnnotation(MapperScan.class);
        assertThat(mappers).isNotNull();
        assertThat(mappers.value()).containsExactlyInAnyOrder(
                UserMapper.class.getPackageName(), SmsLogMapper.class.getPackageName()
        );
        assertThat(mappers.basePackages()).isEmpty();
        assertThat(mappers.basePackageClasses()).isEmpty();

        ConfigurationPropertiesScan properties = AppApplication.class.getAnnotation(ConfigurationPropertiesScan.class);
        assertThat(properties).isNotNull();
        assertThat(properties.basePackages()).containsExactlyInAnyOrder(
                "com.shadow.backend.common.config", "com.shadow.backend.app.config"
        );
        assertThat(properties.value()).isEmpty();
        assertThat(properties.basePackageClasses()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "com.shadow.backend.admin.auth.controller.AdminAuthController",
            "com.shadow.backend.admin.adminuser.controller.AdminUserManageController",
            "com.shadow.backend.admin.user.controller.AdminUserController",
            "com.shadow.backend.admin.menu.controller.MenuController",
            "com.shadow.backend.admin.role.controller.RoleController",
            "com.shadow.backend.admin.auth.config.AdminUserInitializer",
            "com.shadow.backend.admin.rbac.RbacDataInitializer",
            "com.shadow.backend.admin.config.AdminProdStartupValidator",
            "com.shadow.backend.admin.config.AdminSecurityProperties",
            "com.shadow.backend.admin.config.AdminSaTokenConfigure",
            "com.shadow.backend.admin.AdminApplication"
    })
    void runtimeClasspath_excludesAdminControllersInitializersAndConfiguration(String className) {
        // 禁止初始化类，避免触发启动逻辑或修改 Sa-Token 全局状态。
        assertThatThrownBy(() -> Class.forName(className, false, AppApplication.class.getClassLoader()))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
