package com.shadow.backend.admin.rbac;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacDataInitializerTest {

    @Mock
    private SysMenuMapper sysMenuMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysRoleMenuMapper sysRoleMenuMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private AdminUserMapper adminUserMapper;
    @InjectMocks
    private RbacDataInitializer initializer;

    @BeforeEach
    void setUp() {
        for (Class<?> entity : List.of(SysMenu.class, SysRole.class, SysRoleMenu.class,
                SysUserRole.class, AdminUser.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entity);
        }
    }

    @Test
    void run_whenMenusPartiallyExist_preservesAllFieldsAndInsertsWithRealParentIds() {
        List<SysMenu> existing = List.of(
                menu(41L, 0L, "系统管理", 1, null),
                menu(42L, 41L, "APP管理", 1, null),
                menu(43L, 0L, "APP管理", 1, null),
                menu(44L, 41L, "自定义用户入口", 2, "user:list"),
                menu(45L, 0L, "自定义报表", 2, "custom:report"),
                menu(46L, 44L, "自定义修改按钮", 3, "admin-user:update"),
                menu(47L, 44L, "自定义首页", 2, "dashboard:view"));
        List<String> snapshots = existing.stream().map(SysMenu::toString).toList();
        List<SysMenu> menus = new ArrayList<>(existing);
        stubMenus(menus);
        when(sysRoleMapper.selectOne(any())).thenReturn(supportRole());

        initializer.run();

        assertThat(existing.stream().map(SysMenu::toString).toList()).isEqualTo(snapshots);
        assertThat(menus).hasSize(21);
        assertThat(byPermission(menus, "user:audit").getParentId()).isEqualTo(44L);
        assertThat(byPermission(menus, "admin-user:list").getParentId()).isEqualTo(41L);
        assertThat(byPermission(menus, "admin-user:update")).isSameAs(existing.get(5));
        verify(sysMenuMapper, times(14)).insert(any(SysMenu.class));
        verify(sysMenuMapper, never()).updateById(any(SysMenu.class));
        verify(sysMenuMapper, never()).delete(any());
    }

    @Test
    void run_whenRoleExists_fillsOnlyMissingMenusAndKeepsExistingAdminAssociation() {
        List<SysMenu> menus = new ArrayList<>(List.of(
                menu(11L, 0L, "首页", 2, "dashboard:view"),
                menu(12L, 0L, "自定义报表", 2, "custom:report")));
        stubMenus(menus);
        when(sysMenuMapper.selectList(null)).thenAnswer(invocation -> List.copyOf(menus));
        SysRole role = supportRole();
        String snapshot = role.toString();
        when(sysRoleMapper.selectOne(any())).thenReturn(role);
        when(sysRoleMenuMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<SysRoleMenu> query = invocation.getArgument(0);
            assertThat(queryValues(query)).containsExactly(role.getId());
            assertThat(query.getSqlSegment()).contains("role_id =");
            return List.of(roleMenu(role.getId(), 11L), roleMenu(role.getId(), 999L));
        });
        stubAdmin();
        when(sysUserRoleMapper.selectCount(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<SysUserRole> query = invocation.getArgument(0);
            assertThat(queryValues(query)).containsExactlyInAnyOrder(7L, role.getId());
            assertThat(query.getSqlSegment()).contains("user_id =", "role_id =");
            return 1L;
        });

        initializer.run();

        ArgumentCaptor<SysRoleMenu> captor = ArgumentCaptor.forClass(SysRoleMenu.class);
        verify(sysRoleMenuMapper, times(menus.size() - 1)).insert(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(link -> assertThat(link.getRoleId()).isEqualTo(role.getId()));
        assertThat(captor.getAllValues()).extracting(SysRoleMenu::getMenuId)
                .containsExactlyInAnyOrderElementsOf(menus.stream().map(SysMenu::getId)
                        .filter(id -> id != 11L).toList());
        assertThat(role.toString()).isEqualTo(snapshot);
        verify(sysRoleMapper, never()).insert(any(SysRole.class));
        verify(sysRoleMapper, never()).updateById(any(SysRole.class));
        verify(sysRoleMenuMapper, never()).delete(any());
        verify(sysUserRoleMapper, never()).insert(any(SysUserRole.class));
        verify(sysUserRoleMapper, never()).delete(any());
    }

    @Test
    void run_twice_doesNotDuplicateMenusRoleOrAssociations() {
        List<SysMenu> menus = new ArrayList<>();
        List<SysRole> roles = new ArrayList<>();
        List<SysRoleMenu> roleMenus = new ArrayList<>();
        List<SysUserRole> userRoles = new ArrayList<>();
        stubMenus(menus);
        when(sysMenuMapper.selectList(null)).thenAnswer(invocation -> List.copyOf(menus));
        when(sysRoleMapper.selectOne(any())).thenAnswer(invocation -> roles.isEmpty() ? null : roles.getFirst());
        when(sysRoleMapper.insert(any(SysRole.class))).thenAnswer(invocation -> {
            SysRole role = invocation.getArgument(0);
            role.setId(90L);
            roles.add(role);
            return 1;
        });
        when(sysRoleMenuMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(roleMenus));
        when(sysRoleMenuMapper.insert(any(SysRoleMenu.class))).thenAnswer(invocation -> {
            SysRoleMenu link = invocation.getArgument(0);
            assertThat(roleMenus).doesNotContain(link);
            roleMenus.add(link);
            return 1;
        });
        stubAdmin();
        when(sysUserRoleMapper.selectCount(any())).thenAnswer(invocation -> (long) userRoles.size());
        when(sysUserRoleMapper.insert(any(SysUserRole.class))).thenAnswer(invocation -> {
            userRoles.add(invocation.getArgument(0));
            return 1;
        });

        initializer.run();

        assertThat(menus).hasSize(19);
        assertThat(roles).singleElement().satisfies(role -> {
            assertThat(role.getCode()).isEqualTo("support");
            assertThat(role.getStatus()).isEqualTo(1);
        });
        assertThat(roleMenus).hasSize(19);
        assertThat(userRoles).singleElement().satisfies(link -> {
            assertThat(link.getUserId()).isEqualTo(7L);
            assertThat(link.getRoleId()).isEqualTo(90L);
        });
        clearInvocations(sysMenuMapper, sysRoleMapper, sysRoleMenuMapper, sysUserRoleMapper);

        initializer.run();

        verify(sysMenuMapper, never()).insert(any(SysMenu.class));
        verify(sysRoleMapper, never()).insert(any(SysRole.class));
        verify(sysRoleMenuMapper, never()).insert(any(SysRoleMenu.class));
        verify(sysUserRoleMapper, never()).insert(any(SysUserRole.class));
    }

    @Test
    void run_whenAssociationInsertsRace_toleratesOnlyDuplicateKeysAndContinues() {
        List<SysMenu> menus = new ArrayList<>();
        stubMenus(menus);
        when(sysMenuMapper.selectList(null)).thenAnswer(invocation -> List.copyOf(menus));
        when(sysRoleMapper.selectOne(any())).thenReturn(supportRole());
        when(sysRoleMenuMapper.insert(any(SysRoleMenu.class)))
                .thenThrow(new DuplicateKeyException("role menu exists")).thenReturn(1);
        stubAdmin();
        when(sysUserRoleMapper.insert(any(SysUserRole.class)))
                .thenThrow(new DuplicateKeyException("user role exists"));

        assertThatCode(() -> initializer.run()).doesNotThrowAnyException();

        verify(sysRoleMenuMapper, times(19)).insert(any(SysRoleMenu.class));
        verify(sysUserRoleMapper).insert(any(SysUserRole.class));
    }

    @Test
    void run_whenRoleMenuInsertFails_propagatesDatabaseException() {
        List<SysMenu> menus = new ArrayList<>();
        stubMenus(menus);
        when(sysMenuMapper.selectList(null)).thenAnswer(invocation -> List.copyOf(menus));
        when(sysRoleMapper.selectOne(any())).thenReturn(supportRole());
        DataIntegrityViolationException failure = new DataIntegrityViolationException("invalid relation");
        when(sysRoleMenuMapper.insert(any(SysRoleMenu.class))).thenThrow(failure);

        assertThatThrownBy(() -> initializer.run()).isSameAs(failure);

        verifyNoInteractions(adminUserMapper, sysUserRoleMapper);
    }

    @Test
    void run_whenAdminRoleInsertFails_propagatesDatabaseException() {
        stubMenus(new ArrayList<>());
        when(sysRoleMapper.selectOne(any())).thenReturn(supportRole());
        stubAdmin();
        DataIntegrityViolationException failure = new DataIntegrityViolationException("invalid relation");
        when(sysUserRoleMapper.insert(any(SysUserRole.class))).thenThrow(failure);

        assertThatThrownBy(() -> initializer.run()).isSameAs(failure);
    }

    @Test
    void run_whenRoleCreationRaces_requeriesExistingRoleWithoutChangingIt() {
        List<SysMenu> menus = new ArrayList<>();
        stubMenus(menus);
        when(sysMenuMapper.selectList(null)).thenAnswer(invocation -> List.copyOf(menus));
        SysRole role = supportRole();
        String snapshot = role.toString();
        when(sysRoleMapper.selectOne(any())).thenReturn(null, role);
        when(sysRoleMapper.insert(any(SysRole.class))).thenThrow(new DuplicateKeyException("role exists"));
        stubAdmin();

        initializer.run();

        verify(sysRoleMapper, times(2)).selectOne(any());
        ArgumentCaptor<SysRoleMenu> menusCaptor = ArgumentCaptor.forClass(SysRoleMenu.class);
        verify(sysRoleMenuMapper, times(19)).insert(menusCaptor.capture());
        assertThat(menusCaptor.getAllValues()).allSatisfy(link -> assertThat(link.getRoleId()).isEqualTo(role.getId()));
        ArgumentCaptor<SysUserRole> userCaptor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(sysUserRoleMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getRoleId()).isEqualTo(role.getId());
        assertThat(role.toString()).isEqualTo(snapshot);
        verify(sysRoleMapper, never()).updateById(any(SysRole.class));
    }

    @Test
    void run_whenRoleCodeIsOccupiedByDeletedRole_doesNotRestoreIt() {
        stubMenus(new ArrayList<>());
        when(sysRoleMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<SysRole> query = invocation.getArgument(0);
            assertThat(queryValues(query)).containsExactly("support");
            assertThat(query.getSqlSegment()).contains("code =");
            return null;
        });
        DuplicateKeyException failure = new DuplicateKeyException("deleted role occupies code");
        when(sysRoleMapper.insert(any(SysRole.class))).thenThrow(failure);

        assertThatThrownBy(() -> initializer.run()).isSameAs(failure);

        verify(sysRoleMapper, times(2)).selectOne(any());
        verify(sysRoleMapper, never()).updateById(any(SysRole.class));
        verifyNoInteractions(sysRoleMenuMapper, adminUserMapper, sysUserRoleMapper);
    }

    @Test
    void run_whenRoleCreationFails_propagatesWithoutRetrying() {
        stubMenus(new ArrayList<>());
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(sysRoleMapper.insert(any(SysRole.class))).thenThrow(failure);

        assertThatThrownBy(() -> initializer.run()).isSameAs(failure);

        verify(sysRoleMapper).selectOne(any());
        verifyNoInteractions(sysRoleMenuMapper, adminUserMapper, sysUserRoleMapper);
    }

    @Test
    void run_whenMenuInsertionFails_retryCompletesRemainingTree() {
        List<SysMenu> menus = new ArrayList<>();
        stubMenuLookup(menus);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        doAnswer(invocation -> {
            SysMenu menu = invocation.getArgument(0);
            if ("role:list".equals(menu.getPermission()) && failOnce.getAndSet(false)) {
                throw failure;
            }
            return insertMenu(menus, menu);
        }).when(sysMenuMapper).insert(any(SysMenu.class));

        assertThatThrownBy(() -> initializer.run()).isSameAs(failure);
        assertThat(menus).hasSize(8);
        verifyNoInteractions(sysRoleMapper, sysRoleMenuMapper, adminUserMapper, sysUserRoleMapper);
        List<String> snapshots = menus.stream().map(SysMenu::toString).toList();
        when(sysRoleMapper.selectOne(any())).thenReturn(supportRole());

        initializer.run();

        assertThat(menus).hasSize(19);
        assertThat(menus.subList(0, 8).stream().map(SysMenu::toString).toList()).isEqualTo(snapshots);
        verify(sysMenuMapper, never()).updateById(any(SysMenu.class));
    }

    private void stubMenus(List<SysMenu> menus) {
        stubMenuLookup(menus);
        when(sysMenuMapper.insert(any(SysMenu.class)))
                .thenAnswer(invocation -> insertMenu(menus, invocation.getArgument(0)));
    }

    private void stubMenuLookup(List<SysMenu> menus) {
        when(sysMenuMapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<SysMenu> query = invocation.getArgument(0);
            Collection<Object> values = queryValues(query);
            if (query.getSqlSegment().contains("permission =")) {
                assertThat(values).hasSize(1);
                return menus.stream().filter(menu -> values.contains(menu.getPermission())).findFirst().orElse(null);
            }
            assertThat(query.getSqlSegment()).contains("name =", "parent_id =");
            assertThat(values).hasSize(2);
            return menus.stream().filter(menu -> values.contains(menu.getName()) && values.contains(menu.getParentId()))
                    .findFirst().orElse(null);
        });
    }

    private int insertMenu(List<SysMenu> menus, SysMenu menu) {
        Long expectedParent;
        if (menu.getType() == 1 || "dashboard:view".equals(menu.getPermission())) {
            expectedParent = 0L;
        } else if (menu.getPermission().endsWith(":list")) {
            String directory = "user:list".equals(menu.getPermission()) ? "APP管理" : "系统管理";
            expectedParent = menus.stream().filter(parent -> directory.equals(parent.getName()) && parent.getParentId() == 0L)
                    .findFirst().orElseThrow().getId();
        } else {
            expectedParent = byPermission(menus, menu.getPermission().split(":")[0] + ":list").getId();
        }
        assertThat(menu.getParentId()).isEqualTo(expectedParent);
        assertThat(menu.getStatus()).isEqualTo(1);
        assertThat(menu.getVisible()).isEqualTo(1);
        menu.setId(1000L + menus.size());
        menus.add(menu);
        return 1;
    }

    private Collection<Object> queryValues(LambdaQueryWrapper<?> query) {
        query.getSqlSegment();
        return query.getParamNameValuePairs().values();
    }

    private void stubAdmin() {
        AdminUser admin = new AdminUser();
        admin.setId(7L);
        admin.setUsername("admin");
        when(adminUserMapper.selectOne(any())).thenReturn(admin);
    }

    private SysRole supportRole() {
        SysRole role = new SysRole();
        role.setId(90L);
        role.setCode("support");
        role.setName("自定义超管");
        role.setStatus(0);
        role.setSortOrder(77);
        role.setRemark("保留自定义角色配置");
        role.setDeleted(0);
        return role;
    }

    private SysRoleMenu roleMenu(Long roleId, Long menuId) {
        SysRoleMenu link = new SysRoleMenu();
        link.setRoleId(roleId);
        link.setMenuId(menuId);
        return link;
    }

    private SysMenu byPermission(List<SysMenu> menus, String permission) {
        return menus.stream().filter(menu -> permission.equals(menu.getPermission())).findFirst().orElseThrow();
    }

    private SysMenu menu(Long id, Long parentId, String name, int type, String permission) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setName(name);
        menu.setType(type);
        menu.setPermission(permission);
        menu.setPath("/custom/" + id);
        menu.setIcon("CustomIcon");
        menu.setSortOrder(88);
        menu.setStatus(0);
        menu.setVisible(0);
        menu.setDeleted(0);
        menu.setCreateTime(LocalDateTime.of(2024, 1, 1, 0, 0));
        menu.setUpdateTime(LocalDateTime.of(2025, 1, 1, 0, 0));
        return menu;
    }
}
