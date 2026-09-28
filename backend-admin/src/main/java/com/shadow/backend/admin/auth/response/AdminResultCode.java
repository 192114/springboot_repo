package com.shadow.backend.admin.auth.response;

import com.shadow.backend.common.response.IResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AdminResultCode implements IResultCode {

    LOGIN_FAILED(20001, "用户名或密码错误"),
    ADMIN_DISABLED(20002, "账号已被禁用"),
    ADMIN_NOT_FOUND(20003, "管理员账号不存在"),
    LOGIN_LOCKED(20004, "登录失败次数过多，请15分钟后再试"),

    // Menu
    MENU_NOT_FOUND(20101, "菜单不存在"),
    MENU_HAS_CHILDREN(20102, "存在子菜单，无法删除"),
    MENU_PARENT_NOT_FOUND(20103, "父级菜单不存在"),
    MENU_INVALID_PARENT(20104, "父级菜单不能为自身或其子菜单"),
    MENU_PARENT_TYPE_INVALID(20105, "按钮类型菜单不能作为父级"),

    // Role
    ROLE_NOT_FOUND(20201, "角色不存在"),
    ROLE_CODE_EXISTS(20202, "角色编码已存在"),
    ROLE_IN_USE(20203, "角色已分配给用户，无法删除"),
    ROLE_ASSIGNMENT_INVALID(20204, "包含无效或已停用的角色"),
    ROLE_ASSIGNMENT_FORBIDDEN(20205, "不能分配超出自身权限范围的角色"),
    MENU_ASSIGNMENT_INVALID(20206, "包含无效的菜单ID"),

    // Admin User
    ADMIN_USERNAME_EXISTS(20301, "用户名已存在"),
    CANNOT_DELETE_SELF(20302, "不能删除当前登录账号"),
    CANNOT_DISABLE_SELF(20303, "不能禁用当前登录账号"),
    CANNOT_MODIFY_OWN_ROLES(20304, "不能修改自己的角色"),
    LAST_SUPER_ADMIN(20305, "系统至少保留一名启用的超级管理员");

    private final int code;
    private final String msg;
}
