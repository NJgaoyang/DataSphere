-- Project workflow full-chain bindings and rerun runtime hardening.
ALTER TABLE `integration_task`
  ADD COLUMN `project_id` bigint DEFAULT NULL COMMENT '所属开发项目ID' AFTER `id`,
  ADD KEY `idx_integration_task_project` (`project_id`),
  ADD CONSTRAINT `fk_integration_task_project` FOREIGN KEY (`project_id`) REFERENCES `dev_project` (`id`) ON DELETE SET NULL;

CREATE TABLE `integration_task_downstream` (
  `task_id` bigint NOT NULL COMMENT '离线同步任务ID',
  `file_id` bigint NOT NULL COMMENT '显式绑定的下游开发任务ID',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`task_id`,`file_id`),
  KEY `idx_integration_downstream_file` (`file_id`),
  CONSTRAINT `fk_integration_downstream_task` FOREIGN KEY (`task_id`) REFERENCES `integration_task` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_integration_downstream_file` FOREIGN KEY (`file_id`) REFERENCES `dev_file` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='离线同步到开发任务的显式依赖';

ALTER TABLE `workflow_rerun_batch`
  MODIFY COLUMN `source_file_id` bigint DEFAULT NULL,
  ADD COLUMN `source_integration_task_id` bigint DEFAULT NULL AFTER `source_file_id`,
  ADD COLUMN `parent_batch_id` bigint DEFAULT NULL AFTER `source_integration_task_id`,
  ADD COLUMN `cancel_requested` tinyint(1) NOT NULL DEFAULT '0' AFTER `status`,
  ADD COLUMN `source_status` varchar(32) DEFAULT NULL AFTER `cancel_requested`,
  ADD COLUMN `source_execution_id` varchar(128) DEFAULT NULL AFTER `source_status`,
  ADD COLUMN `source_error_message` text DEFAULT NULL AFTER `source_execution_id`,
  ADD KEY `idx_wf_rerun_active_source` (`project_id`,`source_file_id`,`source_integration_task_id`,`business_date`,`status`),
  ADD KEY `idx_wf_rerun_parent` (`parent_batch_id`),
  ADD CONSTRAINT `fk_wf_rerun_source_file` FOREIGN KEY (`source_file_id`) REFERENCES `dev_file` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_wf_rerun_source_integration` FOREIGN KEY (`source_integration_task_id`) REFERENCES `integration_task` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_wf_rerun_parent` FOREIGN KEY (`parent_batch_id`) REFERENCES `workflow_rerun_batch` (`id`) ON DELETE SET NULL;

ALTER TABLE `workflow_rerun_task`
  ADD COLUMN `output_log` longtext DEFAULT NULL AFTER `error_message`,
  ADD KEY `idx_wf_rerun_task_status` (`batch_id`,`status`),
  ADD CONSTRAINT `fk_wf_rerun_task_file` FOREIGN KEY (`file_id`) REFERENCES `dev_file` (`id`) ON DELETE SET NULL;
