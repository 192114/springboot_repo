package com.shadow.backend.common.aspect;

import com.shadow.backend.common.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RequestLogAspect {

    private final SecurityProperties securityProperties;

    @Pointcut("execution(* com.shadow.backend..controller..*(..))")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        HttpServletRequest request = currentRequest();
        String method = request != null ? request.getMethod() : "UNKNOWN";
        String uri = request != null ? request.getRequestURI() : "UNKNOWN";
        String ip = request != null ? resolveClientIp(request) : "UNKNOWN";

        try {
            Object result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - start;
            log.info("请求完成 | method={} | uri={} | ip={} | cost={}ms", method, uri, ip, cost);
            return result;
        } catch (Throwable ex) {
            long cost = System.currentTimeMillis() - start;
            log.warn("请求异常 | method={} | uri={} | ip={} | cost={}ms | error={}",
                    method, uri, ip, cost, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (!securityProperties.isTrustedProxy()) {
            return request.getRemoteAddr();
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null) {
            String first = ip.split(",", 2)[0].trim();
            if (!first.isEmpty() && !"unknown".equalsIgnoreCase(first)) {
                return first;
            }
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
