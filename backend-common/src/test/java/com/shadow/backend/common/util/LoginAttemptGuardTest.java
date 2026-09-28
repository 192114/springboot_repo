package com.shadow.backend.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptGuardTest {

    private static final String SCENE = "app-password";
    private static final String IDENTIFIER = "13800138000";
    private static final String KEY = "login:fail:" + SCENE + ":" + IDENTIFIER;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Captor
    private ArgumentCaptor<RedisScript<Long>> scriptCaptor;
    @Captor
    private ArgumentCaptor<List<String>> keysCaptor;
    @Captor
    private ArgumentCaptor<String> ttlCaptor;

    @InjectMocks
    private LoginAttemptGuard loginAttemptGuard;

    @Test
    void isLocked_whenNoRecord_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(null);

        assertThat(loginAttemptGuard.isLocked(SCENE, IDENTIFIER)).isFalse();
    }

    @Test
    void isLocked_whenBelowThreshold_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("4");

        assertThat(loginAttemptGuard.isLocked(SCENE, IDENTIFIER)).isFalse();
    }

    @Test
    void isLocked_whenAtOrAboveThreshold_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn("5");

        assertThat(loginAttemptGuard.isLocked(SCENE, IDENTIFIER)).isTrue();
    }

    @Test
    void onLoginFailed_whenFirstFailure_setsExpiry() {
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(KEY)), eq("900"))).thenReturn(1L);

        loginAttemptGuard.onLoginFailed(SCENE, IDENTIFIER);

        verifyAtomicFailureScript();
    }

    @Test
    void onLoginFailed_whenSubsequentFailure_doesNotResetExpiry() {
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(KEY)), eq("900"))).thenReturn(3L);

        loginAttemptGuard.onLoginFailed(SCENE, IDENTIFIER);

        verifyAtomicFailureScript();
    }

    @Test
    void onLoginSucceeded_clearsFailureCounter() {
        loginAttemptGuard.onLoginSucceeded(SCENE, IDENTIFIER);

        verify(redisTemplate).delete(KEY);
    }

    private void verifyAtomicFailureScript() {
        verify(redisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(), ttlCaptor.capture());
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        assertThat(scriptCaptor.getValue().getResultType()).isEqualTo(Long.class);
        assertThat(scriptCaptor.getValue().getScriptAsString()).isEqualTo("""
                local count = redis.call('INCR', KEYS[1])
                if count == 1 or redis.call('TTL', KEYS[1]) < 0 then
                    redis.call('EXPIRE', KEYS[1], ARGV[1])
                end
                return count
                """);
        assertThat(keysCaptor.getValue()).containsExactly(KEY);
        assertThat(ttlCaptor.getValue()).isEqualTo("900");
        verifyNoMoreInteractions(redisTemplate);
        verifyNoInteractions(valueOperations);
    }
}
