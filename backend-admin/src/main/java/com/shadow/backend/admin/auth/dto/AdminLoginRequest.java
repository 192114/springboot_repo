package com.shadow.backend.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminLoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 32, message = "用户名最多32个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度为6-64位")
    private String password;
}
