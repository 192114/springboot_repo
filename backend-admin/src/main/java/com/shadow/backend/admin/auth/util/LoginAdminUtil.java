package com.shadow.backend.admin.auth.util;

/**
 * Admin 登录用户工具类
 * <p>
 * 对应 {@link StpAdminUtil}（accountType = "admin"），与 {@link com.shadow.backend.auth.util.LoginUserUtil}（App 用户）隔离。
 */
public final class LoginAdminUtil {

    private LoginAdminUtil() {
    }

    public static Long currentAdminId() {
        return StpAdminUtil.getLoginIdAsLong();
    }

    public static boolean isAdminLogin() {
        return StpAdminUtil.isLogin();
    }

    /**
     * 撤销指定管理员的全部会话（禁用/删除管理员后调用，使其立即下线）。
     */
    public static void logoutAdmin(Long adminId) {
        StpAdminUtil.stpLogic.logout(adminId);
    }
}
