package com.shadow.backend.admin.auth.util;

import cn.dev33.satoken.stp.StpLogic;

import java.util.List;

/**
 * Admin 用户认证工具类（accountType = "admin"）
 * <p>
 * 与 {@link com.shadow.backend.account.session.util.StpAppUtil} 完全隔离，Redis key 前缀、token 体系互不干扰。
 * Admin 采用单 Token + 滑动续期模式，无需 Refresh Token。
 */
public class StpAdminUtil {

    public static StpLogic stpLogic = new StpLogic("admin");

    /** Admin Token 绝对超时时间（2 小时） */
    public static final long TOKEN_TIMEOUT = 2 * 60 * 60;

    /** 剩余时间低于此阈值（30 分钟）时触发续期 */
    public static final long RENEW_THRESHOLD = 30 * 60;

    public static void login(Object id) {
        stpLogic.login(id);
    }

    public static void logout() {
        stpLogic.logout();
    }

    public static void checkLogin() {
        stpLogic.checkLogin();
    }

    public static boolean isLogin() {
        return stpLogic.isLogin();
    }

    public static Object getLoginId() {
        return stpLogic.getLoginId();
    }

    public static long getLoginIdAsLong() {
        return stpLogic.getLoginIdAsLong();
    }

    public static String getTokenValue() {
        return stpLogic.getTokenValue();
    }

    public static long getTokenTimeout() {
        return stpLogic.getTokenTimeout();
    }

    public static void renewTimeout(long timeout) {
        stpLogic.renewTimeout(timeout);
    }

    /**
     * 滑动续期：剩余时间低于阈值时自动续期至 TOKEN_TIMEOUT
     */
    public static void autoRenew() {
        long remaining = getTokenTimeout();
        if (remaining > 0 && remaining < RENEW_THRESHOLD) {
            renewTimeout(TOKEN_TIMEOUT);
        }
    }

    // ==================== 权限校验 ====================

    /** 获取当前登录管理员的权限列表 */
    public static List<String> getPermissionList() {
        return stpLogic.getPermissionList();
    }

    /** 校验当前登录管理员是否拥有指定权限 */
    public static void checkPermission(String permission) {
        stpLogic.checkPermission(permission);
    }

    /** 获取当前登录管理员的角色编码列表 */
    public static List<String> getRoleList() {
        return stpLogic.getRoleList();
    }

    /** 校验当前登录管理员是否拥有指定角色 */
    public static void checkRole(String role) {
        stpLogic.checkRole(role);
    }
}
