package com.shadow.backend.admin.menu.service;

import com.shadow.backend.admin.menu.dto.CreateMenuRequest;
import com.shadow.backend.admin.menu.dto.UpdateMenuRequest;
import com.shadow.backend.admin.menu.vo.MenuTreeVO;

import java.util.List;

public interface MenuService {

    /** 获取完整菜单树(管理用) */
    List<MenuTreeVO> getAllMenuTree();

    /** 获取当前登录管理员的菜单树(侧边栏用,按权限过滤) */
    List<MenuTreeVO> getCurrentUserMenuTree();

    /** 获取当前登录管理员的权限标识列表 */
    List<String> getCurrentUserPermissions();

    /** 创建菜单 */
    MenuTreeVO create(CreateMenuRequest request);

    /** 修改菜单 */
    MenuTreeVO update(Long id, UpdateMenuRequest request);

    /** 删除菜单 */
    void delete(Long id);
}
