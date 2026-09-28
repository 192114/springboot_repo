package com.shadow.backend.admin.rbac;

import com.shadow.backend.admin.menu.mapper.SysMenuMapper;
import com.shadow.backend.admin.role.mapper.SysRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StpInterfaceImplTest {

    @Mock
    private SysMenuMapper sysMenuMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;

    @InjectMocks
    private StpInterfaceImpl stpInterface;

    @Test
    void getPermissionList_whenLoginTypeIsAdmin_returnsPermissionsFromMapper() {
        when(sysMenuMapper.selectPermissionsByUserId(1L)).thenReturn(List.of("user:list", "user:create"));

        List<String> permissions = stpInterface.getPermissionList(1L, "admin");

        assertThat(permissions).containsExactly("user:list", "user:create");
    }

    @Test
    void getPermissionList_whenLoginTypeIsNotAdmin_returnsEmptyWithoutQuerying() {
        List<String> permissions = stpInterface.getPermissionList(1L, "app");

        assertThat(permissions).isEmpty();
        verify(sysMenuMapper, never()).selectPermissionsByUserId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void getPermissionList_whenMapperReturnsNull_returnsEmptyList() {
        when(sysMenuMapper.selectPermissionsByUserId(1L)).thenReturn(null);

        List<String> permissions = stpInterface.getPermissionList(1L, "admin");

        assertThat(permissions).isEmpty();
    }

    @Test
    void getRoleList_whenLoginTypeIsAdmin_returnsRoleCodesFromMapper() {
        when(sysRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("super_admin"));

        List<String> roles = stpInterface.getRoleList(1L, "admin");

        assertThat(roles).containsExactly("super_admin");
    }

    @Test
    void getRoleList_whenLoginTypeIsNotAdmin_returnsEmptyWithoutQuerying() {
        List<String> roles = stpInterface.getRoleList(1L, "app");

        assertThat(roles).isEmpty();
        verify(sysRoleMapper, never()).selectRoleCodesByUserId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void getPermissionList_whenLoginIdIsNull_returnsEmpty() {
        List<String> permissions = stpInterface.getPermissionList(null, "admin");

        assertThat(permissions).isEmpty();
    }
}
