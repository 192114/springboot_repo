package com.shadow.backend.admin.role.dto;

import lombok.Data;

import java.util.List;

@Data
public class AssignMenusRequest {

    private List<Long> menuIds;
}
