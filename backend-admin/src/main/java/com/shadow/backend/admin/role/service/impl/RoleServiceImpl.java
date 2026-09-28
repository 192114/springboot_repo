package com.shadow.backend.admin.role.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shadow.backend.admin.auth.response.AdminResultCode;
import com.shadow.backend.admin.menu.mapper.SysMenuMapper;
import com.shadow.backend.admin.role.dto.AssignMenusRequest;
import com.shadow.backend.admin.role.dto.CreateRoleRequest;
import com.shadow.backend.admin.role.dto.UpdateRoleRequest;
import com.shadow.backend.admin.role.entity.SysRole;
import com.shadow.backend.admin.role.entity.SysRoleMenu;
import com.shadow.backend.admin.role.mapper.SysRoleMapper;
import com.shadow.backend.admin.role.mapper.SysRoleMenuMapper;
import com.shadow.backend.admin.role.service.RoleService;
import com.shadow.backend.admin.role.vo.RoleVO;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.common.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;

    @Override
    public PageResult<RoleVO> page(long current, long size, String name, Integer status) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) {
            wrapper.like(SysRole::getName, name);
        }
        if (status != null) {
            wrapper.eq(SysRole::getStatus, status);
        }
        wrapper.orderByAsc(SysRole::getSortOrder);

        Page<SysRole> page = new Page<>(current, size);
        Page<SysRole> result = sysRoleMapper.selectPage(page, wrapper);

        List<RoleVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.of(current, size, result.getTotal(), records);
    }

    @Override
    public List<RoleVO> listAll() {
        List<SysRole> roles = sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getStatus, 1)
                        .orderByAsc(SysRole::getSortOrder)
        );
        return roles.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public RoleVO getById(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(AdminResultCode.ROLE_NOT_FOUND);
        }
        RoleVO vo = toVO(role);
        // 查询已关联的菜单ID
        List<SysRoleMenu> roleMenus = sysRoleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id)
        );
        vo.setMenuIds(roleMenus.stream()
                .map(SysRoleMenu::getMenuId)
                .collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional
    public RoleVO create(CreateRoleRequest request) {
        // 检查 code 唯一性
        long count = sysRoleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, request.getCode())
        );
        if (count > 0) {
            throw new BusinessException(AdminResultCode.ROLE_CODE_EXISTS);
        }

        SysRole role = new SysRole();
        role.setName(request.getName());
        role.setCode(request.getCode());
        role.setSortOrder(request.getSortOrder());
        role.setStatus(1);
        role.setRemark(request.getRemark());
        sysRoleMapper.insert(role);
        return toVO(role);
    }

    @Override
    @Transactional
    public RoleVO update(Long id, UpdateRoleRequest request) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(AdminResultCode.ROLE_NOT_FOUND);
        }
        role.setName(request.getName());
        role.setSortOrder(request.getSortOrder());
        role.setRemark(request.getRemark());
        sysRoleMapper.updateById(role);
        return toVO(role);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(AdminResultCode.ROLE_NOT_FOUND);
        }
        // 检查是否有关联用户
        long userCount = sysRoleMapper.countUsersByRoleId(id);
        if (userCount > 0) {
            throw new BusinessException(AdminResultCode.ROLE_IN_USE);
        }
        // 删除角色-菜单关联
        sysRoleMenuMapper.delete(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id)
        );
        sysRoleMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void assignMenus(Long id, AssignMenusRequest request) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(AdminResultCode.ROLE_NOT_FOUND);
        }
        List<Long> menuIds = request.getMenuIds() == null ? List.of()
                : request.getMenuIds().stream().distinct().toList();
        if (menuIds.stream().anyMatch(Objects::isNull)
                || (!menuIds.isEmpty() && sysMenuMapper.selectByIds(menuIds).size() != menuIds.size())) {
            throw new BusinessException(AdminResultCode.MENU_ASSIGNMENT_INVALID);
        }
        sysRoleMenuMapper.delete(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id)
        );
        for (Long menuId : menuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(id);
            rm.setMenuId(menuId);
            sysRoleMenuMapper.insert(rm);
        }
    }

    private RoleVO toVO(SysRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setName(role.getName());
        vo.setCode(role.getCode());
        vo.setSortOrder(role.getSortOrder());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        vo.setCreateTime(role.getCreateTime());
        vo.setUpdateTime(role.getUpdateTime());
        return vo;
    }
}
