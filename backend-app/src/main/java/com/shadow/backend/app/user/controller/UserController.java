package com.shadow.backend.app.user.controller;

import com.shadow.backend.common.response.Result;
import com.shadow.backend.auth.util.LoginUserUtil;
import com.shadow.backend.user.dto.ChangePasswordRequest;
import com.shadow.backend.user.dto.UpdateProfileRequest;
import com.shadow.backend.user.service.UserService;
import com.shadow.backend.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * App 用户自助 Controller（个人资料、修改密码）
 * <p>
 * 用户管理 CRUD 已迁移至 {@link com.shadow.backend.admin.user.controller.AdminUserController}
 */
@RestController
@RequestMapping("/api/app/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(userService.updateProfile(LoginUserUtil.currentUserId(), request));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(LoginUserUtil.currentUserId(), request);
        return Result.success();
    }
}
