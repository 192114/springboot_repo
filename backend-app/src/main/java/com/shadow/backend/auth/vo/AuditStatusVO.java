package com.shadow.backend.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuditStatusVO {

    private Integer auditStatus;
    private String phone;
}
