package com.shadow.backend.common.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shadow.backend.common.response.ResultCode;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void exceptionLogsDoNotExposeMessagesOrCauses() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        String sensitive = "password=private-password phone=13800138000 code=654321";
        try {
            var business = handler.handleBusinessException(new BusinessException(10019, sensitive));
            assertThat(business.getStatusCode().value()).isEqualTo(200);
            assertThat(business.getBody().getCode()).isEqualTo(10019);
            assertThat(business.getBody().getMsg()).isEqualTo(sensitive);

            var conflict = handler.handleDataIntegrityViolation(new DuplicateKeyException(sensitive));
            assertThat(conflict.getStatusCode().value()).isEqualTo(200);
            assertThat(conflict.getBody().getCode()).isEqualTo(409);
            assertThat(conflict.getBody().getMsg()).isEqualTo(ResultCode.CONFLICT.getMsg());

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/users");
            var failure = handler.handleException(
                    new IllegalStateException(sensitive, new RuntimeException(sensitive)), request);
            assertThat(failure.getStatusCode().value()).isEqualTo(500);
            assertThat(failure.getBody().getMsg()).isEqualTo(ResultCode.INTERNAL_ERROR.getMsg());

            assertThat(events.list).hasSize(3).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("private-password", "13800138000", "654321");
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getArgumentArray()).noneMatch(Throwable.class::isInstance);
            });
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }
}
