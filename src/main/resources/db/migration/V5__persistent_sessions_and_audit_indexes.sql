CREATE TABLE `auth_session` (
  `token_hash` char(64) NOT NULL COMMENT '会话令牌SHA-256',
  `username` varchar(128) NOT NULL COMMENT '用户名',
  `expires_at` timestamp NOT NULL COMMENT '过期时间',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_seen_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近访问时间',
  PRIMARY KEY (`token_hash`),
  KEY `idx_auth_session_user` (`username`,`expires_at`),
  KEY `idx_auth_session_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台持久化登录会话';

ALTER TABLE `operation_audit`
  ADD KEY `idx_operation_audit_created` (`created_at`),
  ADD KEY `idx_operation_audit_operator_created` (`operator_name`,`created_at`),
  ADD KEY `idx_operation_audit_resource_created` (`resource_type`,`resource_id`,`created_at`),
  ADD KEY `idx_operation_audit_action_created` (`action`,`created_at`);

ALTER TABLE `operation_log`
  ADD KEY `idx_operation_log_created` (`created_at`),
  ADD KEY `idx_operation_log_operator_created` (`operator_name`,`created_at`);
