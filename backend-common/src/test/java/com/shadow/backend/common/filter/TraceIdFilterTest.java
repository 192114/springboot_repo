package com.shadow.backend.common.filter;

import com.shadow.backend.common.constant.AppConstant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "trace\r\ninjected", "abcdefghijklmnopqrstuvwxyz1234567", "<script>"})
    void invalidTraceId_isReplacedAndMdcIsCleared(String input) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (input != null) {
            request.addHeader(AppConstant.TRACE_ID_HEADER, input);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(AppConstant.TRACE_ID)).matches("[a-f0-9]{12}"));
        assertThat(response.getHeader(AppConstant.TRACE_ID_HEADER)).matches("[a-f0-9]{12}");
        assertThat(MDC.get(AppConstant.TRACE_ID)).isNull();
    }

    @Test
    void validTraceId_isPreservedAndClearedOnFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AppConstant.TRACE_ID_HEADER, "request_123-ABC");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IllegalStateException failure = new IllegalStateException("failed");
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get(AppConstant.TRACE_ID)).isEqualTo("request_123-ABC");
            throw failure;
        })).isSameAs(failure);
        assertThat(response.getHeader(AppConstant.TRACE_ID_HEADER)).isEqualTo("request_123-ABC");
        assertThat(MDC.get(AppConstant.TRACE_ID)).isNull();
    }
}
