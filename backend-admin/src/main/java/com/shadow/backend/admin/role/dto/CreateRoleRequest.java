package com.shadow.backend.admin.role.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateRoleRequest {

    @NotBlank(message = "角色名称不能为空")
    private String name;

    @NotBlank(message = "角色编码不能为空")
    private String code;

    private Integer sortOrder = 0;

    private String remark;
}
