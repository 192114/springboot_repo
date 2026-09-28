package com.shadow.backend.auth.service;

import com.shadow.backend.auth.dto.LoginResponse;
import com.shadow.backend.auth.dto.PasswordLoginRequest;
import com.shadow.backend.auth.dto.RegisterRequest;
import com.shadow.backend.auth.dto.RegisterResponse;
import com.shadow.backend.auth.dto.ResubmitRequest;
import com.shadow.backend.auth.dto.ResetPasswordRequest;
import com.shadow.backend.auth.dto.SmsLoginRequest;
import com.shadow.backend.auth.dto.RefreshTokenRequest;
import com.shadow.backend.auth.vo.AuditStatusVO;
import com.shadow.backend.auth.vo.RefreshTokenResponse;
import com.shadow.backend.user.vo.UserVO;

public interface AuthService {

    LoginResponse loginByPassword(PasswordLoginRequest req);

    LoginResponse loginBySms(SmsLoginRequest req);

    RegisterResponse register(RegisterRequest req);

    RefreshTokenResponse refresh(RefreshTokenRequest req);

    void logout();

    UserVO getCurrentUser();

    void resetPassword(ResetPasswordRequest req);

    AuditStatusVO getAuditStatus(String phone);

    RegisterResponse resubmit(ResubmitRequest req);
}
