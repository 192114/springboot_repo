package com.shadow.backend.admin.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shadow.backend.admin.auth.dto.AdminLoginRequest;
import com.shadow.backend.admin.auth.dto.AdminLoginResponse;
import com.shadow.backend.admin.auth.entity.AdminUser;
import com.shadow.backend.admin.auth.mapper.AdminUserMapper;
import com.shadow.backend.admin.auth.response.AdminResultCode;
import com.shadow.backend.admin.auth.service.AdminAuthService;
import com.shadow.backend.admin.auth.vo.AdminUserVO;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.admin.auth.util.LoginAdminUtil;
import com.shadow.backend.common.util.LoginAttemptGuard;
import com.shadow.backend.common.util.PasswordUtil;
import com.shadow.backend.admin.auth.util.StpAdminUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final String LOGIN_ATTEMPT_SCENE = "admin-password";

    private final AdminUserMapper adminUserMapper;
    private final PasswordUtil passwordUtil;
    private final LoginAttemptGuard loginAttemptGuard;

    @Override
    public AdminLoginResponse login(AdminLoginRequest req) {
        if (loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, req.getUsername())) {
            throw new BusinessException(AdminResultCode.LOGIN_LOCKED);
        }

        AdminUser admin = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, req.getUsername())
        );

        if (admin == null || !passwordUtil.verify(req.getPassword(), admin.getPassword())) {
            loginAttemptGuard.onLoginFailed(LOGIN_ATTEMPT_SCENE, req.getUsername());
            throw new BusinessException(AdminResultCode.LOGIN_FAILED);
        }
        loginAttemptGuard.onLoginSucceeded(LOGIN_ATTEMPT_SCENE, req.getUsername());

        checkAdminStatus(admin);

        StpAdminUtil.login(admin.getId());
        String token = StpAdminUtil.getTokenValue();

        log.info("Admin 登录成功: username={}", req.getUsername());
        return new AdminLoginResponse(token, toVO(admin));
    }

    @Override
    public void logout() {
        StpAdminUtil.logout();
        log.info("Admin 退出登录");
    }

    @Override
    public AdminUserVO getCurrentAdmin() {
        Long adminId = LoginAdminUtil.currentAdminId();
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException(AdminResultCode.ADMIN_NOT_FOUND);
        }
        return toVO(admin);
    }

    private void checkAdminStatus(AdminUser admin) {
        if (admin.getStatus() != null && admin.getStatus() == 0) {
            throw new BusinessException(AdminResultCode.ADMIN_DISABLED);
        }
    }

    private AdminUserVO toVO(AdminUser admin) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(admin.getId());
        vo.setUsername(admin.getUsername());
        vo.setNickname(admin.getNickname());
        vo.setEmail(admin.getEmail());
        vo.setStatus(admin.getStatus());
        vo.setCreateTime(admin.getCreateTime());
        vo.setUpdateTime(admin.getUpdateTime());
        return vo;
    }
}
