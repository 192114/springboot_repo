package com.shadow.backend.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long id;
    private String phone;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private Integer status;
    private Integer auditStatus;
    private String auditRemark;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
