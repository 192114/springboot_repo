package com.shadow.backend.admin.adminuser.service.impl;

import com.shadow.backend.admin.adminuser.dto.AssignRolesRequest;
import com.shadow.backend.admin.adminuser.dto.UpdateAdminUserRequest;
import com.shadow.backend.admin.adminuser.entity.SysUserRole;
import com.shadow.backend.admin.adminuser.mapper.SysUserRoleMapper;
import com.shadow.backend.admin.adminuser.vo.AdminUserManageVO;
import com.shadow.backend.admin.auth.entity.AdminUser;
import com.shadow.backend.admin.auth.mapper.AdminUserMapper;
import com.shadow.backend.admin.auth.response.AdminResultCode;
import com.shadow.backend.admin.role.entity.SysRole;
import com.shadow.backend.admin.role.mapper.SysRoleMapper;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.common.response.PageResult;
import com.shadow.backend.admin.auth.util.LoginAdminUtil;
import com.shadow.backend.common.util.PasswordUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserManageServiceImplTest {

    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private PasswordUtil passwordUtil;

    @InjectMocks
    private AdminUserManageServiceImpl adminUserManageService;

    private AdminUser targetUser;

    @BeforeEach
    void setUp() {
        // 纯 Mockito 环境无 MyBatis 启动流程，手动初始化实体元数据以支持 Lambda 列解析
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AdminUser.class);
        TableInfoHelper.initTableInfo(assistant, SysRole.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
        targetUser = new AdminUser();
        targetUser.setId(2L);
        targetUser.setUsername("editor");
        targetUser.setStatus(1);
    }

    // ---------- page: 过滤分支 ----------

    @Test
    void page_whenRoleHasNoUsers_returnsEmptyPageWithoutUserQuery() {
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of());

        PageResult<AdminUserManageVO> result = adminUserManageService.page(1, 10, null, 5L, null);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verify(adminUserMapper, never()).selectPage(any(), any());
    }

    @Test
    void page_whenRoleFilterApplies_queriesUsersByRoleUserIds() {
        SysUserRole link = new SysUserRole();
        link.setUserId(2L);
        link.setRoleId(5L);
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(link));

        Page<AdminUser> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of(targetUser));
        pageResult.setTotal(1);
        when(adminUserMapper.selectPage(any(), any())).thenReturn(pageResult);

        SysRole role = new SysRole();
        role.setId(5L);
        role.setName("editor");
        role.setCode("editor");
        when(sysRoleMapper.selectByIds(any())).thenReturn(List.of(role));

        PageResult<AdminUserManageVO> result = adminUserManageService.page(1, 10, null, 5L, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords().get(0).getRoles()).hasSize(1);

        ArgumentCaptor<LambdaQueryWrapper<AdminUser>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(adminUserMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("IN");
    }

    @Test
    void page_whenStatusFilterApplies_addsStatusCondition() {
        Page<AdminUser> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of(targetUser));
        pageResult.setTotal(1);
        when(adminUserMapper.selectPage(any(), any())).thenReturn(pageResult);

        PageResult<AdminUserManageVO> result = adminUserManageService.page(1, 10, null, null, 0);

        assertThat(result.getRecords()).hasSize(1);
        ArgumentCaptor<LambdaQueryWrapper<AdminUser>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(adminUserMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("status");
    }

    // ---------- update: 不能禁用自己 ----------

    @Test
    void update_whenDisablingSelf_throws() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        UpdateAdminUserRequest req = new UpdateAdminUserRequest();
        req.setStatus(0);

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(2L);

            assertThatThrownBy(() -> adminUserManageService.update(2L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(AdminResultCode.CANNOT_DISABLE_SELF.getCode());
        }

        verify(adminUserMapper, never()).update(org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void update_whenDisablingSomeoneElse_succeeds() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        UpdateAdminUserRequest req = new UpdateAdminUserRequest();
        req.setStatus(0);

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(1L);

            adminUserManageService.update(2L, req);
        }

        verify(adminUserMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void update_whenOnlyNicknameProvided_doesNotOverwriteAccountState() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        UpdateAdminUserRequest request = new UpdateAdminUserRequest();
        request.setNickname("新昵称");
        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(1L);
            adminUserManageService.update(2L, request);
            loginAdminUtil.verify(() -> LoginAdminUtil.logoutAdmin(2L), never());
        }
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AdminUser>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(adminUserMapper).update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("nickname", "update_time")
                .doesNotContain("status", "password", "username", "email");
    }

    @Test
    void update_whenUserNotFound_throws() {
        when(adminUserMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> adminUserManageService.update(99L, new UpdateAdminUserRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.ADMIN_NOT_FOUND.getCode());
    }

    // ---------- delete: 不能删除自己 ----------

    @Test
    void delete_whenDeletingSelf_throws() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(2L);

            assertThatThrownBy(() -> adminUserManageService.delete(2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(AdminResultCode.CANNOT_DELETE_SELF.getCode());
        }

        verify(adminUserMapper, never()).deleteById(any(Long.class));
        verify(sysUserRoleMapper, never()).delete(any());
    }

    @Test
    void delete_whenDeletingSomeoneElse_removesRoleLinksThenUser() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(1L);

            adminUserManageService.delete(2L);
        }

        verify(sysUserRoleMapper).delete(any());
        verify(adminUserMapper).deleteById(2L);
    }

    // ---------- assignRoles ----------

    @Test
    void assignRoles_whenUserNotFound_throws() {
        when(adminUserMapper.selectById(2L)).thenReturn(null);
        AssignRolesRequest req = new AssignRolesRequest();
        req.setRoleIds(List.of(1L));

        assertThatThrownBy(() -> adminUserManageService.assignRoles(2L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.ADMIN_NOT_FOUND.getCode());
    }

    @Test
    void assignRoles_whenRoleNotExists_throws() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        when(sysRoleMapper.selectByIds(List.of(10L))).thenReturn(List.of());
        AssignRolesRequest req = new AssignRolesRequest();
        req.setRoleIds(List.of(10L));

        assertThatThrownBy(() -> adminUserManageService.assignRoles(2L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.ROLE_ASSIGNMENT_INVALID.getCode());

        verify(sysUserRoleMapper, never()).delete(any());
    }

    @Test
    void assignRoles_whenRoleDisabled_throws() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        when(sysRoleMapper.selectByIds(List.of(10L))).thenReturn(List.of(buildRole(10L, "editor", 0)));
        AssignRolesRequest req = new AssignRolesRequest();
        req.setRoleIds(List.of(10L));

        assertThatThrownBy(() -> adminUserManageService.assignRoles(2L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.ROLE_ASSIGNMENT_INVALID.getCode());

        verify(sysUserRoleMapper, never()).delete(any());
    }

    @Test
    void assignRoles_whenRoleBeyondOperatorScope_throws() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        when(sysRoleMapper.selectByIds(List.of(10L))).thenReturn(List.of(buildRole(10L, "editor", 1)));
        when(sysRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("viewer"));
        AssignRolesRequest req = new AssignRolesRequest();
        req.setRoleIds(List.of(10L));

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(1L);

            assertThatThrownBy(() -> adminUserManageService.assignRoles(2L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(AdminResultCode.ROLE_ASSIGNMENT_FORBIDDEN.getCode());
        }

        verify(sysUserRoleMapper, never()).delete(any());
    }

    @Test
    void assignRoles_whenOperatorIsSuperAdmin_bypassesSubsetCheck() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        when(sysRoleMapper.selectByIds(List.of(10L))).thenReturn(List.of(buildRole(10L, "editor", 1)));
        when(sysRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("support"));
        AssignRolesRequest req = new AssignRolesRequest();
        req.setRoleIds(List.of(10L));

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(1L);

            adminUserManageService.assignRoles(2L, req);
        }

        verify(sysUserRoleMapper).delete(any());
        verify(sysUserRoleMapper, times(1)).insert(any(SysUserRole.class));
    }

    @Test
    void assignRoles_whenModifyingOwnRoles_throws() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        when(sysRoleMapper.selectByIds(List.of(10L))).thenReturn(List.of(buildRole(10L, "editor", 1)));
        when(sysRoleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("support"));
        AssignRolesRequest req = new AssignRolesRequest();
        req.setRoleIds(List.of(10L));

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(2L);

            assertThatThrownBy(() -> adminUserManageService.assignRoles(2L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(AdminResultCode.CANNOT_MODIFY_OWN_ROLES.getCode());
        }

        verify(sysUserRoleMapper, never()).delete(any());
    }

    @Test
    void assignRoles_whenRemovingSupportFromLastSuperAdmin_throws() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        when(sysRoleMapper.selectByIds(List.of(10L))).thenReturn(List.of(buildRole(10L, "editor", 1)));
        when(sysRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("support"));
        when(sysRoleMapper.selectOne(any())).thenReturn(buildRole(5L, "support", 1));
        when(sysUserRoleMapper.selectCount(any())).thenReturn(1L);
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(userRoleLink(2L, 5L)));
        AssignRolesRequest req = new AssignRolesRequest();
        req.setRoleIds(List.of(10L));

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(1L);

            assertThatThrownBy(() -> adminUserManageService.assignRoles(2L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(AdminResultCode.LAST_SUPER_ADMIN.getCode());
        }

        verify(sysUserRoleMapper, never()).delete(any());
    }

    @Test
    void assignRoles_replacesOldLinksWithNewOnes() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        when(sysRoleMapper.selectByIds(List.of(10L, 20L))).thenReturn(
                List.of(buildRole(10L, "editor", 1), buildRole(20L, "viewer", 1)));
        when(sysRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("support"));
        AssignRolesRequest req = new AssignRolesRequest();
        req.setRoleIds(List.of(10L, 20L));

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(1L);

            adminUserManageService.assignRoles(2L, req);
        }

        verify(sysUserRoleMapper).delete(any());
        verify(sysUserRoleMapper, times(2)).insert(any(SysUserRole.class));
    }

    @Test
    void assignRoles_whenEmptyRoleIds_onlyDeletesOldLinks() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        when(sysRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("support"));
        AssignRolesRequest req = new AssignRolesRequest();
        req.setRoleIds(List.of());

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(1L);

            adminUserManageService.assignRoles(2L, req);
        }

        verify(sysUserRoleMapper).delete(any());
        verify(sysUserRoleMapper, never()).insert(any(SysUserRole.class));
    }

    // ---------- update/delete: 最后超管保护与会话撤销 ----------

    @Test
    void update_whenDisablingLastEnabledSuperAdmin_throws() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        when(sysRoleMapper.selectOne(any())).thenReturn(buildRole(5L, "support", 1));
        when(sysUserRoleMapper.selectCount(any())).thenReturn(1L);
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(userRoleLink(2L, 5L)));
        UpdateAdminUserRequest req = new UpdateAdminUserRequest();
        req.setStatus(0);

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(1L);

            assertThatThrownBy(() -> adminUserManageService.update(2L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(AdminResultCode.LAST_SUPER_ADMIN.getCode());
        }

        verify(adminUserMapper, never()).update(org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void update_whenDisabling_revokesTargetSessions() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        UpdateAdminUserRequest req = new UpdateAdminUserRequest();
        req.setStatus(0);

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(1L);

            adminUserManageService.update(2L, req);

            loginAdminUtil.verify(() -> LoginAdminUtil.logoutAdmin(2L));
        }

        verify(adminUserMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void delete_whenDeletingLastEnabledSuperAdmin_throws() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        when(sysRoleMapper.selectOne(any())).thenReturn(buildRole(5L, "support", 1));
        when(sysUserRoleMapper.selectCount(any())).thenReturn(1L);
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(userRoleLink(2L, 5L)));

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(1L);

            assertThatThrownBy(() -> adminUserManageService.delete(2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(AdminResultCode.LAST_SUPER_ADMIN.getCode());
        }

        verify(adminUserMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void delete_whenSucceeds_revokesTargetSessions() {
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(1L);

            adminUserManageService.delete(2L);

            loginAdminUtil.verify(() -> LoginAdminUtil.logoutAdmin(2L));
        }

        verify(adminUserMapper).deleteById(2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"disable", "delete", "removeRole"})
    void superAdminChanges_lockBeforeReadingAccountSnapshot(String operation) {
        when(sysRoleMapper.selectOne(any())).thenReturn(buildRole(5L, "support", 1));
        when(adminUserMapper.selectById(2L)).thenReturn(targetUser);
        when(sysUserRoleMapper.selectCount(any())).thenReturn(1L);
        when(sysUserRoleMapper.selectList(any())).thenReturn(
                List.of(userRoleLink(2L, 5L), userRoleLink(3L, 5L)));
        when(adminUserMapper.selectCount(any())).thenReturn(1L);

        try (MockedStatic<LoginAdminUtil> loginAdminUtil = Mockito.mockStatic(LoginAdminUtil.class)) {
            loginAdminUtil.when(LoginAdminUtil::currentAdminId).thenReturn(1L);
            switch (operation) {
                case "disable" -> {
                    UpdateAdminUserRequest request = new UpdateAdminUserRequest();
                    request.setStatus(0);
                    adminUserManageService.update(2L, request);
                }
                case "delete" -> adminUserManageService.delete(2L);
                case "removeRole" -> {
                    when(sysRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("support"));
                    AssignRolesRequest request = new AssignRolesRequest();
                    request.setRoleIds(List.of());
                    adminUserManageService.assignRoles(2L, request);
                }
                default -> throw new AssertionError(operation);
            }
        }

        ArgumentCaptor<LambdaQueryWrapper<SysRole>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        var order = Mockito.inOrder(sysRoleMapper, adminUserMapper, sysUserRoleMapper);
        order.verify(sysRoleMapper).selectOne(captor.capture());
        order.verify(adminUserMapper).selectById(2L);
        order.verify(sysUserRoleMapper).selectCount(any());
        order.verify(sysUserRoleMapper).selectList(any());
        order.verify(adminUserMapper).selectCount(any());
        assertThat(captor.getValue().getSqlSegment()).contains("code", "FOR UPDATE");
        assertThat(captor.getValue().getParamNameValuePairs()).containsValue("support");
    }

    // ---------- helpers ----------

    private SysRole buildRole(Long id, String code, int status) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode(code);
        role.setName(code);
        role.setStatus(status);
        return role;
    }

    private SysUserRole userRoleLink(Long userId, Long roleId) {
        SysUserRole link = new SysUserRole();
        link.setUserId(userId);
        link.setRoleId(roleId);
        return link;
    }
}
