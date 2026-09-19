package com.company.platform.system;

import com.company.platform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/system")
public class AccessController {
    private final AccessService service;
    private final AuditService audit;
    public AccessController(AccessService service, AuditService audit) { this.service = service; this.audit = audit; }
    @GetMapping("/users") public Result<List<UserView>> users(@RequestParam(required = false) String keyword) { return Result.ok(service.users(keyword)); }
    @PostMapping("/users") public Result<UserView> createUser(@Valid @RequestBody AccessRequests.UserRequest request, HttpServletRequest http) { return Result.ok(service.createUser(request, operator(http)), "用户已创建"); }
    @PutMapping("/users/{id}") public Result<UserView> updateUser(@PathVariable long id, @Valid @RequestBody AccessRequests.UserUpdateRequest request, HttpServletRequest http) { return Result.ok(service.updateUser(id, request, operator(http)), "用户已更新"); }
    @DeleteMapping("/users/{id}") public Result<Void> deleteUser(@PathVariable long id, HttpServletRequest http) { service.deleteUser(id, operator(http)); return Result.ok(null, "用户已删除"); }
    @PostMapping("/users/{id}/disable") public Result<UserView> disableUser(@PathVariable long id, HttpServletRequest http) { return Result.ok(service.setUserStatus(id, "DISABLED", operator(http)), "用户已禁用"); }
    @PostMapping("/users/{id}/enable") public Result<UserView> enableUser(@PathVariable long id, HttpServletRequest http) { return Result.ok(service.setUserStatus(id, "ACTIVE", operator(http)), "用户已启用"); }
    @PostMapping("/users/{id}/reset-password") public Result<Void> resetPassword(@PathVariable long id, @Valid @RequestBody AccessRequests.ResetPasswordRequest request, HttpServletRequest http) { service.resetPassword(id, request, operator(http)); return Result.ok(null, "密码已重置"); }
    @PostMapping("/users/{id}/force-logout") public Result<Void> forceLogout(@PathVariable long id, HttpServletRequest http) { service.forceLogout(id, operator(http)); return Result.ok(null, "用户已强制下线"); }
    @GetMapping("/users/{id}/permissions") public Result<Set<String>> permissions(@PathVariable long id) { return Result.ok(service.permissions(id)); }
    @PutMapping("/users/{id}/permissions") public Result<Set<String>> setPermissions(@PathVariable long id, @RequestBody Map<String, Set<String>> request, HttpServletRequest http) { return Result.ok(service.setPermissions(id, request.getOrDefault("permissions", Set.of()), operator(http)), "用户权限已更新"); }
    @GetMapping("/roles") public Result<List<RoleView>> roles() { return Result.ok(service.roles()); }
    @PostMapping("/roles") public Result<RoleView> createRole(@Valid @RequestBody AccessRequests.RoleRequest request, HttpServletRequest http) { return Result.ok(service.createRole(request, operator(http)), "角色已创建"); }
    @PostMapping("/roles/{roleId}/permissions") public Result<RoleView> grant(@PathVariable long roleId, @Valid @RequestBody AccessRequests.PermissionRequest request, HttpServletRequest http) { return Result.ok(service.grant(roleId, request, operator(http)), "权限已授予"); }
    @PutMapping("/roles/{roleId}/permissions") public Result<RoleView> setRolePermissions(@PathVariable long roleId, @RequestBody Map<String, Set<String>> request, HttpServletRequest http) { return Result.ok(service.setRolePermissions(roleId, request.getOrDefault("permissions", Set.of()), operator(http)), "角色权限已更新"); }
    @PostMapping("/projects/{projectId}/permissions") public Result<String> grantProject(@PathVariable long projectId, @Valid @RequestBody AccessRequests.PermissionBindingRequest request, HttpServletRequest http) { return Result.ok(service.grantProjectPermission(projectId, request, operator(http)), "项目权限已授予"); }
    @PostMapping("/data-sources/{dataSourceId}/permissions") public Result<String> grantDatasource(@PathVariable long dataSourceId, @Valid @RequestBody AccessRequests.PermissionBindingRequest request, HttpServletRequest http) { return Result.ok(service.grantDatasourcePermission(dataSourceId, request, operator(http)), "数据源权限已授予"); }
    @GetMapping("/data-source-permissions") public Result<List<AccessService.DataSourcePermissionView>> datasourcePermissions() { return Result.ok(service.datasourcePermissions()); }
    @DeleteMapping("/data-sources/{dataSourceId}/permissions/{userId}/{permissionCode}") public Result<Void> revokeDatasource(@PathVariable long dataSourceId, @PathVariable long userId, @PathVariable String permissionCode, HttpServletRequest http) { service.revokeDatasourcePermission(dataSourceId, userId, permissionCode, operator(http)); return Result.ok(null, "数据源权限已撤销"); }
    @GetMapping("/alert-channels") public Result<List<AlertChannelView>> channels() { return Result.ok(service.channels()); }
    @PostMapping("/alert-channels") public Result<AlertChannelView> createChannel(@Valid @RequestBody AccessRequests.AlertChannelRequest request, HttpServletRequest http) { return Result.ok(service.createChannel(request, operator(http)), "告警渠道已创建"); }
    @GetMapping("/audit-logs") public Result<AuditService.Page> auditLogs(@RequestParam(required=false) String action,@RequestParam(required=false) String operator,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="50") int pageSize) { return Result.ok(audit.page(action,operator,page,pageSize)); }
    private String operator(HttpServletRequest request) { Object value = request.getAttribute("platform.operator"); return value == null || String.valueOf(value).isBlank() ? "system" : String.valueOf(value); }
}
