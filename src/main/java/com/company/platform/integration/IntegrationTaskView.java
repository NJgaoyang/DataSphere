package com.company.platform.integration;

import java.util.List;

public record IntegrationTaskView(long id, Long projectId, String name, String sourceType, String targetType,
                                  String syncMode, String status, String lifecycleStatus, String sourceConfigJson,
                                  String targetConfigJson, String transformConfigJson, String seatunnelConfig,
                                  List<IntegrationTableView> tables, List<Long> downstreamFileIds) {
    public IntegrationTaskView(long id, String name, String sourceType, String targetType,
                               String syncMode, String status, String lifecycleStatus, String sourceConfigJson,
                               String targetConfigJson, String transformConfigJson, String seatunnelConfig,
                               List<IntegrationTableView> tables) {
        this(id, null, name, sourceType, targetType, syncMode, status, lifecycleStatus, sourceConfigJson,
                targetConfigJson, transformConfigJson, seatunnelConfig, tables, List.of());
    }
    public IntegrationTaskView(long id, String name, String sourceType, String targetType,
                               String syncMode, String status, String sourceConfigJson,
                               String targetConfigJson, String transformConfigJson, String seatunnelConfig,
                               List<IntegrationTableView> tables) {
        this(id, null, name, sourceType, targetType, syncMode, status, "ONLINE", sourceConfigJson, targetConfigJson,
                transformConfigJson, seatunnelConfig, tables, List.of());
    }
    public IntegrationTaskView(long id, String name, String sourceType, String targetType,
                               String syncMode, String status, String seatunnelConfig) {
        this(id, null, name, sourceType, targetType, syncMode, status, "ONLINE", "{}", "{}", "{}", seatunnelConfig, List.of(), List.of());
    }
    public IntegrationTaskView(long id, String name, String sourceType, String targetType,
                               String syncMode, String status, String sourceConfigJson,
                               String targetConfigJson, String transformConfigJson, String seatunnelConfig) {
        this(id, null, name, sourceType, targetType, syncMode, status, "ONLINE", sourceConfigJson, targetConfigJson,
                transformConfigJson, seatunnelConfig, List.of(), List.of());
    }
}
