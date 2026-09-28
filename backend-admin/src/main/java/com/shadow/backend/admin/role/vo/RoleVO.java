package com.shadow.backend.admin.role.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RoleVO {

    private Long id;
    private String name;
    private String code;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 角色已关联的菜单ID列表(编辑时回显) */
    private List<Long> menuIds;
}
