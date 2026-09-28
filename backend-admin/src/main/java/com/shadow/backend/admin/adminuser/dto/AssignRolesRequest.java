package com.shadow.backend.admin.adminuser.dto;

import lombok.Data;

import java.util.List;

@Data
public class AssignRolesRequest {

    private List<Long> roleIds;
}
