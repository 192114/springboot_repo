package com.shadow.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 64, message = "昵称长度不能超过 64")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @Size(max = 255, message = "头像URL长度不能超过 255")
    private String avatar;
}
