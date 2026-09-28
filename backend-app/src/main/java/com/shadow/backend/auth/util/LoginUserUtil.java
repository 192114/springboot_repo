package com.shadow.backend.auth.util;

import com.shadow.backend.account.session.util.StpAppUtil;

public final class LoginUserUtil {

    private LoginUserUtil() {
    }

    public static Long currentUserId() {
        return StpAppUtil.getLoginIdAsLong();
    }

    public static boolean isLogin() {
        return StpAppUtil.isLogin();
    }
}
