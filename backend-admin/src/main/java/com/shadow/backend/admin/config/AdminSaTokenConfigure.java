package com.shadow.backend.admin.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import com.shadow.backend.admin.auth.util.StpAdminUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminSaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    StpAdminUtil.checkLogin();
                    StpAdminUtil.autoRenew();
                    checkAdminRoutePermission();
                }))
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login");
    }

    private void checkAdminRoutePermission() {
        SaRouter.match("/api/admin/auth/**").stop();
        SaRouter.match("/api/admin/menus/tree").stop();

        SaRouter.match("/api/admin/menus/all").match(SaHttpMethod.GET)
                .check(r -> StpAdminUtil.checkPermission("menu:list")).stop();
        SaRouter.match("/api/admin/menus").match(SaHttpMethod.POST)
                .check(r -> StpAdminUtil.checkPermission("menu:create")).stop();
        SaRouter.match("/api/admin/menus/*").match(SaHttpMethod.PUT)
                .check(r -> StpAdminUtil.checkPermission("menu:update")).stop();
        SaRouter.match("/api/admin/menus/*").match(SaHttpMethod.DELETE)
                .check(r -> StpAdminUtil.checkPermission("menu:delete")).stop();

        SaRouter.match("/api/admin/roles").match(SaHttpMethod.GET)
                .check(r -> StpAdminUtil.checkPermission("role:list")).stop();
        SaRouter.match("/api/admin/roles/all").match(SaHttpMethod.GET)
                .check(r -> StpAdminUtil.checkPermission("role:list")).stop();
        SaRouter.match("/api/admin/roles/*").match(SaHttpMethod.GET)
                .check(r -> StpAdminUtil.checkPermission("role:list")).stop();
        SaRouter.match("/api/admin/roles").match(SaHttpMethod.POST)
                .check(r -> StpAdminUtil.checkPermission("role:create")).stop();
        // Sa-Token 的 * 可跨越 /，子资源规则必须先于宽泛 PUT 规则。
        SaRouter.match("/api/admin/roles/*/menus").match(SaHttpMethod.PUT)
                .check(r -> StpAdminUtil.checkPermission("role:assign")).stop();
        SaRouter.match("/api/admin/roles/*").match(SaHttpMethod.PUT)
                .check(r -> StpAdminUtil.checkPermission("role:update")).stop();
        SaRouter.match("/api/admin/roles/*").match(SaHttpMethod.DELETE)
                .check(r -> StpAdminUtil.checkPermission("role:delete")).stop();

        SaRouter.match("/api/admin/admin-users").match(SaHttpMethod.GET)
                .check(r -> StpAdminUtil.checkPermission("admin-user:list")).stop();
        SaRouter.match("/api/admin/admin-users/*").match(SaHttpMethod.GET)
                .check(r -> StpAdminUtil.checkPermission("admin-user:list")).stop();
        SaRouter.match("/api/admin/admin-users").match(SaHttpMethod.POST)
                .check(r -> StpAdminUtil.checkPermission("admin-user:create")).stop();
        SaRouter.match("/api/admin/admin-users/*/roles").match(SaHttpMethod.PUT)
                .check(r -> StpAdminUtil.checkPermission("admin-user:assign")).stop();
        SaRouter.match("/api/admin/admin-users/*").match(SaHttpMethod.PUT)
                .check(r -> StpAdminUtil.checkPermission("admin-user:update")).stop();
        SaRouter.match("/api/admin/admin-users/*").match(SaHttpMethod.DELETE)
                .check(r -> StpAdminUtil.checkPermission("admin-user:delete")).stop();

        SaRouter.match("/api/admin/users").match(SaHttpMethod.GET)
                .check(r -> StpAdminUtil.checkPermission("user:list")).stop();
        SaRouter.match("/api/admin/users/*").match(SaHttpMethod.GET)
                .check(r -> StpAdminUtil.checkPermission("user:list")).stop();
        SaRouter.match("/api/admin/users").match(SaHttpMethod.POST)
                .check(r -> StpAdminUtil.checkPermission("user:list")).stop();
        SaRouter.match("/api/admin/users/*/audit").match(SaHttpMethod.POST)
                .check(r -> StpAdminUtil.checkPermission("user:audit")).stop();
        SaRouter.match("/api/admin/users/*").match(SaHttpMethod.PUT)
                .check(r -> StpAdminUtil.checkPermission("user:list")).stop();
        SaRouter.match("/api/admin/users/*").match(SaHttpMethod.DELETE)
                .check(r -> StpAdminUtil.checkPermission("user:list")).stop();
    }
}
