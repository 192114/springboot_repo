package com.shadow.backend.admin.auth.config;

import com.shadow.backend.admin.auth.entity.AdminUser;
import com.shadow.backend.admin.auth.mapper.AdminUserMapper;
import com.shadow.backend.admin.config.AdminSecurityProperties;
import com.shadow.backend.common.util.PasswordUtil;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserInitializerTest {

    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private PasswordUtil passwordUtil;

    private AdminSecurityProperties securityProperties;

    private AdminUserInitializer initializer;

    @BeforeEach
    void setUp() {
        securityProperties = new AdminSecurityProperties();
        securityProperties.setInitialAdminPassword("prod-secret-pwd");
        initializer = new AdminUserInitializer(adminUserMapper, passwordUtil, securityProperties);
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "0, 0", "1, 1", "1, 0"})
    void run_whenAdminExistsIncludingDeletedOrDisabled_skipsCreation(int deleted, int status) {
        AdminUser existingAdmin = new AdminUser();
        existingAdmin.setUsername("admin");
        existingAdmin.setDeleted(deleted);
        existingAdmin.setStatus(status);
        existingAdmin.setPassword("existing-hash");
        when(adminUserMapper.existsByUsernameIncludingDeleted("admin"))
                .thenAnswer(invocation -> existingAdmin.getUsername().equals(invocation.getArgument(0)));

        initializer.run();

        verify(adminUserMapper).existsByUsernameIncludingDeleted("admin");
        verifyNoMoreInteractions(adminUserMapper);
        verifyNoInteractions(passwordUtil);
        assertThat(existingAdmin.getDeleted()).isEqualTo(deleted);
        assertThat(existingAdmin.getStatus()).isEqualTo(status);
        assertThat(existingAdmin.getPassword()).isEqualTo("existing-hash");
    }

    @Test
    void run_whenAdminMissing_createsWithConfiguredPassword() {
        when(adminUserMapper.existsByUsernameIncludingDeleted("admin")).thenReturn(false);
        when(passwordUtil.hash("prod-secret-pwd")).thenReturn("hashed-pwd");

        initializer.run();

        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserMapper).insert(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("admin");
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed-pwd");
        assertThat(captor.getValue().getNickname()).isEqualTo("超级管理员");
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@shadow.com");
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        verify(adminUserMapper).existsByUsernameIncludingDeleted("admin");
        verifyNoMoreInteractions(adminUserMapper);
    }

    @Test
    void run_whenPasswordUnset_usesDevDefault() {
        AdminSecurityProperties devDefaults = new AdminSecurityProperties();
        AdminUserInitializer devInitializer =
                new AdminUserInitializer(adminUserMapper, passwordUtil, devDefaults);
        when(adminUserMapper.existsByUsernameIncludingDeleted("admin")).thenReturn(false);
        when(passwordUtil.hash("admin123")).thenReturn("dev-hash");

        devInitializer.run();

        verify(passwordUtil).hash("admin123");
    }

    @Test
    void usernameExistenceQuery_includesDeletedAndDisabledAccountsAndBindsUsername() throws Exception {
        Method method = AdminUserMapper.class.getMethod("existsByUsernameIncludingDeleted", String.class);

        assertThat(method.getAnnotation(Select.class).value())
                .containsExactly("SELECT EXISTS(SELECT 1 FROM sys_user WHERE username = #{username})");
        assertThat(method.getParameters()[0].getAnnotation(Param.class).value()).isEqualTo("username");
    }

    @Test
    void run_whenConcurrentInsertCreatesAdmin_toleratesDuplicateKey() {
        when(adminUserMapper.existsByUsernameIncludingDeleted("admin")).thenReturn(false, true);
        when(passwordUtil.hash("prod-secret-pwd")).thenReturn("hashed-pwd");
        when(adminUserMapper.insert(any(AdminUser.class)))
                .thenThrow(new DuplicateKeyException("duplicate username"));

        assertThatCode(() -> initializer.run()).doesNotThrowAnyException();

        verifyInsertThenRecheck();
    }

    @Test
    void run_whenDuplicateKeyButAdminStillMissing_rethrowsOriginalException() {
        DuplicateKeyException exception = new DuplicateKeyException("another unique key conflict");
        when(adminUserMapper.existsByUsernameIncludingDeleted("admin")).thenReturn(false, false);
        when(passwordUtil.hash("prod-secret-pwd")).thenReturn("hashed-pwd");
        when(adminUserMapper.insert(any(AdminUser.class))).thenThrow(exception);

        assertThatThrownBy(() -> initializer.run()).isSameAs(exception);

        verifyInsertThenRecheck();
    }

    @Test
    void run_whenExistenceQueryFails_propagatesDatabaseError() {
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("database unavailable");
        when(adminUserMapper.existsByUsernameIncludingDeleted("admin")).thenThrow(exception);

        assertThatThrownBy(() -> initializer.run()).isSameAs(exception);

        verify(adminUserMapper).existsByUsernameIncludingDeleted("admin");
        verifyNoMoreInteractions(adminUserMapper);
        verifyNoInteractions(passwordUtil);
    }

    @Test
    void run_whenInsertFailsWithNonDuplicateError_propagatesWithoutRechecking() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("invalid data");
        when(adminUserMapper.existsByUsernameIncludingDeleted("admin")).thenReturn(false);
        when(passwordUtil.hash("prod-secret-pwd")).thenReturn("hashed-pwd");
        when(adminUserMapper.insert(any(AdminUser.class))).thenThrow(exception);

        assertThatThrownBy(() -> initializer.run()).isSameAs(exception);

        verify(adminUserMapper).existsByUsernameIncludingDeleted("admin");
        verify(adminUserMapper).insert(any(AdminUser.class));
        verifyNoMoreInteractions(adminUserMapper);
    }

    @Test
    void run_whenDuplicateKeyRecheckFails_propagatesDatabaseError() {
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("database unavailable");
        when(adminUserMapper.existsByUsernameIncludingDeleted("admin"))
                .thenReturn(false).thenThrow(exception);
        when(passwordUtil.hash("prod-secret-pwd")).thenReturn("hashed-pwd");
        when(adminUserMapper.insert(any(AdminUser.class)))
                .thenThrow(new DuplicateKeyException("duplicate username"));

        assertThatThrownBy(() -> initializer.run()).isSameAs(exception);

        verifyInsertThenRecheck();
    }

    private void verifyInsertThenRecheck() {
        InOrder order = inOrder(adminUserMapper);
        order.verify(adminUserMapper).existsByUsernameIncludingDeleted("admin");
        order.verify(adminUserMapper).insert(any(AdminUser.class));
        order.verify(adminUserMapper).existsByUsernameIncludingDeleted("admin");
        verifyNoMoreInteractions(adminUserMapper);
    }
}
