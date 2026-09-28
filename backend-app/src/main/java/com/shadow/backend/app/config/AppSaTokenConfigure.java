package com.shadow.backend.app.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import com.shadow.backend.account.session.util.StpAppUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppSaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpAppUtil.checkLogin()))
                .addPathPatterns("/api/app/**")
                .excludePathPatterns(
                        "/api/app/auth/login/password",
                        "/api/app/auth/login/sms",
                        "/api/app/auth/register",
                        "/api/app/auth/refresh",
                        "/api/app/auth/send-code",
                        "/api/app/auth/reset-password",
                        "/api/app/auth/audit-status",
                        "/api/app/auth/resubmit"
                );
    }
}
