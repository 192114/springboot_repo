package com.shadow.backend.admin.role.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shadow.backend.admin.auth.response.AdminResultCode;
import com.shadow.backend.admin.menu.entity.SysMenu;
import com.shadow.backend.admin.menu.mapper.SysMenuMapper;
import com.shadow.backend.admin.role.dto.AssignMenusRequest;
import com.shadow.backend.admin.role.entity.SysRole;
import com.shadow.backend.admin.role.entity.SysRoleMenu;
import com.shadow.backend.admin.role.mapper.SysRoleMapper;
import com.shadow.backend.admin.role.mapper.SysRoleMenuMapper;
import com.shadow.backend.admin.role.vo.RoleVO;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.common.response.PageResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
class RoleServiceImplTest {

    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysRoleMenuMapper sysRoleMenuMapper;
    @Mock
    private SysMenuMapper sysMenuMapper;

    @InjectMocks
    private RoleServiceImpl roleService;

    private SysRole role;

    @BeforeEach
    void setUp() {
        // 纯 Mockito 环境无 MyBatis 启动流程，手动初始化实体元数据以支持 Lambda 列解析
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysRole.class);
        role = new SysRole();
        role.setId(5L);
        role.setName("editor");
    }

    // ---------- page: 过滤分支 ----------

    @Test
    void page_whenStatusFilterApplies_addsStatusCondition() {
        Page<SysRole> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of(role));
        pageResult.setTotal(1);
        when(sysRoleMapper.selectPage(any(), any())).thenReturn(pageResult);

        PageResult<RoleVO> result = roleService.page(1, 10, null, 0);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords().get(0).getName()).isEqualTo("editor");

        ArgumentCaptor<LambdaQueryWrapper<SysRole>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sysRoleMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("status");
    }

    @Test
    void page_withoutFilters_mapsRecordsWithoutStatusCondition() {
        Page<SysRole> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of(role));
        pageResult.setTotal(1);
        when(sysRoleMapper.selectPage(any(), any())).thenReturn(pageResult);

        PageResult<RoleVO> result = roleService.page(1, 10, null, null);

        assertThat(result.getRecords()).hasSize(1);
        ArgumentCaptor<LambdaQueryWrapper<SysRole>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sysRoleMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).doesNotContain("status");
    }

    // ---------- delete: 角色被占用时禁止删除 ----------

    @Test
    void delete_whenRoleNotFound_throws() {
        when(sysRoleMapper.selectById(5L)).thenReturn(null);

        assertThatThrownBy(() -> roleService.delete(5L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.ROLE_NOT_FOUND.getCode());
    }

    @Test
    void delete_whenRoleInUse_throwsWithoutDeleting() {
        when(sysRoleMapper.selectById(5L)).thenReturn(role);
        when(sysRoleMapper.countUsersByRoleId(5L)).thenReturn(2L);

        assertThatThrownBy(() -> roleService.delete(5L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.ROLE_IN_USE.getCode());

        verify(sysRoleMenuMapper, never()).delete(any());
        verify(sysRoleMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void delete_whenRoleNotInUse_removesMenuLinksThenRole() {
        when(sysRoleMapper.selectById(5L)).thenReturn(role);
        when(sysRoleMapper.countUsersByRoleId(5L)).thenReturn(0L);

        roleService.delete(5L);

        verify(sysRoleMenuMapper).delete(any());
        verify(sysRoleMapper).deleteById(5L);
    }

    // ---------- assignMenus ----------

    @Test
    void assignMenus_whenRoleNotFound_throws() {
        when(sysRoleMapper.selectById(5L)).thenReturn(null);
        AssignMenusRequest req = new AssignMenusRequest();
        req.setMenuIds(List.of(1L));

        assertThatThrownBy(() -> roleService.assignMenus(5L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.ROLE_NOT_FOUND.getCode());
    }

    @Test
    void assignMenus_replacesOldLinksWithNewOnes() {
        when(sysRoleMapper.selectById(5L)).thenReturn(role);
        AssignMenusRequest req = new AssignMenusRequest();
        req.setMenuIds(List.of(100L, 200L, 300L, 100L));
        when(sysMenuMapper.selectByIds(List.of(100L, 200L, 300L)))
                .thenReturn(List.of(new SysMenu(), new SysMenu(), new SysMenu()));

        roleService.assignMenus(5L, req);

        verify(sysRoleMenuMapper).delete(any());
        verify(sysRoleMenuMapper, times(3)).insert(any(SysRoleMenu.class));
    }

    @Test
    void assignMenus_whenMenuMissing_preservesExistingLinks() {
        when(sysRoleMapper.selectById(5L)).thenReturn(role);
        AssignMenusRequest request = new AssignMenusRequest();
        request.setMenuIds(List.of(99L));
        when(sysMenuMapper.selectByIds(List.of(99L))).thenReturn(List.of());
        assertThatThrownBy(() -> roleService.assignMenus(5L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.MENU_ASSIGNMENT_INVALID.getCode());
        verify(sysRoleMenuMapper, never()).delete(any());
    }

    @Test
    void assignMenus_whenEmptyMenuIds_onlyDeletesOldLinks() {
        when(sysRoleMapper.selectById(5L)).thenReturn(role);
        AssignMenusRequest req = new AssignMenusRequest();
        req.setMenuIds(List.of());

        roleService.assignMenus(5L, req);

        verify(sysRoleMenuMapper).delete(any());
        verify(sysRoleMenuMapper, never()).insert(any(SysRoleMenu.class));
    }
}
