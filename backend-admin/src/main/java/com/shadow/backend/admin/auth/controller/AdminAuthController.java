package com.shadow.backend.admin.auth.controller;

import com.shadow.backend.admin.auth.dto.AdminLoginRequest;
import com.shadow.backend.admin.auth.dto.AdminLoginResponse;
import com.shadow.backend.admin.auth.service.AdminAuthService;
import com.shadow.backend.admin.auth.vo.AdminUserVO;
import com.shadow.backend.common.response.Result;
import com.shadow.backend.admin.auth.util.StpAdminUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public Result<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.success(adminAuthService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        adminAuthService.logout();
        return Result.success();
    }

    @GetMapping("/me")
    public Result<AdminUserVO> me() {
        return Result.success(adminAuthService.getCurrentAdmin());
    }

    /** 获取当前登录管理员的权限标识列表(用于前端按钮级权限控制) */
    @GetMapping("/permissions")
    public Result<List<String>> permissions() {
        return Result.success(StpAdminUtil.getPermissionList());
    }
}
