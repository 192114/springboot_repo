package com.shadow.backend.admin.menu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shadow.backend.admin.menu.dto.CreateMenuRequest;
import com.shadow.backend.admin.menu.dto.UpdateMenuRequest;
import com.shadow.backend.admin.menu.entity.SysMenu;
import com.shadow.backend.admin.menu.mapper.SysMenuMapper;
import com.shadow.backend.admin.menu.service.MenuService;
import com.shadow.backend.admin.menu.vo.MenuTreeVO;
import com.shadow.backend.admin.auth.response.AdminResultCode;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.admin.auth.util.LoginAdminUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final SysMenuMapper sysMenuMapper;

    @Override
    public List<MenuTreeVO> getAllMenuTree() {
        List<SysMenu> menus = sysMenuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSortOrder)
        );
        return buildTree(menus);
    }

    @Override
    public List<MenuTreeVO> getCurrentUserMenuTree() {
        Long userId = LoginAdminUtil.currentAdminId();
        List<SysMenu> menus = sysMenuMapper.selectMenusByUserId(userId);
        // 只返回目录(1)和菜单(2),不返回按钮(3)
        menus = menus.stream()
                .filter(m -> m.getType() != null && m.getType() != 3)
                .collect(Collectors.toList());
        return buildTree(menus);
    }

    @Override
    public List<String> getCurrentUserPermissions() {
        Long userId = LoginAdminUtil.currentAdminId();
        return sysMenuMapper.selectPermissionsByUserId(userId);
    }

    @Override
    public MenuTreeVO create(CreateMenuRequest request) {
        validateParent(request.getParentId(), null);
        SysMenu menu = new SysMenu();
        menu.setParentId(request.getParentId());
        menu.setName(request.getName());
        menu.setType(request.getType());
        menu.setPath(request.getPath());
        menu.setIcon(request.getIcon());
        menu.setSortOrder(request.getSortOrder());
        menu.setPermission(request.getPermission());
        menu.setVisible(request.getVisible());
        menu.setStatus(request.getStatus());
        sysMenuMapper.insert(menu);
        return toVO(menu);
    }

    @Override
    public MenuTreeVO update(Long id, UpdateMenuRequest request) {
        SysMenu menu = sysMenuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(AdminResultCode.MENU_NOT_FOUND);
        }
        validateParent(request.getParentId(), id);
        menu.setParentId(request.getParentId());
        menu.setName(request.getName());
        menu.setType(request.getType());
        menu.setPath(request.getPath());
        menu.setIcon(request.getIcon());
        menu.setSortOrder(request.getSortOrder());
        menu.setPermission(request.getPermission());
        menu.setVisible(request.getVisible());
        menu.setStatus(request.getStatus());
        sysMenuMapper.updateById(menu);
        return toVO(menu);
    }

    @Override
    public void delete(Long id) {
        SysMenu menu = sysMenuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(AdminResultCode.MENU_NOT_FOUND);
        }
        // 检查是否有子菜单
        long childCount = sysMenuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id)
        );
        if (childCount > 0) {
            throw new BusinessException(AdminResultCode.MENU_HAS_CHILDREN);
        }
        sysMenuMapper.deleteById(id);
    }

    // ==================== Validation ====================

    /**
     * 校验父级菜单合法性：存在、非按钮类型、（更新时）不能为自身或其后代。
     * <p>
     * 成环判定沿 parent 链向上回溯：若从 parentId 出发能走到 selfId，
     * 说明新父级位于自身子树内，会构成环。
     */
    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BusinessException(AdminResultCode.MENU_INVALID_PARENT);
        }
        SysMenu parent = sysMenuMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(AdminResultCode.MENU_PARENT_NOT_FOUND);
        }
        if (parent.getType() != null && parent.getType() == 3) {
            throw new BusinessException(AdminResultCode.MENU_PARENT_TYPE_INVALID);
        }
        Set<Long> visited = new HashSet<>();
        visited.add(parentId);
        Long cursor = parent.getParentId();
        while (cursor != null && cursor != 0L) {
            if (cursor.equals(selfId) || !visited.add(cursor)) {
                throw new BusinessException(AdminResultCode.MENU_INVALID_PARENT);
            }
            SysMenu ancestor = sysMenuMapper.selectById(cursor);
            if (ancestor == null) {
                throw new BusinessException(AdminResultCode.MENU_PARENT_NOT_FOUND);
            }
            cursor = ancestor.getParentId();
        }
    }

    // ==================== Tree Building ====================

    private List<MenuTreeVO> buildTree(List<SysMenu> menus) {
        if (menus == null || menus.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, MenuTreeVO> voMap = menus.stream()
                .map(this::toVO)
                .collect(Collectors.toMap(MenuTreeVO::getId, vo -> vo));

        List<MenuTreeVO> roots = new ArrayList<>();
        for (MenuTreeVO vo : voMap.values()) {
            if (vo.getParentId() == null || vo.getParentId() == 0L) {
                roots.add(vo);
            } else {
                MenuTreeVO parent = voMap.get(vo.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(vo);
                } else {
                    // parent not in current set, treat as root
                    roots.add(vo);
                }
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<MenuTreeVO> tree) {
        if (tree == null || tree.isEmpty()) return;
        tree.sort(Comparator.comparingInt(vo ->
                vo.getSortOrder() != null ? vo.getSortOrder() : 0));
        for (MenuTreeVO vo : tree) {
            sortTree(vo.getChildren());
        }
    }

    private MenuTreeVO toVO(SysMenu menu) {
        MenuTreeVO vo = new MenuTreeVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setName(menu.getName());
        vo.setType(menu.getType());
        vo.setPath(menu.getPath());
        vo.setIcon(menu.getIcon());
        vo.setSortOrder(menu.getSortOrder());
        vo.setPermission(menu.getPermission());
        vo.setVisible(menu.getVisible());
        vo.setStatus(menu.getStatus());
        return vo;
    }
}
