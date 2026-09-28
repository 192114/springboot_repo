package com.shadow.backend.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

@Data
public class AuditUserRequest {

    @NotNull(message = "审核状态不能为空")
    @Range(min = 1, max = 2, message = "审核状态只能为通过或拒绝")
    private Integer auditStatus;

    @Size(max = 255, message = "审核备注长度不能超过 255")
    private String auditRemark;
}
