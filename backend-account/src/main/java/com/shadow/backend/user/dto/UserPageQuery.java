package com.shadow.backend.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UserPageQuery {

    @Min(value = 1, message = "页码最小为 1")
    private long page = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private long size = 10;

    private String username;

    private Integer auditStatus;
}
