package com.shadow.backend.auth.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shadow.backend.auth.constant.SmsScene;
import com.shadow.backend.auth.dto.LoginResponse;
import com.shadow.backend.auth.dto.PasswordLoginRequest;
import com.shadow.backend.auth.dto.RefreshTokenRequest;
import com.shadow.backend.auth.dto.RegisterRequest;
import com.shadow.backend.auth.dto.RegisterResponse;
import com.shadow.backend.auth.dto.ResetPasswordRequest;
import com.shadow.backend.auth.dto.ResubmitRequest;
import com.shadow.backend.auth.dto.SmsLoginRequest;
import com.shadow.backend.account.response.AuthResultCode;
import com.shadow.backend.auth.service.SmsService;
import com.shadow.backend.account.session.service.TokenService;
import com.shadow.backend.auth.vo.AuditStatusVO;
import com.shadow.backend.auth.vo.RefreshTokenResponse;
import com.shadow.backend.account.session.vo.TokenPair;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.common.util.LoginAttemptGuard;
import com.shadow.backend.common.util.PasswordUtil;
import com.shadow.backend.user.constant.AuditStatus;
import com.shadow.backend.user.entity.User;
import com.shadow.backend.user.mapper.UserMapper;
import com.shadow.backend.user.response.UserResultCode;
import com.shadow.backend.user.service.UserService;
import com.shadow.backend.user.vo.UserVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String LOGIN_ATTEMPT_SCENE = "app-password";
    private static final String PHONE = "13800138000";

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserService userService;
    @Mock
    private PasswordUtil passwordUtil;
    @Mock
    private SmsService smsService;
    @Mock
    private TokenService tokenService;
    @Mock
    private LoginAttemptGuard loginAttemptGuard;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Captor
    private ArgumentCaptor<LambdaUpdateWrapper<User>> updateCaptor;

    @InjectMocks
    private AuthServiceImpl authService;

    private User activeUser;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setPhone(PHONE);
        activeUser.setPassword("hashed");
        activeUser.setStatus(1);
        activeUser.setAuditStatus(AuditStatus.APPROVED.getValue());
    }

    // ---------- loginByPassword ----------

    @Test
    void loginByPassword_whenLocked_throwsLoginLocked() {
        PasswordLoginRequest req = passwordLoginRequest();
        when(loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, PHONE)).thenReturn(true);

        assertThatThrownBy(() -> authService.loginByPassword(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.LOGIN_LOCKED.getCode());

        verify(userService, never()).getByPhone(anyString());
    }

    @Test
    void loginByPassword_whenPasswordWrong_recordsFailureAndThrows() {
        PasswordLoginRequest req = passwordLoginRequest();
        when(loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, PHONE)).thenReturn(false);
        when(userService.getByPhone(PHONE)).thenReturn(activeUser);
        when(passwordUtil.verify(req.getPassword(), activeUser.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.loginByPassword(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(UserResultCode.LOGIN_FAILED.getCode());

        verify(loginAttemptGuard).onLoginFailed(LOGIN_ATTEMPT_SCENE, PHONE);
        verify(loginAttemptGuard, never()).onLoginSucceeded(anyString(), anyString());
    }

    @Test
    void loginByPassword_whenUserNotFound_recordsFailureAndThrowsLoginFailed() {
        PasswordLoginRequest req = passwordLoginRequest();
        when(loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, PHONE)).thenReturn(false);
        when(userService.getByPhone(PHONE)).thenReturn(null);

        assertThatThrownBy(() -> authService.loginByPassword(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(UserResultCode.LOGIN_FAILED.getCode());

        verify(loginAttemptGuard).onLoginFailed(LOGIN_ATTEMPT_SCENE, PHONE);
    }

    @Test
    void loginByPassword_whenSuccess_clearsAttemptsAndReturnsTokens() {
        PasswordLoginRequest req = passwordLoginRequest();
        when(loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, PHONE)).thenReturn(false);
        when(userService.getByPhone(PHONE)).thenReturn(activeUser);
        when(passwordUtil.verify(req.getPassword(), activeUser.getPassword())).thenReturn(true);
        when(tokenService.createTokens(activeUser.getId())).thenReturn(new TokenPair("access", "refresh"));
        UserVO vo = new UserVO();
        vo.setId(activeUser.getId());
        when(userService.getById(activeUser.getId())).thenReturn(vo);

        LoginResponse response = authService.loginByPassword(req);

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        verify(loginAttemptGuard).onLoginSucceeded(LOGIN_ATTEMPT_SCENE, PHONE);
        verify(loginAttemptGuard, never()).onLoginFailed(anyString(), anyString());
    }

    @Test
    void loginByPassword_whenAuditPending_throwsAuditPending() {
        activeUser.setAuditStatus(AuditStatus.PENDING.getValue());
        PasswordLoginRequest req = passwordLoginRequest();
        when(loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, PHONE)).thenReturn(false);
        when(userService.getByPhone(PHONE)).thenReturn(activeUser);
        when(passwordUtil.verify(req.getPassword(), activeUser.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.loginByPassword(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.USER_AUDIT_PENDING.getCode());
    }

    @Test
    void loginByPassword_whenAuditRejected_throwsWithRemarkAppended() {
        activeUser.setAuditStatus(AuditStatus.REJECTED.getValue());
        activeUser.setAuditRemark("资料不完整");
        PasswordLoginRequest req = passwordLoginRequest();
        when(loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, PHONE)).thenReturn(false);
        when(userService.getByPhone(PHONE)).thenReturn(activeUser);
        when(passwordUtil.verify(req.getPassword(), activeUser.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.loginByPassword(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("资料不完整");
    }

    @Test
    void loginByPassword_whenUserDisabled_throwsUserDisabled() {
        activeUser.setStatus(0);
        PasswordLoginRequest req = passwordLoginRequest();
        when(loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, PHONE)).thenReturn(false);
        when(userService.getByPhone(PHONE)).thenReturn(activeUser);
        when(passwordUtil.verify(req.getPassword(), activeUser.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.loginByPassword(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(UserResultCode.USER_DISABLED.getCode());
    }

    // ---------- loginBySms ----------

    @Test
    void loginBySms_whenPhoneNotRegistered_throws() {
        SmsLoginRequest req = new SmsLoginRequest();
        req.setPhone(PHONE);
        req.setCode("123456");
        when(userService.getByPhone(PHONE)).thenReturn(null);

        assertThatThrownBy(() -> authService.loginBySms(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.PHONE_NOT_REGISTERED.getCode());

        verify(smsService).verifyCode(PHONE, SmsScene.LOGIN, "123456");
    }

    // ---------- register ----------

    @Test
    void register_whenPhoneAlreadyRegistered_throws() {
        RegisterRequest req = new RegisterRequest();
        req.setPhone(PHONE);
        req.setPassword("password1");
        req.setCode("123456");
        when(userService.getByPhone(PHONE)).thenReturn(activeUser);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.PHONE_ALREADY_REGISTERED.getCode());

        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void register_whenSuccess_createsUserWithPendingAuditStatus() {
        RegisterRequest req = new RegisterRequest();
        req.setPhone(PHONE);
        req.setPassword("password1");
        req.setCode("123456");
        when(userService.getByPhone(PHONE)).thenReturn(null);
        when(passwordUtil.hash("password1")).thenReturn("hashed-pwd");
        UserVO vo = new UserVO();
        when(userService.getById(any())).thenReturn(vo);

        RegisterResponse response = authService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User inserted = captor.getValue();
        assertThat(inserted.getPhone()).isEqualTo(PHONE);
        assertThat(inserted.getPassword()).isEqualTo("hashed-pwd");
        assertThat(inserted.getAuditStatus()).isEqualTo(AuditStatus.PENDING.getValue());
        assertThat(response.getUser()).isSameAs(vo);
    }

    // ---------- resubmit ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 1, 3})
    void resubmit_whenNotRejected_throws(Integer auditStatus) {
        activeUser.setAuditStatus(auditStatus);
        ResubmitRequest req = resubmitRequest();
        when(userService.getByPhone(PHONE)).thenReturn(activeUser);

        assertThatThrownBy(() -> authService.resubmit(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.RESUBMIT_NOT_REJECTED.getCode());

        verify(smsService).verifyCode(PHONE, SmsScene.REGISTER, req.getCode());
        verify(userService).getByPhone(PHONE);
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(userMapper, passwordUtil, tokenService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "新昵称"})
    void resubmit_whenRejected_resetsAuditFieldsWithExplicitNullBindings(String nickname) {
        LocalDateTime oldTime = LocalDateTime.of(2025, 1, 1, 12, 0);
        activeUser.setStatus(0);
        activeUser.setNickname("旧昵称");
        activeUser.setAuditStatus(AuditStatus.REJECTED.getValue());
        activeUser.setAuditRemark("资料不完整");
        activeUser.setAuditTime(oldTime);
        activeUser.setUpdateTime(oldTime);
        ResubmitRequest req = resubmitRequest();
        req.setNickname(nickname);
        when(userService.getByPhone(PHONE)).thenReturn(activeUser);
        when(passwordUtil.hash(req.getPassword())).thenReturn("new-hashed");
        when(userMapper.update(isNull(), any())).thenReturn(1);
        UserVO persisted = new UserVO();
        persisted.setId(1L);
        persisted.setStatus(0);
        persisted.setNickname("新昵称".equals(nickname) ? nickname : "旧昵称");
        persisted.setAuditStatus(AuditStatus.PENDING.getValue());
        persisted.setUpdateTime(oldTime.plusDays(1));
        when(userService.getById(1L)).thenReturn(persisted);
        LocalDateTime before = LocalDateTime.now();

        RegisterResponse response = authService.resubmit(req);

        LambdaUpdateWrapper<User> wrapper = capturedResubmitUpdate();
        Map<String, Object> values = assertResubmitAssignments(wrapper, nickname);
        assertThat((LocalDateTime) values.get("update_time")).isBetween(before, LocalDateTime.now());
        assertThat(activeUser.getPassword()).isEqualTo("hashed");
        assertThat(activeUser.getNickname()).isEqualTo("旧昵称");
        assertThat(activeUser.getStatus()).isEqualTo(0);
        assertThat(activeUser.getAuditStatus()).isEqualTo(AuditStatus.REJECTED.getValue());
        assertThat(activeUser.getAuditRemark()).isEqualTo("资料不完整");
        assertThat(activeUser.getAuditTime()).isEqualTo(oldTime);
        assertThat(activeUser.getUpdateTime()).isEqualTo(oldTime);
        assertThat(response.getUser()).isSameAs(persisted);
        assertThat(response.getUser().getAuditRemark()).isNull();
        assertThat(response.getUser().getAuditTime()).isNull();
        InOrder order = inOrder(smsService, userService, userMapper);
        order.verify(smsService).verifyCode(PHONE, SmsScene.REGISTER, req.getCode());
        order.verify(userService).getByPhone(PHONE);
        order.verify(userMapper).update(isNull(), same(wrapper));
        order.verify(userService).getById(1L);
        order.verifyNoMoreInteractions();
        verify(passwordUtil).hash(req.getPassword());
        verifyNoInteractions(tokenService);
    }

    @Test
    void resubmit_whenLosingRace_throwsWithoutReloading() {
        activeUser.setAuditStatus(AuditStatus.REJECTED.getValue());
        activeUser.setAuditRemark("资料不完整");
        ResubmitRequest req = resubmitRequest();
        req.setNickname("新昵称");
        when(userService.getByPhone(PHONE)).thenReturn(activeUser);
        when(passwordUtil.hash(req.getPassword())).thenReturn("new-hashed");
        // 读取时为驳回状态，但另一请求已抢先重新提交。
        when(userMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> authService.resubmit(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.RESUBMIT_NOT_REJECTED.getCode());

        assertResubmitAssignments(capturedResubmitUpdate(), req.getNickname());
        assertThat(activeUser.getAuditStatus()).isEqualTo(AuditStatus.REJECTED.getValue());
        assertThat(activeUser.getAuditRemark()).isEqualTo("资料不完整");
        assertThat(activeUser.getPassword()).isEqualTo("hashed");
        verify(smsService).verifyCode(PHONE, SmsScene.REGISTER, req.getCode());
        verify(userService).getByPhone(PHONE);
        verifyNoMoreInteractions(userService, userMapper);
        verifyNoInteractions(tokenService);
    }

    @Test
    void resubmit_whenPhoneNotRegistered_throws() {
        ResubmitRequest req = resubmitRequest();
        when(userService.getByPhone(PHONE)).thenReturn(null);

        assertThatThrownBy(() -> authService.resubmit(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.PHONE_NOT_REGISTERED.getCode());

        verify(smsService).verifyCode(PHONE, SmsScene.REGISTER, req.getCode());
        verify(userService).getByPhone(PHONE);
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(userMapper, passwordUtil, tokenService);
    }

    // ---------- resetPassword ----------

    @Test
    void resetPassword_whenPhoneNotRegistered_throws() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setPhone(PHONE);
        req.setNewPassword("newpassword1");
        req.setCode("123456");
        when(userService.getByPhone(PHONE)).thenReturn(null);

        assertThatThrownBy(() -> authService.resetPassword(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.PHONE_NOT_REGISTERED.getCode());

        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void resetPassword_whenSuccess_updatesHashedPassword() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setPhone(PHONE);
        req.setNewPassword("newpassword1");
        req.setCode("123456");
        when(userService.getByPhone(PHONE)).thenReturn(activeUser);
        when(passwordUtil.hash("newpassword1")).thenReturn("hashed-new");

        authService.resetPassword(req);

        assertThat(activeUser.getPassword()).isEqualTo("hashed-new");
        verify(userMapper).updateById(activeUser);
        verify(tokenService).revokeUserTokens(1L);
    }

    @Test
    void refresh_whenMissing_rejectsWithoutRotating() {
        RefreshTokenRequest request = refreshRequest();
        when(tokenService.getUserIdByRefreshToken("refresh")).thenReturn(null);
        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.REFRESH_TOKEN_INVALID.getCode());
        verify(tokenService, never()).refreshToken(anyString());
        verify(userService, never()).getById(any());
    }

    @Test
    void refresh_whenDisabled_rejectsWithoutRotating() {
        RefreshTokenRequest request = refreshRequest();
        UserVO user = new UserVO();
        user.setStatus(0);
        when(tokenService.getUserIdByRefreshToken("refresh")).thenReturn(1L);
        when(userService.getById(1L)).thenReturn(user);
        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(UserResultCode.USER_DISABLED.getCode());
        verify(tokenService, never()).refreshToken(anyString());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void refresh_whenNotApproved_rejectsWithoutRotating(int auditStatus) {
        RefreshTokenRequest request = refreshRequest();
        UserVO user = new UserVO();
        user.setStatus(1);
        user.setAuditStatus(auditStatus);
        when(tokenService.getUserIdByRefreshToken("refresh")).thenReturn(1L);
        when(userService.getById(1L)).thenReturn(user);
        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.REFRESH_TOKEN_INVALID.getCode());
        verify(tokenService, never()).refreshToken(anyString());
    }

    @Test
    void refresh_whenApproved_returnsRotatedTokens() {
        UserVO user = new UserVO();
        user.setStatus(1);
        user.setAuditStatus(1);
        when(tokenService.getUserIdByRefreshToken("refresh")).thenReturn(1L);
        when(userService.getById(1L)).thenReturn(user);
        when(tokenService.refreshToken("refresh")).thenReturn(new TokenPair("new-access", "new-refresh"));
        RefreshTokenResponse response = authService.refresh(refreshRequest());
        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void logout_removesCurrentDeviceRefreshToken() {
        when(tokenService.getCurrentDeviceRefreshToken()).thenReturn("device-a");
        authService.logout();
        verify(tokenService).removeTokens("device-a");
        verify(tokenService, never()).revokeUserTokens(any());
    }

    @Test
    void getAuditStatus_whenRateLimited_skipsUserLookup() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("audit:query:limit:" + PHONE, "1", Duration.ofSeconds(60)))
                .thenReturn(false);
        assertThatThrownBy(() -> authService.getAuditStatus(PHONE))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.AUDIT_QUERY_TOO_FREQUENT.getCode());
        verify(userService, never()).getByPhone(anyString());
    }

    @Test
    void getAuditStatus_whenAllowed_returnsOnlyStatusAndMaskedPhone() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("audit:query:limit:" + PHONE, "1", Duration.ofSeconds(60)))
                .thenReturn(true);
        when(userService.getByPhone(PHONE)).thenReturn(activeUser);
        AuditStatusVO result = authService.getAuditStatus(PHONE);
        assertThat(result.getPhone()).isEqualTo("138****8000");
        assertThat(result.getAuditStatus()).isEqualTo(1);
        assertThat(AuditStatusVO.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("auditStatus", "phone");
    }

    @Test
    void getAuditStatus_whenUnknown_stillConsumesRateLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("audit:query:limit:" + PHONE, "1", Duration.ofSeconds(60)))
                .thenReturn(true);
        assertThatThrownBy(() -> authService.getAuditStatus(PHONE))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AuthResultCode.PHONE_NOT_REGISTERED.getCode());
        verify(redisTemplate, never()).delete(anyString());
    }

    private RefreshTokenRequest refreshRequest() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh");
        return request;
    }

    // ---------- helpers ----------

    private LambdaUpdateWrapper<User> capturedResubmitUpdate() {
        verify(userMapper).update(isNull(), updateCaptor.capture());
        verify(userMapper, never()).updateById(any(User.class));
        LambdaUpdateWrapper<User> wrapper = updateCaptor.getValue();
        assertThat(wrapper.getEntity()).isNull();
        String sql = wrapper.getSqlSegment();
        assertThat(sql).startsWith("(").endsWith(")");
        Map<String, Object> conditions = boundValues(wrapper, sql.substring(1, sql.length() - 1).split(" AND "));
        assertThat(conditions).containsExactlyInAnyOrderEntriesOf(
                Map.of("id", activeUser.getId(), "audit_status", AuditStatus.REJECTED.getValue()));
        return wrapper;
    }

    private Map<String, Object> assertResubmitAssignments(LambdaUpdateWrapper<User> wrapper, String nickname) {
        Map<String, Object> values = boundValues(wrapper, wrapper.getSqlSet().split(","));
        if ("新昵称".equals(nickname)) {
            assertThat(values).containsOnlyKeys("password", "nickname", "audit_status",
                            "audit_remark", "audit_time", "update_time")
                    .containsEntry("nickname", nickname);
        } else {
            assertThat(values).containsOnlyKeys("password", "audit_status", "audit_remark", "audit_time", "update_time");
        }
        assertThat(values).containsEntry("password", "new-hashed")
                .containsEntry("audit_status", AuditStatus.PENDING.getValue())
                .containsEntry("audit_remark", null)
                .containsEntry("audit_time", null);
        assertThat(values.get("update_time")).isInstanceOf(LocalDateTime.class);
        return values;
    }

    private Map<String, Object> boundValues(LambdaUpdateWrapper<User> wrapper, String[] assignments) {
        Map<String, Object> values = new LinkedHashMap<>();
        String prefix = "#{ew.paramNameValuePairs.";
        for (String assignment : assignments) {
            String[] parts = assignment.split("=", 2);
            assertThat(parts).hasSize(2);
            String column = parts[0].trim();
            String placeholder = parts[1].trim();
            assertThat(placeholder).startsWith(prefix).endsWith("}");
            String parameter = placeholder.substring(prefix.length(), placeholder.length() - 1);
            // containsKey 区分显式绑定 null 与根本没有生成该参数。
            assertThat(wrapper.getParamNameValuePairs()).containsKey(parameter);
            assertThat(values).doesNotContainKey(column);
            values.put(column, wrapper.getParamNameValuePairs().get(parameter));
        }
        return values;
    }

    private PasswordLoginRequest passwordLoginRequest() {
        PasswordLoginRequest req = new PasswordLoginRequest();
        req.setPhone(PHONE);
        req.setPassword("password1");
        return req;
    }

    private ResubmitRequest resubmitRequest() {
        ResubmitRequest req = new ResubmitRequest();
        req.setPhone(PHONE);
        req.setPassword("newpassword1");
        req.setCode("123456");
        return req;
    }
}
