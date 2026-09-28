package com.shadow.backend.account.session.service.impl;

import cn.dev33.satoken.session.SaSession;
import com.shadow.backend.account.response.AuthResultCode;
import com.shadow.backend.account.session.vo.TokenPair;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.account.session.util.StpAppUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {

    private static final String REFRESH_TTL_SECONDS = "604800";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SetOperations<String, String> setOperations;
    @Mock
    private SaSession tokenSession;
    @Captor
    private ArgumentCaptor<RedisScript<Long>> scriptCaptor;
    @Captor
    private ArgumentCaptor<List<String>> keysCaptor;
    @Captor
    private ArgumentCaptor<String> refreshCaptor;

    @InjectMocks
    private TokenServiceImpl tokenService;

    @Test
    void createTokens_atomicallyRegistersBeforeLoginAndRechecksAfterBinding() {
        stubRegistration();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("42");

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            stubTokenCreation(stpAppUtil, "access-token");
            stpAppUtil.when(() -> StpAppUtil.login(42L)).thenAnswer(invocation -> {
                verify(redisTemplate).execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(),
                        eq("42"), anyString(), eq(REFRESH_TTL_SECONDS));
                verifyNoInteractions(valueOperations, tokenSession);
                return null;
            });

            TokenPair tokens = tokenService.createTokens(42L);

            assertThat(tokens.getAccessToken()).isEqualTo("access-token");
            assertThat(tokens.getRefreshToken()).isEqualTo(verifyRegistration());
            assertThat(scriptCaptor.getValue().getResultType()).isEqualTo(Long.class);
            assertThat(scriptCaptor.getValue().getScriptAsString()).isEqualTo("""
                    redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
                    redis.call('SADD', KEYS[2], ARGV[2])
                    redis.call('EXPIRE', KEYS[2], ARGV[3])
                    return 1
                    """);
            InOrder order = inOrder(tokenSession, valueOperations);
            order.verify(tokenSession).set("refreshToken", tokens.getRefreshToken());
            order.verify(valueOperations).get("auth:refresh:" + tokens.getRefreshToken());
            verify(redisTemplate).opsForValue();
            verifyNoMoreInteractions(redisTemplate, valueOperations, tokenSession);
            verifyNoInteractions(setOperations);
            verifyCurrentDeviceLogin(stpAppUtil);
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "99")
    void createTokens_whenMappingChangesDuringLogin_rejectsAndLogsOutOnlyCurrentDevice(String ownerAfterLogin) {
        stubRegistration();
        AtomicReference<String> owner = new AtomicReference<>("42");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> owner.get());

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            stubTokenCreation(stpAppUtil, "access-token");
            stpAppUtil.when(() -> StpAppUtil.login(42L)).thenAnswer(invocation -> {
                owner.set(ownerAfterLogin);
                return null;
            });

            assertInvalidRefresh(() -> tokenService.createTokens(42L));

            String refreshToken = verifyRegistration();
            verify(tokenSession).set("refreshToken", refreshToken);
            verify(valueOperations).get("auth:refresh:" + refreshToken);
            verifyRefreshCleanup(refreshToken);
            stpAppUtil.verify(StpAppUtil::logout);
            verifyCurrentDeviceLogin(stpAppUtil);
        }
    }

    @Test
    void createTokens_whenRegistrationFails_cleansOnlyNewRefreshAndPreservesFailure() {
        RuntimeException failure = new IllegalStateException("registration failed");
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(),
                eq("42"), anyString(), eq(REFRESH_TTL_SECONDS))).thenThrow(failure);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            assertThatThrownBy(() -> tokenService.createTokens(42L)).isSameAs(failure);

            verifyRefreshCleanup(verifyRegistration());
            verifyNoInteractions(valueOperations, tokenSession);
            stpAppUtil.verifyNoInteractions();
        }
    }

    @Test
    void createTokens_whenLoginFails_cleansOnlyNewRefreshAndPreservesFailure() {
        stubRegistration();
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        RuntimeException failure = new IllegalStateException("login failed");

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            stpAppUtil.when(() -> StpAppUtil.login(42L)).thenThrow(failure);

            assertThatThrownBy(() -> tokenService.createTokens(42L)).isSameAs(failure);

            verifyRefreshCleanup(verifyRegistration());
            verifyNoInteractions(valueOperations, tokenSession);
            stpAppUtil.verify(() -> StpAppUtil.login(42L));
            stpAppUtil.verifyNoMoreInteractions();
        }
    }

    @Test
    void createTokens_whenBindingAndCleanupFail_preservesOriginalAndSuppressedErrors() {
        stubRegistration();
        RuntimeException failure = new IllegalStateException("binding failed");
        RuntimeException logoutFailure = new IllegalStateException("logout failed");
        RuntimeException deleteFailure = new IllegalStateException("delete failed");
        RuntimeException removeFailure = new IllegalStateException("remove failed");
        when(tokenSession.set(eq("refreshToken"), anyString())).thenThrow(failure);
        when(redisTemplate.delete(anyString())).thenThrow(deleteFailure);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.remove(eq("auth:refresh:user:42"), anyString())).thenThrow(removeFailure);

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            stubTokenCreation(stpAppUtil, "access-token");
            stpAppUtil.when(StpAppUtil::logout).thenThrow(logoutFailure);

            assertThatThrownBy(() -> tokenService.createTokens(42L)).isSameAs(failure);

            assertThat(failure.getSuppressed()).containsExactly(logoutFailure, deleteFailure, removeFailure);
            verifyRefreshCleanup(verifyRegistration());
            verifyNoInteractions(valueOperations);
            stpAppUtil.verify(StpAppUtil::logout);
            verifyCurrentDeviceLogin(stpAppUtil);
        }
    }

    @Test
    void refreshToken_whenValid_atomicallyRotatesBeforeLoginAndRechecksAfterBinding() {
        stubRotation();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("42");

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            stubTokenCreation(stpAppUtil, "new-access-token");
            stpAppUtil.when(() -> StpAppUtil.login(42L)).thenAnswer(invocation -> {
                verify(redisTemplate).execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(),
                        eq("42"), eq("old-token"), anyString(), eq(REFRESH_TTL_SECONDS));
                verifyNoInteractions(tokenSession);
                return null;
            });

            TokenPair tokens = tokenService.refreshToken("old-token");

            assertThat(tokens.getAccessToken()).isEqualTo("new-access-token");
            assertThat(tokens.getRefreshToken()).isEqualTo(verifyRotation());
            assertThat(scriptCaptor.getValue().getResultType()).isEqualTo(Long.class);
            assertThat(scriptCaptor.getValue().getScriptAsString()).isEqualTo("""
                    if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                        return 0
                    end
                    redis.call('GETDEL', KEYS[1])
                    redis.call('SREM', KEYS[3], ARGV[2])
                    redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[4])
                    redis.call('SADD', KEYS[3], ARGV[3])
                    redis.call('EXPIRE', KEYS[3], ARGV[4])
                    return 1
                    """);
            InOrder order = inOrder(valueOperations, redisTemplate, tokenSession);
            order.verify(valueOperations).get("auth:refresh:old-token");
            order.verify(redisTemplate).execute(ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(keysCaptor.getValue()), eq("42"), eq("old-token"),
                    eq(tokens.getRefreshToken()), eq(REFRESH_TTL_SECONDS));
            order.verify(tokenSession).set("refreshToken", tokens.getRefreshToken());
            order.verify(valueOperations).get("auth:refresh:" + tokens.getRefreshToken());
            verify(redisTemplate, times(2)).opsForValue();
            verifyNoMoreInteractions(redisTemplate, valueOperations, tokenSession);
            verifyNoInteractions(setOperations);
            verifyCurrentDeviceLogin(stpAppUtil);
        }
    }

    @Test
    void refreshToken_whenTwoCallsReadOldValue_onlyFirstScriptConsumesIt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get(anyString())).thenReturn("42");
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(),
                eq("42"), eq("old-token"), anyString(), eq(REFRESH_TTL_SECONDS))).thenReturn(1L, 0L);

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            stubTokenCreation(stpAppUtil, "new-access-token");

            TokenPair tokens = tokenService.refreshToken("old-token");
            assertInvalidRefresh(() -> tokenService.refreshToken("old-token"));

            verify(redisTemplate, times(2)).execute(scriptCaptor.capture(), keysCaptor.capture(),
                    eq("42"), eq("old-token"), refreshCaptor.capture(), eq(REFRESH_TTL_SECONDS));
            List<String> generatedTokens = refreshCaptor.getAllValues();
            assertThat(generatedTokens).hasSize(2).doesNotHaveDuplicates();
            assertThat(tokens.getRefreshToken()).isEqualTo(generatedTokens.getFirst());
            for (int i = 0; i < generatedTokens.size(); i++) {
                assertThat(keysCaptor.getAllValues().get(i)).containsExactly("auth:refresh:old-token",
                        "auth:refresh:" + generatedTokens.get(i), "auth:refresh:user:42");
            }
            verifyRefreshCleanup(generatedTokens.getLast());
            verify(valueOperations, times(2)).get("auth:refresh:old-token");
            verify(valueOperations).get("auth:refresh:" + tokens.getRefreshToken());
            verify(tokenSession).set("refreshToken", tokens.getRefreshToken());
            verifyNoMoreInteractions(valueOperations, tokenSession);
            verify(redisTemplate, never()).execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(),
                    anyString(), anyString(), anyString());
            verifyCurrentDeviceLogin(stpAppUtil);
        }
    }

    @Test
    void refreshToken_whenNotFound_throwsInvalidWithoutCreatingTokens() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:refresh:bad-token")).thenReturn(null);

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            assertInvalidRefresh(() -> tokenService.refreshToken("bad-token"));

            verify(redisTemplate).opsForValue();
            verify(valueOperations).get("auth:refresh:bad-token");
            verifyNoMoreInteractions(redisTemplate, valueOperations);
            verifyNoInteractions(setOperations, tokenSession);
            stpAppUtil.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = 0)
    void refreshToken_whenScriptDoesNotSucceed_rejectsWithoutLogin(Long result) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get("auth:refresh:old-token")).thenReturn("42");
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(),
                eq("42"), eq("old-token"), anyString(), eq(REFRESH_TTL_SECONDS))).thenReturn(result);

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            assertInvalidRefresh(() -> tokenService.refreshToken("old-token"));

            verifyRefreshCleanup(verifyRotation());
            verify(valueOperations).get("auth:refresh:old-token");
            verifyNoMoreInteractions(valueOperations);
            verifyNoInteractions(tokenSession);
            stpAppUtil.verifyNoInteractions();
        }
    }

    @Test
    void refreshToken_whenRevocationWinsAfterRead_doesNotRegisterOrLogin() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get("auth:refresh:old-token")).thenReturn("42");
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("auth:refresh:user:42")), eq("auth:refresh:"))).thenReturn(1L);
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(),
                eq("42"), eq("old-token"), anyString(), eq(REFRESH_TTL_SECONDS))).thenAnswer(invocation -> {
                    tokenService.revokeUserTokens(42L);
                    return 0L;
                });

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            assertInvalidRefresh(() -> tokenService.refreshToken("old-token"));

            verifyRefreshCleanup(verifyRotation());
            verify(valueOperations).get("auth:refresh:old-token");
            verifyNoMoreInteractions(valueOperations);
            verifyNoInteractions(tokenSession);
            verify(redisTemplate, never()).execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(),
                    anyString(), anyString(), anyString());
            stpAppUtil.verify(() -> StpAppUtil.logoutUser(42L));
            stpAppUtil.verifyNoMoreInteractions();
        }
    }

    // Answer 仅模拟脚本结果与调用穿插，不执行 Lua 或真实并发。
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void refreshToken_whenRevokedBeforeOrAfterLogin_rejectsNewDevice(boolean revokeBeforeLogin) {
        List<String> events = new ArrayList<>();
        AtomicReference<String> owner = new AtomicReference<>("42");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> owner.get());
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("auth:refresh:user:42")), eq("auth:refresh:"))).thenAnswer(invocation -> {
                    events.add("revoke");
                    owner.set(null);
                    return 1L;
                });
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(),
                eq("42"), eq("old-token"), anyString(), eq(REFRESH_TTL_SECONDS))).thenAnswer(invocation -> {
                    events.add("rotate");
                    if (revokeBeforeLogin) {
                        tokenService.revokeUserTokens(42L);
                    }
                    return 1L;
                });
        when(tokenSession.set(eq("refreshToken"), anyString())).thenAnswer(invocation -> {
            events.add("bind");
            if (!revokeBeforeLogin) {
                tokenService.revokeUserTokens(42L);
            }
            return tokenSession;
        });

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            stubTokenCreation(stpAppUtil, "new-access-token");
            stpAppUtil.when(() -> StpAppUtil.login(42L)).thenAnswer(invocation -> {
                events.add("login");
                return null;
            });
            stpAppUtil.when(() -> StpAppUtil.logoutUser(42L)).thenAnswer(invocation -> {
                events.add("logout-user");
                return null;
            });
            stpAppUtil.when(StpAppUtil::logout).thenAnswer(invocation -> {
                events.add("logout-device");
                return null;
            });

            assertInvalidRefresh(() -> tokenService.refreshToken("old-token"));

            assertThat(events).containsExactlyElementsOf(revokeBeforeLogin
                    ? List.of("rotate", "revoke", "logout-user", "login", "bind", "logout-device")
                    : List.of("rotate", "login", "bind", "revoke", "logout-user", "logout-device"));
            String newRefreshToken = verifyRotation();
            verify(tokenSession).set("refreshToken", newRefreshToken);
            verify(valueOperations).get("auth:refresh:" + newRefreshToken);
            verifyRefreshCleanup(newRefreshToken);
            verify(redisTemplate, never()).execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(),
                    anyString(), anyString(), anyString());
            stpAppUtil.verify(() -> StpAppUtil.logoutUser(42L));
            stpAppUtil.verify(StpAppUtil::logout);
            verifyCurrentDeviceLogin(stpAppUtil);
        }
    }

    @Test
    void refreshToken_whenFinalReadFails_cleansNewRefreshAndPreservesFailure() {
        stubRotation();
        RuntimeException failure = new IllegalStateException("read failed");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> {
            if ("auth:refresh:old-token".equals(invocation.getArgument(0))) {
                return "42";
            }
            throw failure;
        });

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            stubTokenCreation(stpAppUtil, "new-access-token");

            assertThatThrownBy(() -> tokenService.refreshToken("old-token")).isSameAs(failure);

            verifyRefreshCleanup(verifyRotation());
            stpAppUtil.verify(StpAppUtil::logout);
            verifyCurrentDeviceLogin(stpAppUtil);
        }
    }

    @Test
    void removeTokens_whenPresent_removesOnlyCurrentDeviceTokenAndLogsOut() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get("auth:refresh:device-token")).thenReturn("42");

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            tokenService.removeTokens("device-token");

            verify(redisTemplate).opsForValue();
            verify(redisTemplate).opsForSet();
            verify(valueOperations).get("auth:refresh:device-token");
            verify(setOperations).remove("auth:refresh:user:42", "device-token");
            verify(redisTemplate).delete("auth:refresh:device-token");
            verifyNoMoreInteractions(redisTemplate, valueOperations, setOperations);
            verifyNoInteractions(tokenSession);
            stpAppUtil.verify(StpAppUtil::logout);
            stpAppUtil.verifyNoMoreInteractions();
        }
    }

    @Test
    void removeTokens_whenExpired_deletesKeyAndLogsOutWithoutChangingUserSet() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:refresh:expired-token")).thenReturn(null);

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            tokenService.removeTokens("expired-token");

            verify(redisTemplate).opsForValue();
            verify(valueOperations).get("auth:refresh:expired-token");
            verify(redisTemplate).delete("auth:refresh:expired-token");
            verifyNoMoreInteractions(redisTemplate, valueOperations);
            verifyNoInteractions(setOperations, tokenSession);
            stpAppUtil.verify(StpAppUtil::logout);
            stpAppUtil.verifyNoMoreInteractions();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    void removeTokens_whenNullOrEmpty_skipsRedisButStillLogsOut(String refreshToken) {
        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            tokenService.removeTokens(refreshToken);

            verifyNoInteractions(redisTemplate, valueOperations, setOperations, tokenSession);
            stpAppUtil.verify(StpAppUtil::logout);
            stpAppUtil.verifyNoMoreInteractions();
        }
    }

    @Test
    void getCurrentDeviceRefreshToken_whenPresent_readsTokenSession() {
        when(tokenSession.get("refreshToken")).thenReturn("device-token");

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            stpAppUtil.when(StpAppUtil::getTokenSession).thenReturn(tokenSession);

            assertThat(tokenService.getCurrentDeviceRefreshToken()).isEqualTo("device-token");

            verify(tokenSession).get("refreshToken");
            verifyNoMoreInteractions(tokenSession);
            verifyNoInteractions(redisTemplate, valueOperations, setOperations);
            stpAppUtil.verify(StpAppUtil::getTokenSession);
            stpAppUtil.verify(StpAppUtil::getSession, never());
            stpAppUtil.verifyNoMoreInteractions();
        }
    }

    @Test
    void getCurrentDeviceRefreshToken_whenAbsent_returnsNull() {
        when(tokenSession.get("refreshToken")).thenReturn(null);

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            stpAppUtil.when(StpAppUtil::getTokenSession).thenReturn(tokenSession);

            assertThat(tokenService.getCurrentDeviceRefreshToken()).isNull();

            verify(tokenSession).get("refreshToken");
            verifyNoMoreInteractions(tokenSession);
            verifyNoInteractions(redisTemplate, valueOperations, setOperations);
            stpAppUtil.verify(StpAppUtil::getTokenSession);
            stpAppUtil.verify(StpAppUtil::getSession, never());
            stpAppUtil.verifyNoMoreInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 2})
    void revokeUserTokens_atomicallyDeletesMappingsAndRegistryBeforeUserLogout(long tokenCount) {
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("auth:refresh:user:42")), eq("auth:refresh:"))).thenReturn(tokenCount);

        try (MockedStatic<StpAppUtil> stpAppUtil = Mockito.mockStatic(StpAppUtil.class)) {
            stpAppUtil.when(() -> StpAppUtil.logoutUser(42L)).thenAnswer(invocation -> {
                verify(redisTemplate).execute(ArgumentMatchers.<RedisScript<Long>>any(),
                        eq(List.of("auth:refresh:user:42")), eq("auth:refresh:"));
                return null;
            });

            tokenService.revokeUserTokens(42L);

            verify(redisTemplate).execute(scriptCaptor.capture(),
                    eq(List.of("auth:refresh:user:42")), eq("auth:refresh:"));
            assertThat(scriptCaptor.getValue().getResultType()).isEqualTo(Long.class);
            assertThat(scriptCaptor.getValue().getScriptAsString()).isEqualTo("""
                    local tokens = redis.call('SMEMBERS', KEYS[1])
                    for _, token in ipairs(tokens) do
                        redis.call('DEL', ARGV[1] .. token)
                    end
                    redis.call('DEL', KEYS[1])
                    return #tokens
                    """);
            verifyNoMoreInteractions(redisTemplate);
            verifyNoInteractions(valueOperations, setOperations, tokenSession);
            stpAppUtil.verify(() -> StpAppUtil.logoutUser(42L));
            stpAppUtil.verifyNoMoreInteractions();
        }
    }

    @Test
    void getUserIdByRefreshToken_whenPresent_returnsUserIdWithoutConsumingToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:refresh:valid-token")).thenReturn("42");

        assertThat(tokenService.getUserIdByRefreshToken("valid-token")).isEqualTo(42L);

        verify(redisTemplate).opsForValue();
        verify(valueOperations).get("auth:refresh:valid-token");
        verifyNoMoreInteractions(redisTemplate, valueOperations);
        verifyNoInteractions(setOperations, tokenSession);
    }

    @Test
    void getUserIdByRefreshToken_whenNotFound_returnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:refresh:missing-token")).thenReturn(null);

        assertThat(tokenService.getUserIdByRefreshToken("missing-token")).isNull();

        verify(redisTemplate).opsForValue();
        verify(valueOperations).get("auth:refresh:missing-token");
        verifyNoMoreInteractions(redisTemplate, valueOperations);
        verifyNoInteractions(setOperations, tokenSession);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void getUserIdByRefreshToken_whenNullOrEmpty_returnsNullWithoutRedisAccess(String refreshToken) {
        assertThat(tokenService.getUserIdByRefreshToken(refreshToken)).isNull();

        verifyNoInteractions(redisTemplate, valueOperations, setOperations, tokenSession);
    }

    private void stubRegistration() {
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(),
                eq("42"), anyString(), eq(REFRESH_TTL_SECONDS))).thenReturn(1L);
    }

    private void stubRotation() {
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(),
                eq("42"), eq("old-token"), anyString(), eq(REFRESH_TTL_SECONDS))).thenReturn(1L);
    }

    private void stubTokenCreation(MockedStatic<StpAppUtil> stpAppUtil, String accessToken) {
        stpAppUtil.when(StpAppUtil::getTokenValue).thenReturn(accessToken);
        stpAppUtil.when(StpAppUtil::getTokenSession).thenReturn(tokenSession);
    }

    private String verifyRegistration() {
        verify(redisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(),
                eq("42"), refreshCaptor.capture(), eq(REFRESH_TTL_SECONDS));
        String refreshToken = refreshCaptor.getValue();
        assertThat(refreshToken).matches("[0-9a-f]{32}");
        assertThat(keysCaptor.getValue()).containsExactly(
                "auth:refresh:" + refreshToken, "auth:refresh:user:42");
        return refreshToken;
    }

    private String verifyRotation() {
        verify(redisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(),
                eq("42"), eq("old-token"), refreshCaptor.capture(), eq(REFRESH_TTL_SECONDS));
        String refreshToken = refreshCaptor.getValue();
        assertThat(refreshToken).matches("[0-9a-f]{32}").isNotEqualTo("old-token");
        assertThat(keysCaptor.getValue()).containsExactly(
                "auth:refresh:old-token", "auth:refresh:" + refreshToken, "auth:refresh:user:42");
        return refreshToken;
    }

    private void verifyRefreshCleanup(String refreshToken) {
        verify(redisTemplate).delete("auth:refresh:" + refreshToken);
        verify(redisTemplate, times(1)).delete(anyString());
        verify(setOperations).remove("auth:refresh:user:42", refreshToken);
        verifyNoMoreInteractions(setOperations);
    }

    private void verifyCurrentDeviceLogin(MockedStatic<StpAppUtil> stpAppUtil) {
        stpAppUtil.verify(() -> StpAppUtil.login(42L));
        stpAppUtil.verify(StpAppUtil::getTokenValue);
        stpAppUtil.verify(StpAppUtil::getTokenSession);
        stpAppUtil.verify(StpAppUtil::getSession, never());
        stpAppUtil.verifyNoMoreInteractions();
    }

    private void assertInvalidRefresh(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.REFRESH_TOKEN_INVALID.getCode());
    }
}
