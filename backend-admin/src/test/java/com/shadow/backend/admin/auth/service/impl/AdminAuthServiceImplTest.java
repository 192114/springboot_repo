package com.shadow.backend.admin.auth.service.impl;

import com.shadow.backend.admin.auth.dto.AdminLoginRequest;
import com.shadow.backend.admin.auth.dto.AdminLoginResponse;
import com.shadow.backend.admin.auth.entity.AdminUser;
import com.shadow.backend.admin.auth.mapper.AdminUserMapper;
import com.shadow.backend.admin.auth.response.AdminResultCode;
import com.shadow.backend.admin.auth.vo.AdminUserVO;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.admin.auth.util.LoginAdminUtil;
import com.shadow.backend.common.util.LoginAttemptGuard;
import com.shadow.backend.common.util.PasswordUtil;
import com.shadow.backend.admin.auth.util.StpAdminUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceImplTest {

    private static final String LOGIN_ATTEMPT_SCENE = "admin-password";
    private static final String USERNAME = "admin";

    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private PasswordUtil passwordUtil;
    @Mock
    private LoginAttemptGuard loginAttemptGuard;

    @InjectMocks
    private AdminAuthServiceImpl adminAuthService;

    private AdminUser activeAdmin;

    @BeforeEach
    void setUp() {
        activeAdmin = new AdminUser();
        activeAdmin.setId(1L);
        activeAdmin.setUsername(USERNAME);
        activeAdmin.setPassword("hashed");
        activeAdmin.setStatus(1);
    }

    @Test
    void login_whenLocked_throwsLoginLocked() {
        AdminLoginRequest req = loginRequest();
        when(loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, USERNAME)).thenReturn(true);

        assertThatThrownBy(() -> adminAuthService.login(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.LOGIN_LOCKED.getCode());

        verify(adminUserMapper, Mockito.never()).selectOne(any());
    }

    @Test
    void login_whenAdminNotFound_recordsFailureAndThrows() {
        AdminLoginRequest req = loginRequest();
        when(loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, USERNAME)).thenReturn(false);
        when(adminUserMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> adminAuthService.login(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.LOGIN_FAILED.getCode());

        verify(loginAttemptGuard).onLoginFailed(LOGIN_ATTEMPT_SCENE, USERNAME);
    }

    @Test
    void login_whenPasswordWrong_recordsFailureAndThrows() {
        AdminLoginRequest req = loginRequest();
        when(loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, USERNAME)).thenReturn(false);
        when(adminUserMapper.selectOne(any())).thenReturn(activeAdmin);
        when(passwordUtil.verify(req.getPassword(), activeAdmin.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> adminAuthService.login(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.LOGIN_FAILED.getCode());

        verify(loginAttemptGuard).onLoginFailed(LOGIN_ATTEMPT_SCENE, USERNAME);
    }

    @Test
    void login_whenAdminDisabled_throwsBeforeIssuingToken() {
        activeAdmin.setStatus(0);
        AdminLoginRequest req = loginRequest();
        when(loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, USERNAME)).thenReturn(false);
        when(adminUserMapper.selectOne(any())).thenReturn(activeAdmin);
        when(passwordUtil.verify(req.getPassword(), activeAdmin.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> adminAuthService.login(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.ADMIN_DISABLED.getCode());

        verify(loginAttemptGuard).onLoginSucceeded(LOGIN_ATTEMPT_SCENE, USERNAME);
    }

    @Test
    void login_whenSuccess_clearsAttemptsAndReturnsToken() {
        AdminLoginRequest req = loginRequest();
        when(loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, USERNAME)).thenReturn(false);
        when(adminUserMapper.selectOne(any())).thenReturn(activeAdmin);
        when(passwordUtil.verify(req.getPassword(), activeAdmin.getPassword())).thenReturn(true);

        try (MockedStatic<StpAdminUtil> stpAdminUtil = Mockito.mockStatic(StpAdminUtil.class)) {
            stpAdminUtil.when(() -> StpAdminUtil.getTokenValue()).thenReturn("admin-token");

            AdminLoginResponse response = adminAuthService.login(req);

            stpAdminUtil.verify(() -> StpAdminUtil.login(activeAdmin.getId()));
            assertThat(response.getToken()).isEqualTo("admin-token");
            assertThat(response.getUser().getUsername()).isEqualTo(USERNAME);
        }

        verify(loginAttemptGuard).onLoginSucceeded(LOGIN_ATTEMPT_SCENE, USERNAME);
        verify(loginAttemptGuard, Mockito.never()).onLoginFailed(anyString(), anyString());
    }

    @Test
    void getCurrentAdmin_whenAdminNotFound_throws() {
        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(99L);
            when(adminUserMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> adminAuthService.getCurrentAdmin())
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(AdminResultCode.ADMIN_NOT_FOUND.getCode());
        }
    }

    @Test
    void getCurrentAdmin_whenFound_mapsToVO() {
        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(activeAdmin.getId());
            when(adminUserMapper.selectById(activeAdmin.getId())).thenReturn(activeAdmin);

            AdminUserVO vo = adminAuthService.getCurrentAdmin();

            assertThat(vo.getId()).isEqualTo(activeAdmin.getId());
            assertThat(vo.getUsername()).isEqualTo(activeAdmin.getUsername());
            assertThat(vo.getStatus()).isEqualTo(activeAdmin.getStatus());
        }
    }

    private AdminLoginRequest loginRequest() {
        AdminLoginRequest req = new AdminLoginRequest();
        req.setUsername(USERNAME);
        req.setPassword("password1");
        return req;
    }
}
