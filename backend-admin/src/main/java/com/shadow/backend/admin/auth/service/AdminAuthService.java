package com.shadow.backend.admin.auth.service;

import com.shadow.backend.admin.auth.dto.AdminLoginRequest;
import com.shadow.backend.admin.auth.dto.AdminLoginResponse;
import com.shadow.backend.admin.auth.vo.AdminUserVO;

public interface AdminAuthService {

    AdminLoginResponse login(AdminLoginRequest req);

    void logout();

    AdminUserVO getCurrentAdmin();
}
