package com.shadow.backend.common.aspect;

import com.shadow.backend.common.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLogAspectTest {

    @Test
    void resolveClientIp_ignoresSpoofedHeadersByDefault() {
        MockHttpServletRequest request = request();
        RequestLogAspect aspect = new RequestLogAspect(new SecurityProperties());
        String ip = ReflectionTestUtils.invokeMethod(aspect, "resolveClientIp", request);
        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    void resolveClientIp_usesForwardedHeadersOnlyBehindTrustedProxy() {
        SecurityProperties properties = new SecurityProperties();
        properties.setTrustedProxy(true);
        RequestLogAspect aspect = new RequestLogAspect(properties);
        MockHttpServletRequest request = request();
        String forwarded = ReflectionTestUtils.invokeMethod(aspect, "resolveClientIp", request);
        assertThat(forwarded).isEqualTo("192.0.2.1");
        request.removeHeader("X-Forwarded-For");
        String realIp = ReflectionTestUtils.invokeMethod(aspect, "resolveClientIp", request);
        assertThat(realIp).isEqualTo("192.0.2.3");
        request.removeHeader("X-Real-IP");
        String remote = ReflectionTestUtils.invokeMethod(aspect, "resolveClientIp", request);
        assertThat(remote).isEqualTo("127.0.0.1");
    }

    @ParameterizedTest
    @ValueSource(strings = {",", ",,,", " ", "unknown, 192.0.2.2"})
    void resolveClientIp_invalidForwardedFirstItemFallsBack(String header) {
        SecurityProperties properties = new SecurityProperties();
        properties.setTrustedProxy(true);
        MockHttpServletRequest request = request();
        request.removeHeader("X-Forwarded-For");
        request.addHeader("X-Forwarded-For", header);
        String ip = ReflectionTestUtils.invokeMethod(new RequestLogAspect(properties), "resolveClientIp", request);
        assertThat(ip).isEqualTo("192.0.2.3");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "192.0.2.1, 192.0.2.2");
        request.addHeader("X-Real-IP", "192.0.2.3");
        return request;
    }
}
