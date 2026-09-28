package com.shadow.backend.admin.audit.aspect;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.shadow.backend.admin.audit.entity.OperationLog;
import com.shadow.backend.admin.audit.mapper.OperationLogMapper;
import com.shadow.backend.admin.auth.entity.AdminUser;
import com.shadow.backend.admin.auth.mapper.AdminUserMapper;
import com.shadow.backend.common.config.SecurityProperties;
import com.shadow.backend.common.constant.AppConstant;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.common.response.Result;
import com.shadow.backend.admin.auth.util.LoginAdminUtil;
import com.shadow.backend.common.util.PhoneMaskUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.security.Principal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE");
    private static final String MASK = "******";

    private final OperationLogMapper operationLogMapper;
    private final AdminUserMapper adminUserMapper;
    private final ObjectMapper objectMapper;
    private final SecurityProperties securityProperties;

    @Around("execution(* com.shadow.backend.admin..controller..*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        OperationLog operationLog = prepareSafely(joinPoint);
        long start = System.nanoTime();
        Object result = null;
        Throwable failure = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            if (operationLog != null) {
                try {
                    operationLog.setCostMs((System.nanoTime() - start) / 1_000_000);
                    operationLog.setResultCode(failure == null
                            ? (result instanceof Result<?> response ? response.getCode() : 200)
                            : resultCode(failure));
                    operationLogMapper.insert(operationLog);
                } catch (Throwable ex) {
                    warn(ex);
                }
            }
        }
    }

    private OperationLog prepareSafely(ProceedingJoinPoint joinPoint) {
        try {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String method = request.getMethod();
            if (method == null || !WRITE_METHODS.contains(method)) {
                return null;
            }
            String uri = request.getRequestURI();
            if (uri == null || uri.endsWith("/auth/login") || uri.endsWith("/auth/login/")) {
                return null;
            }
            Long adminId = LoginAdminUtil.currentAdminId();
            if (adminId == null) {
                return null;
            }
            AdminUser admin = adminUserMapper.selectById(adminId);
            OperationLog operationLog = new OperationLog();
            operationLog.setAdminId(adminId);
            operationLog.setUsername(truncate(admin == null ? null : admin.getUsername(), 32));
            operationLog.setMethod(method);
            operationLog.setUri(truncate(uri, 255));
            operationLog.setIp(truncate(resolveClientIp(request), 64));
            operationLog.setTraceId(truncate(MDC.get(AppConstant.TRACE_ID), 32));
            operationLog.setParams(parameters(joinPoint));
            return operationLog;
        } catch (NotLoginException ex) {
            return null;
        } catch (Throwable ex) {
            warn(ex);
            return null;
        }
    }

    private String parameters(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        ObjectNode parameters = objectMapper.createObjectNode();
        if (names == null || args == null) {
            return "{}";
        }
        Method method = signature.getMethod();
        Parameter[] declared = method == null ? new Parameter[0] : method.getParameters();
        for (int i = 0; i < Math.min(names.length, args.length); i++) {
            try {
                String name = names[i];
                if (name == null || name.isBlank() || "headers".equalsIgnoreCase(name)
                        || "response".equalsIgnoreCase(name) || isNonBusiness(args[i])
                        || (i < declared.length && (declared[i].isAnnotationPresent(RequestHeader.class)
                        || declared[i].isAnnotationPresent(CookieValue.class)))) {
                    continue;
                }
                JsonNode value = isSensitive(name) ? objectMapper.stringNode(MASK)
                        : redact(name, objectMapper.valueToTree(args[i]));
                parameters.set(name, value);
            } catch (Throwable ex) {
                warn(ex);
            }
        }
        return truncate(objectMapper.writeValueAsString(parameters), 1024);
    }

    private JsonNode redact(String name, JsonNode value) {
        if (isSensitive(name)) {
            return objectMapper.stringNode(MASK);
        }
        if (value.isObject()) {
            ObjectNode sanitized = objectMapper.createObjectNode();
            for (Map.Entry<String, JsonNode> field : value.properties()) {
                sanitized.set(field.getKey(), redact(field.getKey(), field.getValue()));
            }
            return sanitized;
        }
        if (value.isArray()) {
            ArrayNode sanitized = objectMapper.createArrayNode();
            for (JsonNode item : value) {
                sanitized.add(redact(name, item));
            }
            return sanitized;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (!value.isNull() && (normalized.contains("phone") || normalized.contains("mobile"))) {
            String phone = value.isString() ? value.asString() : objectMapper.writeValueAsString(value);
            return objectMapper.stringNode(PhoneMaskUtil.mask(phone));
        }
        return value;
    }

    private boolean isSensitive(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return normalized.contains("password") || normalized.contains("passwd") || normalized.contains("pwd")
                || normalized.contains("code") || normalized.contains("token") || normalized.contains("secret")
                || normalized.contains("authorization") || normalized.contains("credential")
                || normalized.contains("cookie") || normalized.contains("sessionid") || normalized.contains("apikey");
    }

    private boolean isNonBusiness(Object value) {
        if (value instanceof ServletRequest || value instanceof ServletResponse || value instanceof HttpSession
                || value instanceof Part || value instanceof MultipartFile || value instanceof Errors
                || value instanceof WebRequest || value instanceof Model || value instanceof ModelMap
                || value instanceof HttpHeaders || value instanceof HttpEntity<?> || value instanceof Principal
                || value instanceof InputStream || value instanceof OutputStream || value instanceof Reader
                || value instanceof Writer || value instanceof Resource) {
            return true;
        }
        if (value instanceof Object[] array) {
            return Arrays.stream(array).anyMatch(this::isNonBusiness);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (isNonBusiness(item)) {
                    return true;
                }
            }
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(this::isNonBusiness);
        }
        return false;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (securityProperties.isTrustedProxy()) {
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
        }
        return request.getRemoteAddr();
    }

    private int resultCode(Throwable failure) {
        if (failure instanceof BusinessException ex) {
            return ex.getCode();
        }
        if (failure instanceof DataIntegrityViolationException) {
            return 409;
        }
        if (failure instanceof NotLoginException) {
            return 401;
        }
        if (failure instanceof NotPermissionException || failure instanceof NotRoleException) {
            return 403;
        }
        return failure instanceof ConstraintViolationException ? 400 : 500;
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(limit - 1)) ? limit - 1 : limit;
        return value.substring(0, end);
    }

    private void warn(Throwable ex) {
        log.warn("管理操作审计失败: {}", ex.getClass().getName());
    }
}
