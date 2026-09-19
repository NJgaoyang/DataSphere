package com.company.platform.workflow;

public record WorkflowEdgeView(long id, long sourceNodeId, long targetNodeId, String branchType) {
    public WorkflowEdgeView(long id, long sourceNodeId, long targetNodeId) {
        this(id, sourceNodeId, targetNodeId, "NORMAL");
    }
    public WorkflowEdgeView {
        branchType = branchType == null || branchType.isBlank() ? "NORMAL" : branchType.trim().toUpperCase();
    }
}
