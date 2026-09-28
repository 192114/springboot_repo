package com.shadow.backend.user.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuditStatus {

    PENDING(0, "待审核"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已拒绝");

    private final int value;
    private final String desc;

    public static AuditStatus fromValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (AuditStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return null;
    }
}
