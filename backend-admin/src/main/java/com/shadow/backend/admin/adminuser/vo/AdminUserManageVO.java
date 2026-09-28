package com.shadow.backend.admin.adminuser.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminUserManageVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 角色列表 */
    private List<SimpleRole> roles;

    @Data
    public static class SimpleRole {
        private Long id;
        private String name;
        private String code;
    }
}
