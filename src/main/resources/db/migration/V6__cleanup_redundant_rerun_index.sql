-- Remove the redundant V4 copy of the existing V2 (batch_id,status) index.
ALTER TABLE `workflow_rerun_task`
  DROP INDEX `idx_wf_rerun_task_status`;
