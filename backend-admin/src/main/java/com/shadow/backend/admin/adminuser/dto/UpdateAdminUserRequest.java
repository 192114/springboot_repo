package com.shadow.backend.admin.adminuser.dto;

import lombok.Data;

@Data
public class UpdateAdminUserRequest {

    private String nickname;

    private String email;

    private Integer status;
}
