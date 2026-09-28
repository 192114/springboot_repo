package com.shadow.backend.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import com.shadow.backend.auth.constant.SmsScene;
import com.shadow.backend.auth.dto.LoginResponse;
import com.shadow.backend.auth.dto.PasswordLoginRequest;
import com.shadow.backend.auth.dto.RefreshTokenRequest;
import com.shadow.backend.auth.dto.RegisterRequest;
import com.shadow.backend.auth.dto.RegisterResponse;
import com.shadow.backend.auth.dto.ResubmitRequest;
import com.shadow.backend.auth.dto.ResetPasswordRequest;
import com.shadow.backend.auth.dto.SmsLoginRequest;
import com.shadow.backend.account.response.AuthResultCode;
import com.shadow.backend.auth.service.AuthService;
import com.shadow.backend.auth.service.SmsService;
import com.shadow.backend.account.session.service.TokenService;
import com.shadow.backend.auth.vo.AuditStatusVO;
import com.shadow.backend.auth.vo.RefreshTokenResponse;
import com.shadow.backend.account.session.vo.TokenPair;
import com.shadow.backend.user.constant.AuditStatus;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.common.util.LoginAttemptGuard;
import com.shadow.backend.auth.util.LoginUserUtil;
import com.shadow.backend.common.util.PasswordUtil;
import com.shadow.backend.common.util.PhoneMaskUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import com.shadow.backend.user.entity.User;
import com.shadow.backend.user.mapper.UserMapper;
import com.shadow.backend.user.response.UserResultCode;
import com.shadow.backend.user.service.UserService;
import com.shadow.backend.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String LOGIN_ATTEMPT_SCENE = "app-password";

    private final UserMapper userMapper;
    private final UserService userService;
    private final PasswordUtil passwordUtil;
    private final SmsService smsService;
    private final TokenService tokenService;
    private final LoginAttemptGuard loginAttemptGuard;
    private final StringRedisTemplate redisTemplate;

    @Override
    public LoginResponse loginByPassword(PasswordLoginRequest req) {
        if (loginAttemptGuard.isLocked(LOGIN_ATTEMPT_SCENE, req.getPhone())) {
            throw new BusinessException(AuthResultCode.LOGIN_LOCKED);
        }

        User user = userService.getByPhone(req.getPhone());
        if (user == null || !passwordUtil.verify(req.getPassword(), user.getPassword())) {
            loginAttemptGuard.onLoginFailed(LOGIN_ATTEMPT_SCENE, req.getPhone());
            throw new BusinessException(UserResultCode.LOGIN_FAILED);
        }
        loginAttemptGuard.onLoginSucceeded(LOGIN_ATTEMPT_SCENE, req.getPhone());

        checkAuditStatus(user);
        checkUserStatus(user);
        return buildLoginResponse(user);
    }

    @Override
    public LoginResponse loginBySms(SmsLoginRequest req) {
        smsService.verifyCode(req.getPhone(), SmsScene.LOGIN, req.getCode());
        User user = userService.getByPhone(req.getPhone());
        if (user == null) {
            throw new BusinessException(AuthResultCode.PHONE_NOT_REGISTERED);
        }
        checkAuditStatus(user);
        checkUserStatus(user);
        return buildLoginResponse(user);
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        smsService.verifyCode(req.getPhone(), SmsScene.REGISTER, req.getCode());

        User existing = userService.getByPhone(req.getPhone());
        if (existing != null) {
            throw new BusinessException(AuthResultCode.PHONE_ALREADY_REGISTERED);
        }

        User user = new User();
        user.setPhone(req.getPhone());
        user.setPassword(passwordUtil.hash(req.getPassword()));
        user.setNickname(StringUtils.hasText(req.getNickname()) ? req.getNickname() : "用户" + PhoneMaskUtil.mask(req.getPhone()));
        user.setStatus(1);
        user.setAuditStatus(AuditStatus.PENDING.getValue());
        userMapper.insert(user);

        log.info("用户注册成功: phone={}", PhoneMaskUtil.mask(req.getPhone()));
        return new RegisterResponse(userService.getById(user.getId()));
    }

    @Override
    public AuditStatusVO getAuditStatus(String phone) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                "audit:query:limit:" + phone, "1", Duration.ofSeconds(60));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(AuthResultCode.AUDIT_QUERY_TOO_FREQUENT);
        }
        User user = userService.getByPhone(phone);
        if (user == null) {
            throw new BusinessException(AuthResultCode.PHONE_NOT_REGISTERED);
        }
        return new AuditStatusVO(user.getAuditStatus(), PhoneMaskUtil.mask(user.getPhone()));
    }

    @Override
    @Transactional
    public RegisterResponse resubmit(ResubmitRequest req) {
        smsService.verifyCode(req.getPhone(), SmsScene.REGISTER, req.getCode());

        User user = userService.getByPhone(req.getPhone());
        if (user == null) {
            throw new BusinessException(AuthResultCode.PHONE_NOT_REGISTERED);
        }
        if (user.getAuditStatus() == null || user.getAuditStatus() != AuditStatus.REJECTED.getValue()) {
            throw new BusinessException(AuthResultCode.RESUBMIT_NOT_REJECTED);
        }

        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .eq(User::getAuditStatus, AuditStatus.REJECTED.getValue())
                .set(User::getPassword, passwordUtil.hash(req.getPassword()))
                .set(StringUtils.hasText(req.getNickname()), User::getNickname, req.getNickname())
                .set(User::getAuditStatus, AuditStatus.PENDING.getValue())
                .set(User::getAuditRemark, null)
                .set(User::getAuditTime, null)
                .set(User::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(AuthResultCode.RESUBMIT_NOT_REJECTED);
        }

        log.info("用户重新提交审核: phone={}", PhoneMaskUtil.mask(req.getPhone()));
        return new RegisterResponse(userService.getById(user.getId()));
    }

    @Override
    public RefreshTokenResponse refresh(RefreshTokenRequest req) {
        // 刷新前复核账号状态：被禁用或审核未通过的账号 Refresh Token 直接失效
        Long userId = tokenService.getUserIdByRefreshToken(req.getRefreshToken());
        if (userId == null) {
            throw new BusinessException(AuthResultCode.REFRESH_TOKEN_INVALID);
        }
        UserVO user = userService.getById(userId);
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(UserResultCode.USER_DISABLED);
        }
        AuditStatus auditStatus = AuditStatus.fromValue(user.getAuditStatus());
        if (auditStatus == AuditStatus.PENDING || auditStatus == AuditStatus.REJECTED) {
            throw new BusinessException(AuthResultCode.REFRESH_TOKEN_INVALID);
        }

        TokenPair tokenPair = tokenService.refreshToken(req.getRefreshToken());
        return new RefreshTokenResponse(tokenPair.getAccessToken(), tokenPair.getRefreshToken());
    }

    @Override
    public void logout() {
        tokenService.removeTokens(tokenService.getCurrentDeviceRefreshToken());
    }

    @Override
    public UserVO getCurrentUser() {
        return userService.getById(LoginUserUtil.currentUserId());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        smsService.verifyCode(req.getPhone(), SmsScene.RESET_PASSWORD, req.getCode());
        User user = userService.getByPhone(req.getPhone());
        if (user == null) {
            throw new BusinessException(AuthResultCode.PHONE_NOT_REGISTERED);
        }
        user.setPassword(passwordUtil.hash(req.getNewPassword()));
        userMapper.updateById(user);
        // 重置密码后撤销该用户全部会话，防止旧凭据继续使用
        tokenService.revokeUserTokens(user.getId());
        log.info("密码重置成功: phone={}", PhoneMaskUtil.mask(req.getPhone()));
    }

    private void checkAuditStatus(User user) {
        AuditStatus auditStatus = AuditStatus.fromValue(user.getAuditStatus());
        if (auditStatus == null) {
            return;
        }
        if (auditStatus == AuditStatus.PENDING) {
            throw new BusinessException(AuthResultCode.USER_AUDIT_PENDING);
        }
        if (auditStatus == AuditStatus.REJECTED) {
            String msg = user.getAuditRemark() != null
                    ? AuthResultCode.USER_AUDIT_REJECTED.getMsg() + "：" + user.getAuditRemark()
                    : AuthResultCode.USER_AUDIT_REJECTED.getMsg();
            throw new BusinessException(AuthResultCode.USER_AUDIT_REJECTED, msg);
        }
    }

    private void checkUserStatus(User user) {
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(UserResultCode.USER_DISABLED);
        }
    }

    private LoginResponse buildLoginResponse(User user) {
        TokenPair tokenPair = tokenService.createTokens(user.getId());
        UserVO userVO = userService.getById(user.getId());
        return new LoginResponse(tokenPair.getAccessToken(), tokenPair.getRefreshToken(), userVO);
    }

}
