package com.shadow.backend.admin.rbac;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shadow.backend.admin.adminuser.entity.SysUserRole;
import com.shadow.backend.admin.adminuser.mapper.SysUserRoleMapper;
import com.shadow.backend.admin.auth.entity.AdminUser;
import com.shadow.backend.admin.auth.mapper.AdminUserMapper;
import com.shadow.backend.admin.menu.entity.SysMenu;
import com.shadow.backend.admin.menu.mapper.SysMenuMapper;
import com.shadow.backend.admin.role.entity.SysRole;
import com.shadow.backend.admin.role.entity.SysRoleMenu;
import com.shadow.backend.admin.role.mapper.SysRoleMapper;
import com.shadow.backend.admin.role.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC 种子数据初始化器
 * <p>
 * 每次启动时补齐缺失数据：
 * 1. 默认菜单树（首页、系统管理(管理员管理, 角色管理, 菜单管理) + APP管理(用户管理) + 按钮权限）
 * 2. "超级管理员"角色（code=support），关联全部菜单
 * 3. 将 admin 用户关联到超级管理员角色
 * <p>
 * 运行优先级低于 AdminUserInitializer（确保 admin 用户已存在）。
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class RbacDataInitializer implements CommandLineRunner {

    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final AdminUserMapper adminUserMapper;

    @Override
    public void run(@NonNull String... args) {
        initMenus();
        SysRole role = ensureSuperAdminRole();
        initSuperAdminRoleMenus(role);
        assignAdminToSuperAdmin(role);
    }

    private void initMenus() {
        ensureMenu(0L, "首页", 2, "/", "LayoutDashboard", 1, "dashboard:view");
        SysMenu systemDir = ensureMenu(0L, "系统管理", 1, null, "Settings", 2, null);
        SysMenu appDir = ensureMenu(0L, "APP管理", 1, null, "Smartphone", 3, null);

        SysMenu adminUserMenu = ensureMenu(systemDir.getId(), "管理员管理", 2,
                "/admin-users", "UserCog", 1, "admin-user:list");
        ensureMenu(adminUserMenu.getId(), "新增管理员", 3, null, null, 1, "admin-user:create");
        ensureMenu(adminUserMenu.getId(), "修改管理员", 3, null, null, 2, "admin-user:update");
        ensureMenu(adminUserMenu.getId(), "删除管理员", 3, null, null, 3, "admin-user:delete");
        ensureMenu(adminUserMenu.getId(), "分配角色", 3, null, null, 4, "admin-user:assign");

        SysMenu roleMenu = ensureMenu(systemDir.getId(), "角色管理", 2, "/roles", "Shield", 2, "role:list");
        ensureMenu(roleMenu.getId(), "新增角色", 3, null, null, 1, "role:create");
        ensureMenu(roleMenu.getId(), "修改角色", 3, null, null, 2, "role:update");
        ensureMenu(roleMenu.getId(), "删除角色", 3, null, null, 3, "role:delete");
        ensureMenu(roleMenu.getId(), "分配权限", 3, null, null, 4, "role:assign");

        SysMenu menuMenu = ensureMenu(systemDir.getId(), "菜单管理", 2, "/menus", "Menu", 3, "menu:list");
        ensureMenu(menuMenu.getId(), "新增菜单", 3, null, null, 1, "menu:create");
        ensureMenu(menuMenu.getId(), "修改菜单", 3, null, null, 2, "menu:update");
        ensureMenu(menuMenu.getId(), "删除菜单", 3, null, null, 3, "menu:delete");

        SysMenu userMenu = ensureMenu(appDir.getId(), "用户管理", 2, "/users", "Users", 1, "user:list");
        ensureMenu(userMenu.getId(), "审核用户", 3, null, null, 1, "user:audit");
    }

    private SysRole ensureSuperAdminRole() {
        SysRole role = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, "support")
        );
        if (role != null) {
            return role;
        }

        role = new SysRole();
        role.setName("超级管理员");
        role.setCode("support");
        role.setSortOrder(1);
        role.setStatus(1);
        role.setRemark("拥有全部权限");
        try {
            sysRoleMapper.insert(role);
        } catch (DuplicateKeyException ex) {
            role = sysRoleMapper.selectOne(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, "support")
            );
            if (role == null) {
                throw ex;
            }
        }
        return role;
    }

    private void initSuperAdminRoleMenus(SysRole role) {
        Set<Long> menuIds = sysRoleMenuMapper.selectList(
                        new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, role.getId()))
                .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toSet());
        List<SysMenu> allMenus = sysMenuMapper.selectList(null);
        for (SysMenu menu : allMenus) {
            if (!menuIds.add(menu.getId())) {
                continue;
            }
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(role.getId());
            rm.setMenuId(menu.getId());
            try {
                sysRoleMenuMapper.insert(rm);
            } catch (DuplicateKeyException ex) {
                log.debug("RBAC: 角色菜单关联已存在，roleId={}, menuId={}", role.getId(), menu.getId());
            }
        }
    }

    private void assignAdminToSuperAdmin(SysRole role) {
        // 查找 admin 用户
        AdminUser admin = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, "admin")
        );
        if (admin == null) {
            log.warn("RBAC: admin 用户不存在，跳过角色分配");
            return;
        }

        // 检查是否已关联
        long count = sysUserRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, admin.getId())
                        .eq(SysUserRole::getRoleId, role.getId())
        );
        if (count > 0) {
            return;
        }

        SysUserRole ur = new SysUserRole();
        ur.setUserId(admin.getId());
        ur.setRoleId(role.getId());
        try {
            sysUserRoleMapper.insert(ur);
            log.info("RBAC: 已将 admin 用户关联到超级管理员角色");
        } catch (DuplicateKeyException ex) {
            log.debug("RBAC: 管理员角色关联已存在，userId={}, roleId={}", admin.getId(), role.getId());
        }
    }

    private SysMenu ensureMenu(Long parentId, String name, int type, String path,
                               String icon, int sortOrder, String permission) {
        LambdaQueryWrapper<SysMenu> query = new LambdaQueryWrapper<>();
        if (permission != null) {
            query.eq(SysMenu::getPermission, permission);
        } else {
            query.eq(SysMenu::getName, name).eq(SysMenu::getParentId, parentId);
        }
        SysMenu existing = sysMenuMapper.selectOne(query.last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        SysMenu menu = new SysMenu();
        menu.setParentId(parentId);
        menu.setName(name);
        menu.setType(type);
        menu.setPath(path);
        menu.setIcon(icon);
        menu.setSortOrder(sortOrder);
        menu.setPermission(permission);
        menu.setVisible(1);
        menu.setStatus(1);
        sysMenuMapper.insert(menu);
        return menu;
    }
}
