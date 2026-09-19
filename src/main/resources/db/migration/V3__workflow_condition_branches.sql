ALTER TABLE `workflow_edge`
  ADD COLUMN `branch_type` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT '条件分支类型 NORMAL/TRUE/FALSE' AFTER `target_node_id`;

UPDATE `workflow_edge` e
JOIN `workflow_node` n ON n.id=e.source_node_id AND n.node_type='CONDITION'
JOIN (
  SELECT source_node_id, MIN(id) AS true_edge_id
  FROM `workflow_edge`
  GROUP BY source_node_id
) first_edge ON first_edge.source_node_id=e.source_node_id
SET e.branch_type=CASE WHEN e.id=first_edge.true_edge_id THEN 'TRUE' ELSE 'FALSE' END;
