package com.shadow.backend.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;

    private String username;

    private String password;

    private String nickname;

    private String avatar;

    private String email;

    private Integer status;

    private Integer auditStatus;

    private String auditRemark;

    private java.time.LocalDateTime auditTime;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
