package com.shadow.backend.admin.menu.vo;

import lombok.Data;

import java.util.List;

@Data
public class MenuTreeVO {

    private Long id;
    private Long parentId;
    private String name;
    private Integer type;
    private String path;
    private String icon;
    private Integer sortOrder;
    private String permission;
    private Integer visible;
    private Integer status;
    private List<MenuTreeVO> children;
}
