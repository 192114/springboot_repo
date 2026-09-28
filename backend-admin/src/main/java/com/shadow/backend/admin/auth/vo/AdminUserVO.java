package com.shadow.backend.admin.auth.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
