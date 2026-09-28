package com.shadow.backend.auth.controller;

import com.shadow.backend.auth.constant.SmsScene;
import com.shadow.backend.auth.dto.LoginResponse;
import com.shadow.backend.auth.dto.PasswordLoginRequest;
import com.shadow.backend.auth.dto.RefreshTokenRequest;
import com.shadow.backend.auth.dto.RegisterRequest;
import com.shadow.backend.auth.dto.RegisterResponse;
import com.shadow.backend.auth.dto.ResubmitRequest;
import com.shadow.backend.auth.dto.ResetPasswordRequest;
import com.shadow.backend.auth.dto.SendCodeRequest;
import com.shadow.backend.auth.dto.SmsLoginRequest;
import com.shadow.backend.account.response.AuthResultCode;
import com.shadow.backend.auth.service.AuthService;
import com.shadow.backend.auth.service.SmsService;
import com.shadow.backend.auth.vo.AuditStatusVO;
import com.shadow.backend.auth.vo.RefreshTokenResponse;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.common.response.Result;
import com.shadow.backend.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SmsService smsService;

    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        SmsScene scene = SmsScene.fromName(request.getScene());
        if (scene == null) {
            throw new BusinessException(AuthResultCode.SMS_SCENE_INVALID);
        }
        smsService.sendCode(request.getPhone(), scene);
        return Result.success();
    }

    @PostMapping("/login/password")
    public Result<LoginResponse> loginByPassword(@Valid @RequestBody PasswordLoginRequest request) {
        return Result.success(authService.loginByPassword(request));
    }

    @PostMapping("/login/sms")
    public Result<LoginResponse> loginBySms(@Valid @RequestBody SmsLoginRequest request) {
        return Result.success(authService.loginBySms(request));
    }

    @PostMapping("/register")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @GetMapping("/audit-status")
    public Result<AuditStatusVO> getAuditStatus(@RequestParam String phone) {
        return Result.success(authService.getAuditStatus(phone));
    }

    @PostMapping("/resubmit")
    public Result<RegisterResponse> resubmit(@Valid @RequestBody ResubmitRequest request) {
        return Result.success(authService.resubmit(request));
    }

    @PostMapping("/refresh")
    public Result<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refresh(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.getCurrentUser());
    }

    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.success();
    }
}
