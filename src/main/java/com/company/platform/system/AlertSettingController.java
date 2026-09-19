package com.company.platform.system;

import com.company.platform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/alert-settings")
public class AlertSettingController {
    private final AlertSettingService service;
    public AlertSettingController(AlertSettingService service) { this.service = service; }

    @GetMapping public Result<List<AlertSettingView>> list() { return Result.ok(service.list()); }
    @PostMapping public Result<AlertSettingView> create(@Valid @RequestBody AlertSettingRequest request, HttpServletRequest http) { return Result.ok(service.create(request, operator(http)), "告警配置已创建"); }
    @PutMapping("/{id}") public Result<AlertSettingView> update(@PathVariable long id, @Valid @RequestBody AlertSettingRequest request, HttpServletRequest http) { return Result.ok(service.update(id, request, operator(http)), "告警配置已更新"); }
    @PostMapping("/{id}/enabled") public Result<AlertSettingView> enabled(@PathVariable long id, @RequestBody Map<String, Boolean> body, HttpServletRequest http) { return Result.ok(service.setEnabled(id, Boolean.TRUE.equals(body.get("enabled")), operator(http)), "告警状态已更新"); }
    @PostMapping("/{id}/test") public Result<String> test(@PathVariable long id, HttpServletRequest http) { return Result.ok(service.test(id, operator(http))); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable long id, HttpServletRequest http) { service.delete(id, operator(http)); return Result.ok(null, "告警配置已删除"); }
    private String operator(HttpServletRequest request) { Object value = request.getAttribute("platform.operator"); return value == null || String.valueOf(value).isBlank() ? "system" : String.valueOf(value); }
}
