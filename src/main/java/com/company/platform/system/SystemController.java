package com.company.platform.system;

import com.company.platform.common.Result;
import com.company.platform.config.DataSphereProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final DataSphereProperties properties;
    private final AuditService audit;
    public SystemController(DataSphereProperties properties, AuditService audit) { this.properties = properties; this.audit = audit; }
    @GetMapping("/features") public Result<Map<String, Boolean>> features() {
        return Result.ok(Map.of("realtime-sync", properties.getFeatures().isRealtimeSync()));
    }
    @GetMapping("/operation-logs") public Result<List<String>> operationLogs() { return Result.ok(audit.operationMessages(200)); }
}
