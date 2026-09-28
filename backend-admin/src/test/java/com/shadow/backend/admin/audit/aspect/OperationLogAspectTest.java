package com.shadow.backend.admin.audit.aspect;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationLogAspectTest {

    @Mock
    private OperationLogMapper operationLogMapper;
    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;
    @Mock
    private HttpServletRequest request;
    @Mock
    private ServletRequestAttributes attributes;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecurityProperties securityProperties = new SecurityProperties();
    private final AdminUser admin = new AdminUser();
    private OperationLogAspect aspect;
    private MockedStatic<LoginAdminUtil> loginAdmin;
    private MockedStatic<RequestContextHolder> requestContext;
    private Logger logger;
    private ListAppender<ILoggingEvent> warnings;

    @BeforeEach
    void setUp() {
        aspect = new OperationLogAspect(operationLogMapper, adminUserMapper, objectMapper, securityProperties);
        loginAdmin = mockStatic(LoginAdminUtil.class);
        requestContext = mockStatic(RequestContextHolder.class);
        admin.setId(7L);
        admin.setUsername("operator");
        logger = (Logger) LoggerFactory.getLogger(OperationLogAspect.class);
        warnings = new ListAppender<>();
        warnings.start();
        logger.addAppender(warnings);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(warnings);
        warnings.stop();
        requestContext.close();
        loginAdmin.close();
        MDC.remove(AppConstant.TRACE_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void successfulWritesKeepResultAndCaptureMetadata(String method) throws Throwable {
        auditRequest(method);
        arguments(new String[]{"id"}, 42L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        MDC.put(AppConstant.TRACE_ID, "trace-before");
        Result<String> result = Result.success("response-must-not-be-logged");
        when(joinPoint.proceed()).thenReturn(result);

        assertSame(result, aspect.around(joinPoint));

        OperationLog entry = inserted();
        assertThat(entry.getAdminId()).isEqualTo(7L);
        assertThat(entry.getUsername()).isEqualTo("operator");
        assertThat(entry.getMethod()).isEqualTo(method);
        assertThat(entry.getUri()).isEqualTo("/api/admin/users/42");
        assertThat(entry.getParams()).isEqualTo("{\"id\":42}");
        assertThat(entry.getResultCode()).isEqualTo(200);
        assertThat(entry.getIp()).isEqualTo("127.0.0.1");
        assertThat(entry.getTraceId()).isEqualTo("trace-before");
        assertThat(entry.getCostMs()).isNotNegative();
        assertThat(entry.getId()).isNull();
        verify(joinPoint).proceed();
        verify(request, never()).getHeader(anyString());
    }

    @Test
    void normallyReturnedFailureUsesItsBusinessCode() throws Throwable {
        auditRequest("POST");
        Result<Void> result = Result.fail(20017, "业务失败");
        when(joinPoint.proceed()).thenReturn(result);

        assertSame(result, aspect.around(joinPoint));
        assertThat(inserted().getResultCode()).isEqualTo(20017);
        verify(joinPoint).proceed();
    }

    @ParameterizedTest
    @MethodSource("businessFailures")
    void failuresKeepOriginalThrowableAndRecordCode(Throwable failure, int code) throws Throwable {
        auditRequest("POST");
        when(joinPoint.proceed()).thenThrow(failure);

        assertSame(failure, assertThrows(Throwable.class, () -> aspect.around(joinPoint)));
        assertThat(inserted().getResultCode()).isEqualTo(code);
        verify(joinPoint).proceed();
    }

    static Stream<Arguments> businessFailures() {
        return Stream.of(
                Arguments.of(new BusinessException(20018, "业务错误"), 20018),
                Arguments.of(new DataIntegrityViolationException("duplicate"), 409),
                Arguments.of(notLoggedIn(), 401),
                Arguments.of(new NotPermissionException("write"), 403),
                Arguments.of(new NotRoleException("admin"), 403),
                Arguments.of(new ConstraintViolationException(Set.of()), 400),
                Arguments.of(new IllegalStateException("unknown"), 500),
                Arguments.of(new AssertionError("unknown error"), 500));
    }

    @Test
    void parametersMaskNestedObjectsArraysAndNamedScalarsWithoutMutatingInput() throws Throwable {
        auditRequest("PUT");
        Map<String, Object> contact = Map.of(
                "Phone", "13800138000", "MOBILE", List.of("13900139000", "123"),
                "CoDe", "raw-code", "accessToken", "raw-token",
                "SECRET", "raw-secret", "Authorization", "raw-authorization");
        SensitiveRequest payload = new SensitiveRequest("alice", "raw-old", "raw-new", List.of(contact));
        JsonNode tree = objectMapper.valueToTree(Map.of("PWD", "raw-tree-password"));
        arguments(new String[]{"payload", "pWd", "mobile", "items", "tree"},
                payload, "raw-argument-password", 13700137000L, new Object[]{contact}, tree);
        when(joinPoint.proceed()).thenReturn(Result.success());

        aspect.around(joinPoint);

        String params = inserted().getParams();
        JsonNode json = objectMapper.readTree(params);
        assertThat(json.at("/payload/username").asString()).isEqualTo("alice");
        assertThat(json.at("/payload/oldPassword").asString()).isEqualTo("******");
        assertThat(json.at("/payload/newPassword").asString()).isEqualTo("******");
        assertThat(json.at("/pWd").asString()).isEqualTo("******");
        assertThat(json.at("/mobile").asString()).isEqualTo(PhoneMaskUtil.mask("13700137000"));
        for (String prefix : List.of("/payload/contacts/0/", "/items/0/")) {
            assertThat(json.at(prefix + "Phone").asString()).isEqualTo(PhoneMaskUtil.mask("13800138000"));
            assertThat(json.at(prefix + "MOBILE/0").asString()).isEqualTo(PhoneMaskUtil.mask("13900139000"));
            assertThat(json.at(prefix + "MOBILE/1").asString()).isEqualTo(PhoneMaskUtil.mask("123"));
            for (String field : List.of("CoDe", "accessToken", "SECRET", "Authorization")) {
                assertThat(json.at(prefix + field).asString()).isEqualTo("******");
            }
        }
        assertThat(json.at("/tree/PWD").asString()).isEqualTo("******");
        assertThat(tree.get("PWD").asString()).isEqualTo("raw-tree-password");
        assertThat(payload.oldPassword()).isEqualTo("raw-old");
        assertThat(params).doesNotContain("raw-", "13800138000", "13900139000", "13700137000");
        verify(joinPoint).proceed();
    }

    @Test
    void parametersAreRedactedBeforeTruncationAndColumnsStayWithinLimits() throws Throwable {
        auditRequest("POST");
        admin.setUsername("u".repeat(40));
        when(request.getRequestURI()).thenReturn("/" + "p".repeat(300));
        when(request.getRemoteAddr()).thenReturn("i".repeat(80));
        MDC.put(AppConstant.TRACE_ID, "t".repeat(40));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("newPassword", "raw-password");
        payload.put("message", "x".repeat(2000));
        arguments(new String[]{"payload"}, payload);
        when(joinPoint.proceed()).thenReturn(Result.success());

        aspect.around(joinPoint);

        OperationLog entry = inserted();
        assertThat(entry.getParams()).hasSize(1024).startsWith("{\"payload\":{\"newPassword\":\"******\"")
                .doesNotContain("raw-password");
        assertThat(entry.getUsername()).hasSize(32);
        assertThat(entry.getUri()).hasSize(255);
        assertThat(entry.getIp()).hasSize(64);
        assertThat(entry.getTraceId()).hasSize(32);
    }

    @Test
    void nonBusinessArgumentsAndUnserializableValuesAreDroppedWithoutToString() throws Throwable {
        auditRequest("POST");
        BrokenArgument broken = new BrokenArgument();
        MultipartFile file = mock(MultipartFile.class);
        arguments(new String[]{"request", "reply", "binding", "upload", "uploads", "files", "httpHeaders",
                        "headers", "response", "broken", "id"},
                request, mock(HttpServletResponse.class), mock(BindingResult.class), file,
                new MultipartFile[]{file}, List.of(file), new HttpHeaders(), Map.of("X-Key", "raw-header"),
                "raw-response", broken, 42L);
        when(joinPoint.proceed()).thenReturn(Result.success());

        aspect.around(joinPoint);

        assertThat(inserted().getParams()).isEqualTo("{\"id\":42}");
        assertThat(broken.stringified).isFalse();
        assertSafeWarning();
        verify(joinPoint).proceed();
    }

    @Test
    void headerAndCookieBoundScalarsAreNotCaptured() throws Throwable {
        auditRequest("POST");
        arguments(new String[]{"client", "session", "id"}, "raw-header", "raw-cookie", 42L);
        when(signature.getMethod()).thenReturn(OperationLogAspectTest.class.getDeclaredMethod(
                "headerArguments", String.class, String.class, Long.class));
        when(joinPoint.proceed()).thenReturn(Result.success());

        aspect.around(joinPoint);

        assertThat(inserted().getParams()).isEqualTo("{\"id\":42}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PATCH", "OPTIONS"})
    void readAndUnsupportedMethodsSkipAudit(String method) throws Throwable {
        request(method, null);
        Object result = new Object();
        when(joinPoint.proceed()).thenReturn(result);

        assertSame(result, aspect.around(joinPoint));
        loginAdmin.verifyNoInteractions();
        verifyNoInteractions(adminUserMapper, operationLogMapper);
        verify(joinPoint).proceed();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anonymousRequestsNeverInventAnAdminId(boolean nullId) throws Throwable {
        request("POST", "/api/admin/users/42");
        if (nullId) {
            loginAdmin.when(LoginAdminUtil::currentAdminId).thenReturn(null);
        } else {
            loginAdmin.when(LoginAdminUtil::currentAdminId).thenThrow(notLoggedIn());
        }
        Result<Void> result = Result.success();
        when(joinPoint.proceed()).thenReturn(result);

        assertSame(result, aspect.around(joinPoint));
        verifyNoInteractions(adminUserMapper, operationLogMapper);
        verify(joinPoint).proceed();
        assertThat(warnings.list).isEmpty();
    }

    @Test
    void loginEndpointIsSkippedEvenIfItEstablishesAnAdminSession() throws Throwable {
        request("POST", "/api/admin/auth/login");
        Result<Void> result = Result.success();
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            loginAdmin.when(LoginAdminUtil::currentAdminId).thenReturn(7L);
            return result;
        });

        assertSame(result, aspect.around(joinPoint));
        loginAdmin.verifyNoInteractions();
        verifyNoInteractions(adminUserMapper, operationLogMapper);
        verify(joinPoint).proceed();
    }

    @Test
    void logoutCapturesIdentityAndTraceBeforeProceed() throws Throwable {
        auditRequest("POST");
        when(request.getRequestURI()).thenReturn("/api/admin/auth/logout");
        MDC.put(AppConstant.TRACE_ID, "before-logout");
        Result<Void> result = Result.success();
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            loginAdmin.verify(LoginAdminUtil::currentAdminId);
            verify(adminUserMapper).selectById(7L);
            loginAdmin.when(LoginAdminUtil::currentAdminId).thenThrow(notLoggedIn());
            admin.setUsername("changed-after-proceed");
            MDC.remove(AppConstant.TRACE_ID);
            return result;
        });

        assertSame(result, aspect.around(joinPoint));

        OperationLog entry = inserted();
        assertThat(entry.getAdminId()).isEqualTo(7L);
        assertThat(entry.getUsername()).isEqualTo("operator");
        assertThat(entry.getTraceId()).isEqualTo("before-logout");
        var order = inOrder(adminUserMapper, joinPoint, operationLogMapper);
        order.verify(adminUserMapper).selectById(7L);
        order.verify(joinPoint).proceed();
        order.verify(operationLogMapper).insert(any(OperationLog.class));
        loginAdmin.verify(LoginAdminUtil::currentAdminId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void storageFailureCannotReplaceResultOrOriginalException(boolean businessFails) throws Throwable {
        auditRequest("POST");
        when(operationLogMapper.insert(any(OperationLog.class)))
                .thenThrow(new IllegalStateException("raw-storage-secret"));

        assertBusinessOutcome(businessFails);

        verify(operationLogMapper).insert(any(OperationLog.class));
        assertSafeWarning();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preparationFailureCannotReplaceResultOrOriginalException(boolean businessFails) throws Throwable {
        requestContext.when(RequestContextHolder::getRequestAttributes)
                .thenThrow(new IllegalStateException("raw-request-secret"));

        assertBusinessOutcome(businessFails);

        verifyNoInteractions(adminUserMapper, operationLogMapper);
        assertSafeWarning();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void parameterPreparationFailureCannotReplaceBusinessOutcome(boolean businessFails) throws Throwable {
        request("POST", "/api/admin/users/42");
        loginAdmin.when(LoginAdminUtil::currentAdminId).thenReturn(7L);
        when(adminUserMapper.selectById(7L)).thenReturn(admin);
        when(joinPoint.getSignature()).thenThrow(new IllegalStateException("raw-parameter-secret"));

        assertBusinessOutcome(businessFails);

        verifyNoInteractions(operationLogMapper);
        assertSafeWarning();
    }

    @Test
    void identityLookupFailureSkipsAuditWithoutBlockingBusiness() throws Throwable {
        request("POST", "/api/admin/users/42");
        loginAdmin.when(LoginAdminUtil::currentAdminId).thenReturn(7L);
        when(adminUserMapper.selectById(7L)).thenThrow(new IllegalStateException("raw-identity-secret"));

        assertBusinessOutcome(false);

        verifyNoInteractions(operationLogMapper);
        assertSafeWarning();
    }

    @Test
    void missingRequestContextJustProceeds() throws Throwable {
        assertBusinessOutcome(false);
        loginAdmin.verifyNoInteractions();
        verifyNoInteractions(adminUserMapper, operationLogMapper);
    }

    @ParameterizedTest
    @MethodSource("proxyCases")
    void proxyHeadersAreOnlyUsedWhenTrusted(boolean trusted, String forwarded, String realIp, String expected)
            throws Throwable {
        auditRequest("POST");
        securityProperties.setTrustedProxy(trusted);
        if (trusted) {
            when(request.getHeader(anyString())).thenAnswer(invocation ->
                    "X-Forwarded-For".equals(invocation.getArgument(0)) ? forwarded : realIp);
        }
        if ("127.0.0.1".equals(expected)) {
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        }
        when(joinPoint.proceed()).thenReturn(Result.success());

        aspect.around(joinPoint);

        assertThat(inserted().getIp()).isEqualTo(expected);
        if (!trusted) {
            verify(request, never()).getHeader(anyString());
        }
    }

    static Stream<Arguments> proxyCases() {
        return Stream.of(
                Arguments.of(false, "198.51.100.9", "198.51.100.10", "127.0.0.1"),
                Arguments.of(true, " 198.51.100.9 , 10.0.0.1", "198.51.100.10", "198.51.100.9"),
                Arguments.of(true, null, "198.51.100.10", "198.51.100.10"),
                Arguments.of(true, "UNKNOWN", "198.51.100.10", "198.51.100.10"),
                Arguments.of(true, " ", "198.51.100.10", "198.51.100.10"),
                Arguments.of(true, ",,,", "198.51.100.10", "198.51.100.10"),
                Arguments.of(true, "unknown, 10.0.0.1", "198.51.100.10", "198.51.100.10"),
                Arguments.of(true, null, null, "127.0.0.1"),
                Arguments.of(true, "unknown", "UNKNOWN", "127.0.0.1"));
    }

    private void request(String method, String uri) {
        requestContext.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);
        when(attributes.getRequest()).thenReturn(request);
        when(request.getMethod()).thenReturn(method);
        if (uri != null) {
            when(request.getRequestURI()).thenReturn(uri);
        }
    }

    private void auditRequest(String method) throws NoSuchMethodException {
        request(method, "/api/admin/users/42");
        loginAdmin.when(LoginAdminUtil::currentAdminId).thenReturn(7L);
        when(adminUserMapper.selectById(7L)).thenReturn(admin);
        arguments(new String[0]);
    }

    private void arguments(String[] names, Object... args) throws NoSuchMethodException {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(names);
        when(signature.getMethod()).thenReturn(OperationLogAspectTest.class.getDeclaredMethod("businessArgument", Object.class));
        when(joinPoint.getArgs()).thenReturn(args);
    }

    private OperationLog inserted() {
        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        return captor.getValue();
    }

    private void assertBusinessOutcome(boolean fails) throws Throwable {
        if (fails) {
            BusinessException failure = new BusinessException(20019, "original business failure");
            when(joinPoint.proceed()).thenThrow(failure);
            assertSame(failure, assertThrows(Throwable.class, () -> aspect.around(joinPoint)));
        } else {
            Object result = new Object();
            when(joinPoint.proceed()).thenReturn(result);
            assertSame(result, aspect.around(joinPoint));
        }
        verify(joinPoint).proceed();
    }

    private void assertSafeWarning() {
        assertThat(warnings.list).hasSize(1);
        ILoggingEvent event = warnings.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
        assertThat(event.getFormattedMessage()).startsWith("管理操作审计失败: ").doesNotContain("raw-");
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getArgumentArray()).hasSize(1);
        assertThat(event.getArgumentArray()[0]).isInstanceOf(String.class);
    }

    private static NotLoginException notLoggedIn() {
        return new NotLoginException("admin", NotLoginException.NOT_TOKEN, "not logged in");
    }

    private static void businessArgument(Object payload) {
    }

    private static void headerArguments(@RequestHeader("X-Client") String client,
                                        @CookieValue("session") String session, Long id) {
    }

    public record SensitiveRequest(String username, String oldPassword, String newPassword,
                                   List<Map<String, Object>> contacts) {
    }

    public static class BrokenArgument {
        private boolean stringified;

        public String getValue() {
            throw new IllegalStateException("raw-serialization-secret");
        }

        @Override
        public String toString() {
            stringified = true;
            return "raw-toString-secret";
        }
    }
}
