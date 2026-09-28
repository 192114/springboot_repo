package com.shadow.backend.admin.adminuser.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAdminUserRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String nickname;

    private String email;

    private Integer status = 1;
}
