package com.shadow.backend.auth.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shadow.backend.auth.constant.SmsScene;
import com.shadow.backend.auth.entity.SmsLog;
import com.shadow.backend.auth.mapper.SmsLogMapper;
import com.shadow.backend.account.response.AuthResultCode;
import com.shadow.backend.auth.service.SmsSender;
import com.shadow.backend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsServiceImplTest {

    private static final String PHONE = "13800138000";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SmsLogMapper smsLogMapper;
    @Mock
    private SmsSender smsSender;
    @Captor
    private ArgumentCaptor<RedisScript<Long>> scriptCaptor;

    private SmsServiceImpl smsService;

    @BeforeEach
    void setUp() {
        smsService = new SmsServiceImpl(redisTemplate, smsLogMapper, smsSender, 10);
    }

    // ---------- sendCode: 冷却与每日上限 ----------

    @Test
    void sendCode_whenRateLimited_throwsTooFrequent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("sms:limit:" + PHONE, "1", Duration.ofSeconds(60)))
                .thenReturn(false);

        assertThatThrownBy(() -> smsService.sendCode(PHONE, SmsScene.REGISTER))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.SMS_CODE_SEND_TOO_FREQUENT.getCode());

        verify(redisTemplate).opsForValue();
        verifyNoMoreInteractions(redisTemplate);
        verifyNoInteractions(smsSender, smsLogMapper);
    }

    @Test
    void sendCode_whenAllowed_countsAtomicallyAndExpiresAtNextDayStart() {
        stubSendAllowed(1L);
        ZonedDateTime before = ZonedDateTime.now();

        smsService.sendCode(PHONE, SmsScene.REGISTER);

        ZonedDateTime after = ZonedDateTime.now();
        ArgumentCaptor<String> expiryCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).execute(scriptCaptor.capture(),
                eq(List.of("sms:daily:" + PHONE)), expiryCaptor.capture());
        assertThat(Long.parseLong(expiryCaptor.getValue())).isIn(
                before.toLocalDate().plusDays(1).atStartOfDay(before.getZone()).toInstant().toEpochMilli(),
                after.toLocalDate().plusDays(1).atStartOfDay(after.getZone()).toInstant().toEpochMilli());
        assertThat(scriptCaptor.getValue().getScriptAsString()).contains(
                "local count = redis.call('INCR', KEYS[1])",
                "if count == 1 then\n    redis.call('PEXPIREAT', KEYS[1], ARGV[1])\nend",
                "return count");
        verify(valueOperations, never()).increment(anyString());
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        verify(smsSender).send(eq(PHONE), anyString());
    }

    @Test
    void sendCode_atomicallyStoresNewCodeAndClearsPreviousAttemptsBeforeSending() {
        stubSendAllowed(2L);

        smsService.sendCode(PHONE, SmsScene.REGISTER);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(redisTemplate, smsSender);
        order.verify(redisTemplate).execute(scriptCaptor.capture(),
                eq(List.of("sms:code:REGISTER:" + PHONE, "sms:attempt:REGISTER:" + PHONE)),
                codeCaptor.capture(), eq("300"));
        order.verify(smsSender).send(PHONE, codeCaptor.getValue());
        assertThat(codeCaptor.getValue()).matches("[0-9]{6}");
        assertThat(scriptCaptor.getValue().getScriptAsString()).contains(
                "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])",
                "redis.call('DEL', KEYS[2])");
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void sendCode_whenDailyLimitReached_allowsLastSend() {
        stubSendAllowed(10L);

        smsService.sendCode(PHONE, SmsScene.REGISTER);

        verify(smsSender).send(eq(PHONE), anyString());
        verify(smsLogMapper).insert(any(SmsLog.class));
    }

    @Test
    void sendCode_whenDailyLimitExceeded_throws() {
        stubSendAllowed(11L);

        assertThatThrownBy(() -> smsService.sendCode(PHONE, SmsScene.REGISTER))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.SMS_CODE_DAILY_LIMIT.getCode());

        verifyNoInteractions(smsSender, smsLogMapper);
        verify(redisTemplate).opsForValue();
        verify(redisTemplate).execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("sms:daily:" + PHONE)), anyString());
        verifyNoMoreInteractions(redisTemplate);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void sendCode_writesMaskInsteadOfPlaintextCodeToLog() {
        stubSendAllowed(2L);

        smsService.sendCode(PHONE, SmsScene.REGISTER);

        ArgumentCaptor<SmsLog> logCaptor = ArgumentCaptor.forClass(SmsLog.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsLogMapper).insert(logCaptor.capture());
        verify(smsSender).send(eq(PHONE), codeCaptor.capture());
        assertThat(logCaptor.getValue().getCode()).isEqualTo("******").isNotEqualTo(codeCaptor.getValue());
        assertThat(logCaptor.getValue().getPhone()).isEqualTo(PHONE);
        assertThat(logCaptor.getValue().getScene()).isEqualTo(SmsScene.REGISTER.name());
        assertThat(logCaptor.getValue().getStatus()).isZero();
        assertThat(logCaptor.getValue().getSendTime()).isNotNull();
    }

    @Test
    void logSmsSender_doesNotLogPhoneOrCode() {
        Logger logger = (Logger) LoggerFactory.getLogger(LogSmsSender.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            new LogSmsSender().send(PHONE, "123456");

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain(PHONE, "123456");
            assertThat(appender.list.getFirst().getArgumentArray()).isNullOrEmpty();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    // ---------- verifyCode: 原子核销 ----------

    @Test
    void verifyCode_whenCodeNotFound_throws() {
        stubVerifyScriptResult("123456", -1L);

        assertThatThrownBy(() -> smsService.verifyCode(PHONE, SmsScene.LOGIN, "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.SMS_CODE_NOT_FOUND.getCode());
    }

    @Test
    void verifyCode_whenCodeMismatch_throws() {
        stubVerifyScriptResult("222222", 0L);

        assertThatThrownBy(() -> smsService.verifyCode(PHONE, SmsScene.LOGIN, "222222"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.SMS_CODE_INVALID.getCode());
    }

    @Test
    void verifyCode_whenAttemptLimitReached_throws() {
        stubVerifyScriptResult("222222", -2L);

        assertThatThrownBy(() -> smsService.verifyCode(PHONE, SmsScene.LOGIN, "222222"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.SMS_CODE_EXCEED_ATTEMPTS.getCode());
    }

    @Test
    void verifyCode_whenValid_marksLatestLogVerified() {
        stubVerifyScriptResult("111111", 1L);
        SmsLog latestLog = new SmsLog();
        latestLog.setId(9L);
        latestLog.setStatus(0);
        when(smsLogMapper.selectOne(any())).thenReturn(latestLog);

        smsService.verifyCode(PHONE, SmsScene.LOGIN, "111111");

        verify(smsLogMapper).updateById(latestLog);
        assertThat(latestLog.getStatus()).isEqualTo(1);
        assertThat(latestLog.getVerifiedTime()).isNotNull();
    }

    @Test
    void verifyCode_whenValid_butNoMatchingLog_stillSucceedsWithoutUpdate() {
        stubVerifyScriptResult("111111", 1L);
        when(smsLogMapper.selectOne(any())).thenReturn(null);

        smsService.verifyCode(PHONE, SmsScene.LOGIN, "111111");

        verify(smsLogMapper, never()).updateById(any(SmsLog.class));
    }

    @Test
    void verifyCode_usesAtomicScriptWithRemainingCodeTtlAndAttemptLimit() {
        stubVerifyScriptResult("111111", 1L);

        smsService.verifyCode(PHONE, SmsScene.LOGIN, "111111");

        verify(redisTemplate).execute(scriptCaptor.capture(),
                eq(List.of("sms:code:LOGIN:" + PHONE, "sms:attempt:LOGIN:" + PHONE)),
                eq("111111"), eq("5"));
        assertThat(scriptCaptor.getValue().getScriptAsString()).contains(
                "if stored == ARGV[1] then\n    redis.call('DEL', KEYS[1], KEYS[2])\n    return 1\nend",
                "local attempts = redis.call('INCR', KEYS[2])",
                "redis.call('PEXPIRE', KEYS[2], redis.call('PTTL', KEYS[1]))",
                "if attempts >= tonumber(ARGV[2]) then\n    redis.call('DEL', KEYS[1], KEYS[2])\n    return -2\nend");
        verify(redisTemplate, never()).delete(anyString());
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    // ---------- helpers ----------

    private void stubSendAllowed(Long sentToday) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("sms:limit:" + PHONE, "1", Duration.ofSeconds(60)))
                .thenReturn(true);
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("sms:daily:" + PHONE)), anyString())).thenReturn(sentToday);
    }

    private void stubVerifyScriptResult(String code, Long result) {
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("sms:code:LOGIN:" + PHONE, "sms:attempt:LOGIN:" + PHONE)),
                eq(code), eq("5"))).thenReturn(result);
    }
}
