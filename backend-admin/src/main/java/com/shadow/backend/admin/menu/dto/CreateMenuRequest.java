package com.shadow.backend.admin.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateMenuRequest {

    private Long parentId = 0L;

    @NotBlank(message = "菜单名称不能为空")
    private String name;

    @NotNull(message = "菜单类型不能为空")
    private Integer type;

    private String path;

    private String icon;

    private Integer sortOrder = 0;

    private String permission;

    private Integer visible = 1;

    private Integer status = 1;
}
