-- 可重复执行；在目标数据库中单实例执行，仅补缺项，不覆盖或恢复已有菜单与授权。
START TRANSACTION;

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT 0, '首页', 2, '/', 'LayoutDashboard', 1, 'dashboard:view', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'dashboard:view');

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT 0, '系统管理', 1, NULL, 'Settings', 2, NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 0 AND name = '系统管理');

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT 0, 'APP管理', 1, NULL, 'Smartphone', 3, NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 0 AND name = 'APP管理');

SET @system_id = (SELECT MIN(id) FROM sys_menu WHERE parent_id = 0 AND name = '系统管理' AND deleted = 0);
SET @app_id = (SELECT MIN(id) FROM sys_menu WHERE parent_id = 0 AND name = 'APP管理' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @system_id, '管理员管理', 2, '/admin-users', 'UserCog', 1, 'admin-user:list', 1, 1
WHERE @system_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'admin-user:list');

SET @admin_menu_id = (SELECT MIN(id) FROM sys_menu WHERE permission = 'admin-user:list' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @admin_menu_id, '新增管理员', 3, NULL, NULL, 1, 'admin-user:create', 1, 1
WHERE @admin_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'admin-user:create');

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @admin_menu_id, '修改管理员', 3, NULL, NULL, 2, 'admin-user:update', 1, 1
WHERE @admin_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'admin-user:update');

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @admin_menu_id, '删除管理员', 3, NULL, NULL, 3, 'admin-user:delete', 1, 1
WHERE @admin_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'admin-user:delete');

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @admin_menu_id, '分配角色', 3, NULL, NULL, 4, 'admin-user:assign', 1, 1
WHERE @admin_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'admin-user:assign');

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @system_id, '角色管理', 2, '/roles', 'Shield', 2, 'role:list', 1, 1
WHERE @system_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'role:list');

SET @role_menu_id = (SELECT MIN(id) FROM sys_menu WHERE permission = 'role:list' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @role_menu_id, '新增角色', 3, NULL, NULL, 1, 'role:create', 1, 1
WHERE @role_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'role:create');

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @role_menu_id, '修改角色', 3, NULL, NULL, 2, 'role:update', 1, 1
WHERE @role_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'role:update');

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @role_menu_id, '删除角色', 3, NULL, NULL, 3, 'role:delete', 1, 1
WHERE @role_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'role:delete');

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @role_menu_id, '分配权限', 3, NULL, NULL, 4, 'role:assign', 1, 1
WHERE @role_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'role:assign');

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @system_id, '菜单管理', 2, '/menus', 'Menu', 3, 'menu:list', 1, 1
WHERE @system_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'menu:list');

SET @menu_menu_id = (SELECT MIN(id) FROM sys_menu WHERE permission = 'menu:list' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @menu_menu_id, '新增菜单', 3, NULL, NULL, 1, 'menu:create', 1, 1
WHERE @menu_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'menu:create');

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @menu_menu_id, '修改菜单', 3, NULL, NULL, 2, 'menu:update', 1, 1
WHERE @menu_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'menu:update');

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @menu_menu_id, '删除菜单', 3, NULL, NULL, 3, 'menu:delete', 1, 1
WHERE @menu_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'menu:delete');

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @app_id, 'App 用户管理', 2, '/app-users', 'Users', 1, 'user:list', 1, 1
WHERE @app_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'user:list');

SET @user_menu_id = (SELECT MIN(id) FROM sys_menu WHERE permission = 'user:list' AND deleted = 0);

INSERT INTO sys_menu (parent_id, name, type, path, icon, sort_order, permission, visible, status)
SELECT @user_menu_id, '审核用户', 3, NULL, NULL, 1, 'user:audit', 1, 1
WHERE @user_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'user:audit');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.code = 'support' AND r.deleted = 0 AND r.status = 1 AND m.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

COMMIT;
