package com.shadow.backend.admin.adminuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shadow.backend.admin.adminuser.dto.AssignRolesRequest;
import com.shadow.backend.admin.adminuser.dto.CreateAdminUserRequest;
import com.shadow.backend.admin.adminuser.dto.UpdateAdminUserRequest;
import com.shadow.backend.admin.adminuser.entity.SysUserRole;
import com.shadow.backend.admin.adminuser.mapper.SysUserRoleMapper;
import com.shadow.backend.admin.adminuser.service.AdminUserManageService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserManageServiceImpl implements AdminUserManageService {

    /** 超级管理员角色编码（种子数据约定） */
    private static final String SUPER_ADMIN_ROLE_CODE = "support";

    private final AdminUserMapper adminUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordUtil passwordUtil;

    @Override
    public PageResult<AdminUserManageVO> page(long current, long size, String username, Long roleId, Integer status) {
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) {
            wrapper.like(AdminUser::getUsername, username);
        }
        if (status != null) {
            wrapper.eq(AdminUser::getStatus, status);
        }
        if (roleId != null) {
            List<Long> userIds = sysUserRoleMapper.selectList(
                            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId)
                    ).stream()
                    .map(SysUserRole::getUserId)
                    .collect(Collectors.toList());
            if (userIds.isEmpty()) {
                return PageResult.of(current, size, 0, Collections.emptyList());
            }
            wrapper.in(AdminUser::getId, userIds);
        }
        wrapper.orderByDesc(AdminUser::getCreateTime);

        Page<AdminUser> page = new Page<>(current, size);
        Page<AdminUser> result = adminUserMapper.selectPage(page, wrapper);

        List<AdminUserManageVO> records = result.getRecords().stream()
                .map(this::toVOWithoutRoles)
                .collect(Collectors.toList());

        // 批量加载角色
        if (!records.isEmpty()) {
            List<Long> userIds = records.stream().map(AdminUserManageVO::getId).collect(Collectors.toList());
            Map<Long, List<AdminUserManageVO.SimpleRole>> roleMap = batchLoadRoles(userIds);
            for (AdminUserManageVO vo : records) {
                vo.setRoles(roleMap.getOrDefault(vo.getId(), Collections.emptyList()));
            }
        }

        return PageResult.of(current, size, result.getTotal(), records);
    }

    @Override
    public AdminUserManageVO getById(Long id) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(AdminResultCode.ADMIN_NOT_FOUND);
        }
        AdminUserManageVO vo = toVOWithoutRoles(user);
        vo.setRoles(loadRoles(id));
        return vo;
    }

    @Override
    @Transactional
    public AdminUserManageVO create(CreateAdminUserRequest request) {
        // 检查用户名唯一
        long count = adminUserMapper.selectCount(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw new BusinessException(AdminResultCode.ADMIN_USERNAME_EXISTS);
        }

        AdminUser user = new AdminUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordUtil.hash(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setStatus(request.getStatus());
        adminUserMapper.insert(user);

        return toVOWithoutRoles(user);
    }

    @Override
    @Transactional
    public AdminUserManageVO update(Long id, UpdateAdminUserRequest request) {
        Long supportRoleId = request.getStatus() == null ? null : lockSuperAdminRole();
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(AdminResultCode.ADMIN_NOT_FOUND);
        }

        // 不能禁用自己
        Long currentId = LoginAdminUtil.currentAdminId();
        if (id.equals(currentId) && request.getStatus() != null && request.getStatus() == 0) {
            throw new BusinessException(AdminResultCode.CANNOT_DISABLE_SELF);
        }

        // 最后超管保护：禁用启用的超管前，确保仍有其他启用的超管
        boolean disabling = request.getStatus() != null && request.getStatus() == 0;
        if (disabling && user.getStatus() == 1 && supportRoleId != null) {
            ensureNotLastSuperAdmin(id, supportRoleId);
        }

        if (request.getNickname() == null && request.getEmail() == null && request.getStatus() == null) {
            return toVOWithoutRoles(user);
        }
        adminUserMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .eq(AdminUser::getId, id)
                .set(request.getNickname() != null, AdminUser::getNickname, request.getNickname())
                .set(request.getEmail() != null, AdminUser::getEmail, request.getEmail())
                .set(request.getStatus() != null, AdminUser::getStatus, request.getStatus())
                .set(AdminUser::getUpdateTime, LocalDateTime.now()));
        if (disabling) {
            LoginAdminUtil.logoutAdmin(id);
        }

        return toVOWithoutRoles(adminUserMapper.selectById(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long supportRoleId = lockSuperAdminRole();
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(AdminResultCode.ADMIN_NOT_FOUND);
        }

        // 不能删除自己
        Long currentId = LoginAdminUtil.currentAdminId();
        if (id.equals(currentId)) {
            throw new BusinessException(AdminResultCode.CANNOT_DELETE_SELF);
        }

        // 最后超管保护：删除启用的超管前，确保仍有其他启用的超管
        if (user.getStatus() == 1 && supportRoleId != null) {
            ensureNotLastSuperAdmin(id, supportRoleId);
        }

        // 删除用户-角色关联
        sysUserRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id)
        );
        adminUserMapper.deleteById(id);

        // 删除成功后撤销其全部会话
        LoginAdminUtil.logoutAdmin(id);
    }

    @Override
    @Transactional
    public void assignRoles(Long id, AssignRolesRequest request) {
        Long supportRoleId = lockSuperAdminRole();
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(AdminResultCode.ADMIN_NOT_FOUND);
        }

        // roleIds 去重
        List<Long> roleIds = request.getRoleIds() == null ? List.of()
                : request.getRoleIds().stream().distinct().toList();

        // 校验角色存在且启用
        List<SysRole> roles = roleIds.isEmpty() ? List.of() : sysRoleMapper.selectByIds(roleIds);
        if (roles.size() != roleIds.size()
                || roles.stream().anyMatch(role -> role.getStatus() == null || role.getStatus() != 1)) {
            throw new BusinessException(AdminResultCode.ROLE_ASSIGNMENT_INVALID);
        }

        // 目标角色集合必须是操作者自身角色集合的子集（防越级授权）；超级管理员豁免
        Long currentId = LoginAdminUtil.currentAdminId();
        List<String> operatorRoleCodes = sysRoleMapper.selectRoleCodesByUserId(currentId);
        if (!operatorRoleCodes.contains(SUPER_ADMIN_ROLE_CODE)) {
            Set<String> operatorRoleCodeSet = new HashSet<>(operatorRoleCodes);
            boolean beyondScope = roles.stream()
                    .anyMatch(role -> !operatorRoleCodeSet.contains(role.getCode()));
            if (beyondScope) {
                throw new BusinessException(AdminResultCode.ROLE_ASSIGNMENT_FORBIDDEN);
            }
        }

        // 禁止修改自己的角色集合（防自提权）
        if (id.equals(currentId)) {
            throw new BusinessException(AdminResultCode.CANNOT_MODIFY_OWN_ROLES);
        }

        // 最后超管保护：移除启用超管的 support 角色时，确保仍有其他启用的超管
        if (user.getStatus() == 1 && supportRoleId != null && !roleIds.contains(supportRoleId)) {
            ensureNotLastSuperAdmin(id, supportRoleId);
        }

        // 先删除旧关联
        sysUserRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id)
        );

        // 插入新关联
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(id);
            ur.setRoleId(roleId);
            sysUserRoleMapper.insert(ur);
        }
    }

    // ==================== Private Helpers ====================

    /**
     * 最后超管保护：目标当前持有 support 角色时，确认除目标外仍存在启用的超级管理员，否则拒绝操作。
     */
    private void ensureNotLastSuperAdmin(Long targetUserId, Long supportRoleId) {
        if (!hasRole(targetUserId, supportRoleId)) {
            return;
        }
        List<Long> otherUserIds = sysUserRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, supportRoleId))
                .stream().map(SysUserRole::getUserId).distinct()
                .filter(userId -> !userId.equals(targetUserId)).toList();
        long enabledOthers = otherUserIds.isEmpty() ? 0
                : adminUserMapper.selectCount(new LambdaQueryWrapper<AdminUser>()
                        .in(AdminUser::getId, otherUserIds)
                        .eq(AdminUser::getStatus, 1));
        if (enabledOthers == 0) {
            throw new BusinessException(AdminResultCode.LAST_SUPER_ADMIN);
        }
    }

    private Long lockSuperAdminRole() {
        // 首次快照读取前锁定同一角色行，串行化跨实例的超管变更并避免读取旧快照。
        SysRole supportRole = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, SUPER_ADMIN_ROLE_CODE).last("FOR UPDATE"));
        return supportRole == null ? null : supportRole.getId();
    }

    private boolean hasRole(Long userId, Long roleId) {
        return sysUserRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, roleId)) > 0;
    }

    private List<AdminUserManageVO.SimpleRole> loadRoles(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
        );
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<SysRole> roles = sysRoleMapper.selectByIds(roleIds);
        return roles.stream().map(this::toSimpleRole).collect(Collectors.toList());
    }

    private Map<Long, List<AdminUserManageVO.SimpleRole>> batchLoadRoles(List<Long> userIds) {
        // 查询所有用户-角色关联
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds)
        );
        if (userRoles.isEmpty()) {
            return Collections.emptyMap();
        }

        // 查询角色详情
        Set<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());
        List<SysRole> roles = sysRoleMapper.selectByIds(roleIds);
        Map<Long, SysRole> roleMap = roles.stream()
                .collect(Collectors.toMap(SysRole::getId, r -> r));

        // 按用户分组
        Map<Long, List<AdminUserManageVO.SimpleRole>> result = new HashMap<>();
        for (SysUserRole ur : userRoles) {
            SysRole role = roleMap.get(ur.getRoleId());
            if (role != null) {
                result.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>())
                        .add(toSimpleRole(role));
            }
        }
        return result;
    }

    private AdminUserManageVO.SimpleRole toSimpleRole(SysRole role) {
        AdminUserManageVO.SimpleRole sr = new AdminUserManageVO.SimpleRole();
        sr.setId(role.getId());
        sr.setName(role.getName());
        sr.setCode(role.getCode());
        return sr;
    }

    private AdminUserManageVO toVOWithoutRoles(AdminUser user) {
        AdminUserManageVO vo = new AdminUserManageVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}
