package com.shadow.backend.admin.menu.service.impl;

import com.shadow.backend.admin.auth.response.AdminResultCode;
import com.shadow.backend.admin.menu.dto.CreateMenuRequest;
import com.shadow.backend.admin.menu.dto.UpdateMenuRequest;
import com.shadow.backend.admin.menu.entity.SysMenu;
import com.shadow.backend.admin.menu.mapper.SysMenuMapper;
import com.shadow.backend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuServiceImplTest {

    @Mock
    private SysMenuMapper sysMenuMapper;

    @InjectMocks
    private MenuServiceImpl menuService;

    private SysMenu directory;
    private SysMenu menu;

    @BeforeEach
    void setUp() {
        directory = new SysMenu();
        directory.setId(1L);
        directory.setParentId(0L);
        directory.setName("系统管理");
        directory.setType(1);

        menu = new SysMenu();
        menu.setId(2L);
        menu.setParentId(1L);
        menu.setName("菜单管理");
        menu.setType(2);
    }

    // ---------- create: 父级校验 ----------

    @Test
    void create_whenParentIsRoot_skipsParentLookupAndInserts() {
        menuService.create(createRequest(0L));

        verify(sysMenuMapper, never()).selectById(any(Long.class));
        verify(sysMenuMapper).insert(any(SysMenu.class));
    }

    @Test
    void create_whenParentNotFound_throwsWithoutInsert() {
        when(sysMenuMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> menuService.create(createRequest(99L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.MENU_PARENT_NOT_FOUND.getCode());

        verify(sysMenuMapper, never()).insert(any(SysMenu.class));
    }

    @Test
    void create_whenParentIsButton_throws() {
        SysMenu button = new SysMenu();
        button.setId(3L);
        button.setParentId(2L);
        button.setType(3);
        when(sysMenuMapper.selectById(3L)).thenReturn(button);

        assertThatThrownBy(() -> menuService.create(createRequest(3L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.MENU_PARENT_TYPE_INVALID.getCode());
    }

    @Test
    void create_whenParentIsValidDirectory_inserts() {
        when(sysMenuMapper.selectById(1L)).thenReturn(directory);

        menuService.create(createRequest(1L));

        verify(sysMenuMapper).insert(any(SysMenu.class));
    }

    // ---------- update: 父级校验 ----------

    @Test
    void update_whenMenuNotFound_throws() {
        when(sysMenuMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> menuService.update(9L, updateRequest(0L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.MENU_NOT_FOUND.getCode());
    }

    @Test
    void update_whenParentIsSelf_throws() {
        when(sysMenuMapper.selectById(2L)).thenReturn(menu);

        assertThatThrownBy(() -> menuService.update(2L, updateRequest(2L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.MENU_INVALID_PARENT.getCode());

        verify(sysMenuMapper, never()).updateById(any(SysMenu.class));
    }

    @Test
    void update_whenParentIsDirectChild_throws() {
        SysMenu child = new SysMenu();
        child.setId(10L);
        child.setParentId(2L);
        child.setType(2);
        when(sysMenuMapper.selectById(2L)).thenReturn(menu);
        when(sysMenuMapper.selectById(10L)).thenReturn(child);

        assertThatThrownBy(() -> menuService.update(2L, updateRequest(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.MENU_INVALID_PARENT.getCode());
    }

    @Test
    void update_whenParentIsDeepDescendant_throws() {
        // 链: menu(2) -> mid(10) -> leaf(11)，将 menu 的父级设为 leaf 应成环
        SysMenu mid = new SysMenu();
        mid.setId(10L);
        mid.setParentId(2L);
        mid.setType(2);
        SysMenu leaf = new SysMenu();
        leaf.setId(11L);
        leaf.setParentId(10L);
        leaf.setType(2);
        when(sysMenuMapper.selectById(2L)).thenReturn(menu);
        when(sysMenuMapper.selectById(11L)).thenReturn(leaf);
        when(sysMenuMapper.selectById(10L)).thenReturn(mid);

        assertThatThrownBy(() -> menuService.update(2L, updateRequest(11L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.MENU_INVALID_PARENT.getCode());
    }

    @Test
    void update_whenParentIsValid_updates() {
        when(sysMenuMapper.selectById(2L)).thenReturn(menu);

        menuService.update(2L, updateRequest(0L));

        verify(sysMenuMapper).updateById(any(SysMenu.class));
    }

    @Test
    void create_whenAncestorAlreadyCyclic_rejectsWithoutLooping() {
        directory.setParentId(2L);
        when(sysMenuMapper.selectById(1L)).thenReturn(directory);
        when(sysMenuMapper.selectById(2L)).thenReturn(menu);
        assertThatThrownBy(() -> menuService.create(createRequest(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(AdminResultCode.MENU_INVALID_PARENT.getCode());
        verify(sysMenuMapper, never()).insert(any(SysMenu.class));
    }

    // ---------- helpers ----------

    private CreateMenuRequest createRequest(Long parentId) {
        CreateMenuRequest req = new CreateMenuRequest();
        req.setParentId(parentId);
        req.setName("测试菜单");
        req.setType(2);
        return req;
    }

    private UpdateMenuRequest updateRequest(Long parentId) {
        UpdateMenuRequest req = new UpdateMenuRequest();
        req.setParentId(parentId);
        req.setName("测试菜单");
        req.setType(2);
        return req;
    }
}
