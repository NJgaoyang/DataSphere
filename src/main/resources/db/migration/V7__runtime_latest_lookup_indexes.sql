-- Optimize bulk runtime lookups that select the latest row per task/file.
CREATE INDEX idx_integration_batch_task_id ON integration_batch(task_id, id);
CREATE INDEX idx_integration_instance_task_id ON integration_instance(task_id, id);
CREATE INDEX idx_dev_sched_exec_file_id ON dev_file_schedule_execution(file_id, id);
