package com.shadow.backend.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shadow.backend.account.session.service.TokenService;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.common.response.ResultCode;
import com.shadow.backend.common.util.PasswordUtil;
import com.shadow.backend.user.dto.AuditUserRequest;
import com.shadow.backend.user.dto.ChangePasswordRequest;
import com.shadow.backend.user.dto.UpdateProfileRequest;
import com.shadow.backend.user.dto.UpdateUserRequest;
import com.shadow.backend.user.entity.User;
import com.shadow.backend.user.mapper.UserMapper;
import com.shadow.backend.user.response.UserResultCode;
import com.shadow.backend.user.vo.UserVO;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final LocalDateTime OLD_TIME = LocalDateTime.of(2025, 1, 1, 12, 0);

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordUtil passwordUtil;
    @Mock
    private TokenService tokenService;

    @Captor
    private ArgumentCaptor<LambdaUpdateWrapper<User>> updateCaptor;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    @BeforeEach
    void setUp() {
        user = existingUser();
    }

    // ---------- changePassword ----------

    @Test
    void changePassword_whenUserNotFound_throws() {
        when(userMapper.selectById(1L)).thenReturn(null);
        ChangePasswordRequest req = changePasswordRequest();

        assertThatThrownBy(() -> userService.changePassword(1L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(UserResultCode.USER_NOT_FOUND.getCode());
    }

    @Test
    void changePassword_whenOldPasswordIncorrect_throws() {
        when(userMapper.selectById(1L)).thenReturn(user);
        ChangePasswordRequest req = changePasswordRequest();
        when(passwordUtil.verify(req.getOldPassword(), user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(1L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(UserResultCode.OLD_PASSWORD_INCORRECT.getCode());

        verify(userMapper, never()).updateById(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void changePassword_whenSuccess_updatesHashedPassword() {
        when(userMapper.selectById(1L)).thenReturn(user);
        ChangePasswordRequest req = changePasswordRequest();
        when(passwordUtil.verify(req.getOldPassword(), user.getPassword())).thenReturn(true);
        when(passwordUtil.hash(req.getNewPassword())).thenReturn("hashed-new");

        userService.changePassword(1L, req);

        assertThat(user.getPassword()).isEqualTo("hashed-new");
        verify(userMapper).updateById(user);
    }

    // ---------- audit ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 3})
    void audit_whenInvalidStatus_rejectsBeforeDatabaseAccess(Integer auditStatus) {
        assertThatThrownBy(() -> userService.audit(1L, auditStatus, "资料不完整"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST.getCode());

        verifyNoInteractions(userMapper, passwordUtil, tokenService);
    }

    @Test
    void audit_whenUserNotFound_throws() {
        when(userMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> userService.audit(1L, 1, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(UserResultCode.USER_NOT_FOUND.getCode());

        verify(userMapper).selectById(1L);
        verifyNoMoreInteractions(userMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void audit_whenAlreadyAuditedOrLosingRace_throwsWithoutReloading(int currentStatus) {
        // 读到待审核也可能在条件更新前被其他请求抢先审核。
        user.setAuditStatus(currentStatus);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> userService.audit(1L, 2, "资料不完整"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(UserResultCode.USER_ALREADY_AUDITED.getCode());

        LambdaUpdateWrapper<User> wrapper = capturedUpdate();
        assertWhere(wrapper, 0);
        assertAuditAssignments(wrapper, 2, "资料不完整");
        assertThat(user.getAuditStatus()).isEqualTo(currentStatus);
        verify(userMapper).selectById(1L);
        verifyNoMoreInteractions(userMapper);
        verifyNoInteractions(tokenService);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void audit_whenPending_updatesOnlyAuditFieldsAndReturnsReloadedUser(int auditStatus) {
        String remark = auditStatus == 1 ? null : "资料不完整";
        User persisted = existingUser();
        persisted.setNickname("数据库中的最新昵称");
        persisted.setAuditStatus(auditStatus);
        persisted.setAuditRemark(remark);
        persisted.setAuditTime(OLD_TIME.plusDays(1));
        persisted.setUpdateTime(OLD_TIME.plusDays(1));
        when(userMapper.selectById(1L)).thenReturn(user, persisted);
        when(userMapper.update(isNull(), any())).thenReturn(1);
        LocalDateTime before = LocalDateTime.now();

        UserVO vo = userService.audit(1L, auditStatus, remark);

        LambdaUpdateWrapper<User> wrapper = capturedUpdate();
        assertWhere(wrapper, 0);
        assertAuditAssignments(wrapper, auditStatus, remark);
        assertThat((LocalDateTime) setValues(wrapper).get("audit_time"))
                .isBetween(before, LocalDateTime.now());
        assertThat(user).isEqualTo(existingUser());
        assertReloadedUser(vo, persisted, wrapper);
    }

    // ---------- AuditUserRequest validation ----------

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void auditUserRequest_whenValidStatus_passesValidation(int auditStatus) {
        AuditUserRequest request = new AuditUserRequest();
        request.setAuditStatus(auditStatus);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 3})
    void auditUserRequest_whenInvalidStatus_failsValidation(Integer auditStatus) {
        AuditUserRequest request = new AuditUserRequest();
        request.setAuditStatus(auditStatus);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("auditStatus");
        }
    }

    // ---------- update ----------

    @Test
    void update_whenDisabling_revokesAllSessions() {
        User persisted = existingUser();
        persisted.setStatus(0);
        when(userMapper.selectById(1L)).thenReturn(user, persisted);
        when(userMapper.update(isNull(), any())).thenReturn(1);
        UpdateUserRequest request = new UpdateUserRequest();
        request.setStatus(0);

        UserVO vo = userService.update(1L, request);

        LambdaUpdateWrapper<User> wrapper = capturedUpdate();
        assertWhere(wrapper, null);
        assertThat(setValues(wrapper)).containsOnlyKeys("status", "update_time")
                .containsEntry("status", 0);
        assertThat(setValues(wrapper).get("update_time")).isInstanceOf(LocalDateTime.class);
        assertThat(user.getStatus()).isEqualTo(1);
        assertReloadedUser(vo, persisted, wrapper);
        verify(tokenService).revokeUserTokens(1L);
    }

    @Test
    void update_whenNotDisabling_keepsSessions() {
        user.setStatus(0);
        User persisted = existingUser();
        when(userMapper.selectById(1L)).thenReturn(user, persisted);
        when(userMapper.update(isNull(), any())).thenReturn(1);
        UpdateUserRequest request = new UpdateUserRequest();
        request.setStatus(1);

        UserVO vo = userService.update(1L, request);

        LambdaUpdateWrapper<User> wrapper = capturedUpdate();
        assertWhere(wrapper, null);
        assertThat(setValues(wrapper)).containsOnlyKeys("status", "update_time")
                .containsEntry("status", 1);
        assertThat(setValues(wrapper).get("update_time")).isInstanceOf(LocalDateTime.class);
        assertReloadedUser(vo, persisted, wrapper);
        verifyNoInteractions(tokenService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nickname", "email"})
    void update_whenSingleFieldProvided_doesNotOverwriteOtherColumns(String field) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setNickname(" ");
        request.setEmail("");
        User persisted = concurrentlyChangedUser();
        String value;
        if (field.equals("nickname")) {
            value = "新昵称";
            request.setNickname(value);
            persisted.setNickname(value);
        } else {
            value = "new@example.com";
            request.setEmail(value);
            persisted.setEmail(value);
        }
        when(userMapper.selectById(1L)).thenReturn(user, persisted);
        when(userMapper.update(isNull(), any())).thenReturn(1);
        LocalDateTime before = LocalDateTime.now();

        UserVO vo = userService.update(1L, request);

        LambdaUpdateWrapper<User> wrapper = capturedUpdate();
        assertWhere(wrapper, null);
        assertThat(setValues(wrapper)).containsOnlyKeys(field, "update_time").containsEntry(field, value);
        assertThat((LocalDateTime) setValues(wrapper).get("update_time"))
                .isBetween(before, LocalDateTime.now());
        assertThat(user).isEqualTo(existingUser());
        assertReloadedUser(vo, persisted, wrapper);
        verifyNoInteractions(passwordUtil, tokenService);
    }

    @Test
    void update_whenAllFieldsProvided_setsOnlyRequestedColumns() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setNickname("新昵称");
        request.setEmail("new@example.com");
        request.setStatus(1);
        User persisted = existingUser();
        persisted.setNickname(request.getNickname());
        persisted.setEmail(request.getEmail());
        when(userMapper.selectById(1L)).thenReturn(user, persisted);
        when(userMapper.update(isNull(), any())).thenReturn(1);

        UserVO vo = userService.update(1L, request);

        LambdaUpdateWrapper<User> wrapper = capturedUpdate();
        assertWhere(wrapper, null);
        assertThat(setValues(wrapper)).containsOnlyKeys("nickname", "email", "status", "update_time")
                .containsEntry("nickname", request.getNickname())
                .containsEntry("email", request.getEmail())
                .containsEntry("status", 1);
        assertThat(setValues(wrapper).get("update_time")).isInstanceOf(LocalDateTime.class);
        assertReloadedUser(vo, persisted, wrapper);
        verifyNoInteractions(passwordUtil, tokenService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void update_whenNoFieldsProvided_skipsUpdateAndReload(String blank) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setNickname(blank);
        request.setEmail(blank);
        when(userMapper.selectById(1L)).thenReturn(user);

        UserVO vo = userService.update(1L, request);

        assertUserView(vo, user);
        verify(userMapper).selectById(1L);
        verifyNoMoreInteractions(userMapper);
        verifyNoInteractions(passwordUtil, tokenService);
    }

    // ---------- updateProfile ----------

    @ParameterizedTest
    @ValueSource(strings = {"nickname", "email", "avatar"})
    void updateProfile_whenSingleFieldProvided_doesNotOverwriteOtherColumns(String field) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("");
        request.setEmail(" ");
        request.setAvatar("\t");
        User persisted = concurrentlyChangedUser();
        String value;
        switch (field) {
            case "nickname" -> {
                value = "新昵称";
                request.setNickname(value);
                persisted.setNickname(value);
            }
            case "email" -> {
                value = "new@example.com";
                request.setEmail(value);
                persisted.setEmail(value);
            }
            default -> {
                value = "https://example.com/new-avatar.png";
                request.setAvatar(value);
                persisted.setAvatar(value);
            }
        }
        when(userMapper.selectById(1L)).thenReturn(user, persisted);
        when(userMapper.update(isNull(), any())).thenReturn(1);
        LocalDateTime before = LocalDateTime.now();

        UserVO vo = userService.updateProfile(1L, request);

        LambdaUpdateWrapper<User> wrapper = capturedUpdate();
        assertWhere(wrapper, null);
        assertThat(setValues(wrapper)).containsOnlyKeys(field, "update_time").containsEntry(field, value);
        assertThat((LocalDateTime) setValues(wrapper).get("update_time"))
                .isBetween(before, LocalDateTime.now());
        assertThat(user).isEqualTo(existingUser());
        assertReloadedUser(vo, persisted, wrapper);
        verifyNoInteractions(passwordUtil, tokenService);
    }

    @Test
    void updateProfile_whenAllFieldsProvided_setsOnlyProfileColumns() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("新昵称");
        request.setEmail("new@example.com");
        request.setAvatar("https://example.com/new-avatar.png");
        User persisted = concurrentlyChangedUser();
        persisted.setNickname(request.getNickname());
        persisted.setEmail(request.getEmail());
        persisted.setAvatar(request.getAvatar());
        when(userMapper.selectById(1L)).thenReturn(user, persisted);
        when(userMapper.update(isNull(), any())).thenReturn(1);

        UserVO vo = userService.updateProfile(1L, request);

        LambdaUpdateWrapper<User> wrapper = capturedUpdate();
        assertWhere(wrapper, null);
        assertThat(setValues(wrapper)).containsOnlyKeys("nickname", "email", "avatar", "update_time")
                .containsEntry("nickname", request.getNickname())
                .containsEntry("email", request.getEmail())
                .containsEntry("avatar", request.getAvatar());
        assertThat(setValues(wrapper).get("update_time")).isInstanceOf(LocalDateTime.class);
        assertReloadedUser(vo, persisted, wrapper);
        verifyNoInteractions(passwordUtil, tokenService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void updateProfile_whenNoFieldsProvided_skipsUpdateAndReload(String blank) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname(blank);
        request.setEmail(blank);
        request.setAvatar(blank);
        when(userMapper.selectById(1L)).thenReturn(user);

        UserVO vo = userService.updateProfile(1L, request);

        assertUserView(vo, user);
        verify(userMapper).selectById(1L);
        verifyNoMoreInteractions(userMapper);
        verifyNoInteractions(passwordUtil, tokenService);
    }

    @Test
    void delete_revokesAllSessions() {
        when(userMapper.selectById(1L)).thenReturn(user);
        userService.delete(1L);
        verify(userMapper).deleteById(1L);
        verify(tokenService).revokeUserTokens(1L);
    }

    // ---------- helpers ----------

    private User existingUser() {
        User existing = new User();
        existing.setId(1L);
        existing.setPassword("hashed-old");
        existing.setNickname("旧昵称");
        existing.setEmail("old@example.com");
        existing.setAvatar("https://example.com/old-avatar.png");
        existing.setStatus(1);
        existing.setAuditStatus(0);
        existing.setAuditRemark("旧审核备注");
        existing.setAuditTime(OLD_TIME);
        existing.setUpdateTime(OLD_TIME);
        return existing;
    }

    private User concurrentlyChangedUser() {
        User persisted = existingUser();
        persisted.setStatus(0);
        persisted.setPassword("concurrently-changed-password");
        persisted.setAuditStatus(2);
        persisted.setAuditRemark("最新审核备注");
        persisted.setAuditTime(OLD_TIME.plusDays(1));
        persisted.setUpdateTime(OLD_TIME.plusDays(1));
        return persisted;
    }

    private LambdaUpdateWrapper<User> capturedUpdate() {
        verify(userMapper).update(isNull(), updateCaptor.capture());
        verify(userMapper, never()).updateById(any(User.class));
        LambdaUpdateWrapper<User> wrapper = updateCaptor.getValue();
        assertThat(wrapper.getEntity()).isNull();
        return wrapper;
    }

    private void assertWhere(LambdaUpdateWrapper<User> wrapper, Integer auditStatus) {
        String sql = wrapper.getSqlSegment();
        assertThat(sql).startsWith("(").endsWith(")");
        Map<String, Object> conditions = boundValues(wrapper, sql.substring(1, sql.length() - 1).split(" AND "));
        if (auditStatus == null) {
            assertThat(conditions).containsExactlyInAnyOrderEntriesOf(Map.of("id", 1L));
        } else {
            assertThat(conditions).containsExactlyInAnyOrderEntriesOf(Map.of("id", 1L, "audit_status", auditStatus));
        }
    }

    private Map<String, Object> setValues(LambdaUpdateWrapper<User> wrapper) {
        return boundValues(wrapper, wrapper.getSqlSet().split(","));
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

    private void assertAuditAssignments(LambdaUpdateWrapper<User> wrapper, int auditStatus, String remark) {
        Map<String, Object> values = setValues(wrapper);
        assertThat(values).containsOnlyKeys("audit_status", "audit_remark", "audit_time", "update_time")
                .containsEntry("audit_status", auditStatus)
                .containsEntry("audit_remark", remark);
        assertThat(values.get("audit_time")).isInstanceOf(LocalDateTime.class);
        assertThat(values.get("update_time")).isEqualTo(values.get("audit_time"));
    }

    private void assertReloadedUser(UserVO vo, User persisted, LambdaUpdateWrapper<User> wrapper) {
        assertUserView(vo, persisted);
        InOrder order = inOrder(userMapper);
        order.verify(userMapper).selectById(1L);
        order.verify(userMapper).update(isNull(), same(wrapper));
        order.verify(userMapper).selectById(1L);
        order.verifyNoMoreInteractions();
    }

    private void assertUserView(UserVO vo, User expected) {
        assertThat(vo).usingRecursiveComparison()
                .comparingOnlyFields("id", "nickname", "email", "avatar", "status",
                        "auditStatus", "auditRemark", "auditTime", "updateTime")
                .isEqualTo(expected);
    }

    private ChangePasswordRequest changePasswordRequest() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("oldpassword1");
        req.setNewPassword("newpassword1");
        return req;
    }
}
