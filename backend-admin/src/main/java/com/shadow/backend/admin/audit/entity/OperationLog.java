package com.shadow.backend.admin.audit.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long adminId;

    private String username;

    private String method;

    private String uri;

    private String params;

    private Integer resultCode;

    private String ip;

    private Long costMs;

    private String traceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
