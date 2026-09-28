package com.shadow.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Size(max = 64, message = "昵称长度不能超过 64")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @Min(value = 0, message = "状态值无效")
    @Max(value = 1, message = "状态值无效")
    private Integer status;
}
