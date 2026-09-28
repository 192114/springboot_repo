CREATE DATABASE IF NOT EXISTS backend DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE backend;

CREATE TABLE IF NOT EXISTS app_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone       VARCHAR(20)  NOT NULL COMMENT '手机号',
    username    VARCHAR(32)  DEFAULT NULL COMMENT '用户名',
    password    VARCHAR(255) NOT NULL COMMENT '密码（Argon2）',
    nickname    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    avatar      VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    email       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    audit_status TINYINT     NOT NULL DEFAULT 1 COMMENT '审核状态：0-待审核，1-已通过，2-已拒绝',
    audit_remark VARCHAR(255) DEFAULT NULL COMMENT '审核备注/驳回原因',
    audit_time   DATETIME    DEFAULT NULL COMMENT '审核时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone (phone),
    UNIQUE KEY uk_username (username),
    INDEX idx_audit_status (audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS app_sms_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone         VARCHAR(20)  NOT NULL COMMENT '手机号',
    scene         VARCHAR(32)  NOT NULL COMMENT '场景：LOGIN/REGISTER/RESET_PASSWORD',
    code          VARCHAR(6)   NOT NULL COMMENT '验证码',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-已发送，1-已验证，2-已过期',
    send_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    verified_time DATETIME     DEFAULT NULL COMMENT '验证时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_phone_scene (phone, scene),
    INDEX idx_send_time (send_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信验证码日志表';

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)  NOT NULL COMMENT '用户名',
    password    VARCHAR(255) NOT NULL COMMENT '密码（Argon2）',
    nickname    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    email       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员用户表';

-- ======================== RBAC 权限系统 ========================

CREATE TABLE IF NOT EXISTS sys_menu (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id   BIGINT       NOT NULL DEFAULT 0     COMMENT '父菜单ID, 0=根节点',
    name        VARCHAR(64)  NOT NULL               COMMENT '菜单名称',
    type        TINYINT      NOT NULL               COMMENT '类型: 1=目录 2=菜单 3=按钮',
    path        VARCHAR(128) DEFAULT NULL           COMMENT '路由路径',
    icon        VARCHAR(64)  DEFAULT NULL           COMMENT '图标(lucide名称)',
    sort_order  INT          NOT NULL DEFAULT 0     COMMENT '排序',
    permission  VARCHAR(128) DEFAULT NULL           COMMENT '权限标识如 user:create',
    visible     TINYINT      NOT NULL DEFAULT 1     COMMENT '是否可见: 0=隐藏 1=显示',
    status      TINYINT      NOT NULL DEFAULT 1     COMMENT '状态: 0=禁用 1=启用',
    deleted     TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';

CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  NOT NULL               COMMENT '角色名称',
    code        VARCHAR(64)  NOT NULL               COMMENT '角色编码',
    sort_order  INT          NOT NULL DEFAULT 0     COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1     COMMENT '状态: 0=禁用 1=启用',
    remark      VARCHAR(256) DEFAULT NULL           COMMENT '备注',
    deleted     TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_menu (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '管理员ID (sys_user.id)',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员角色关联表';

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    admin_id    BIGINT       NOT NULL COMMENT '操作管理员ID',
    username    VARCHAR(32)  DEFAULT NULL COMMENT '操作管理员用户名',
    method      VARCHAR(8)   NOT NULL COMMENT 'HTTP方法',
    uri         VARCHAR(255) NOT NULL COMMENT '请求路径',
    params      VARCHAR(1024) DEFAULT NULL COMMENT '请求参数JSON(脱敏)',
    result_code INT          DEFAULT NULL COMMENT '业务结果码',
    ip          VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
    cost_ms     BIGINT       DEFAULT NULL COMMENT '耗时毫秒',
    trace_id    VARCHAR(32)  DEFAULT NULL COMMENT '链路追踪ID',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    INDEX idx_admin_id (admin_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理端操作审计日志表';
