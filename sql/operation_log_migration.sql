-- 可重复执行；在目标数据库中执行，不改动已有业务数据。
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
