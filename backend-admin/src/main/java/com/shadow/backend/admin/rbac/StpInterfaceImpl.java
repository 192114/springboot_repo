package com.shadow.backend.admin.rbac;

import cn.dev33.satoken.stp.StpInterface;
import com.shadow.backend.admin.menu.mapper.SysMenuMapper;
import com.shadow.backend.admin.role.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限/角色提供者
 * <p>
 * 通过 loginType 区分 App 和 Admin：
 * - "admin": 返回管理员的权限标识和角色编码
 * - 其他: 返回空列表
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMapper sysRoleMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (!"admin".equals(loginType) || loginId == null) {
            return List.of();
        }
        Long userId = Long.parseLong(loginId.toString());
        List<String> permissions = sysMenuMapper.selectPermissionsByUserId(userId);
        return permissions != null ? permissions : List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (!"admin".equals(loginType) || loginId == null) {
            return List.of();
        }
        Long userId = Long.parseLong(loginId.toString());
        List<String> roleCodes = sysRoleMapper.selectRoleCodesByUserId(userId);
        return roleCodes != null ? roleCodes : List.of();
    }
}
