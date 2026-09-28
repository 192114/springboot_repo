package com.shadow.backend.admin.auth.dto;

import com.shadow.backend.admin.auth.vo.AdminUserVO;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminLoginResponse {

    /** 单 Token（无 Refresh Token，通过滑动续期保持会话） */
    private String token;

    private AdminUserVO user;
}
