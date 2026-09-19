package com.company.platform.system;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class AccessService {
    private final PlatformStore store;
    private final AuditService audit;
    private AuthService auth;

    public AccessService(PlatformStore store, AuditService audit) { this.store = store; this.audit = audit; }
    @Autowired public void setAuthService(AuthService auth) { this.auth = auth; }

    public static final Set<String> MODULES = Set.of("WORKBENCH", "METADATA", "DATA_INTEGRATION", "DATA_DEVELOPMENT", "WORKFLOW", "OPERATIONS", "METRICS", "DATA_ASSETS", "RELEASE", "SYSTEM_SETTINGS");
    public static final String PERMISSION_MARKER = "_CONFIGURED";
    public static final String DATA_DEVELOPMENT_PROJECT_ALL = "DATA_DEVELOPMENT_PROJECT_ALL";
    public static final Set<String> MODULE_PERMISSIONS = allPermissionCodes();

    public List<UserView> users() { return users(null); }
    public List<UserView> users(String keyword) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return store.users.values().stream()
                .filter(user -> query.isBlank() || user.username().toLowerCase().contains(query) || user.displayName().toLowerCase().contains(query))
                .toList();
    }

    public UserView createUser(AccessRequests.UserRequest request) { return createUser(request, "system"); }
    @Transactional
    public UserView createUser(AccessRequests.UserRequest request, String operator) {
        if (store.users.values().stream().anyMatch(user -> user.username().equalsIgnoreCase(request.username()))) throw new BadRequestException("用户名已存在");
        UserView user = new UserView(store.nextId(), request.username(), request.displayName(), normalizePhone(request.phone()), normalizeRole(request.roleCode()),
                normalizeStatus(request.status()), LocalDateTime.now(), PasswordHasher.hash(request.password()));
        store.persistUser(user);
        store.users.put(user.id(), user);
        store.persistUserPermissions(user.id(), defaultViewPermissions());
        audit.record("CREATE_USER", "USER", user.id(), user.username(), normalizeOperator(operator));
        return user;
    }

    public UserView updateUser(long id, AccessRequests.UserUpdateRequest request) { return updateUser(id, request, "system"); }
    @Transactional
    public UserView updateUser(long id, AccessRequests.UserUpdateRequest request, String operator) {
        UserView current = store.users.get(id);
        if (current == null) throw new NotFoundException("用户不存在：" + id);
        if (isBuiltInAdmin(current) && (!"ADMIN".equalsIgnoreCase(request.roleCode()) || "DISABLED".equalsIgnoreCase(request.status()))) {
            throw new BadRequestException("内置 admin 账号不能降级或禁用");
        }
        boolean duplicate = store.users.values().stream().anyMatch(user -> user.id() != id && user.username().equalsIgnoreCase(request.username()));
        if (duplicate) throw new BadRequestException("用户名已存在");
        String passwordHash = request.password() == null || request.password().isBlank() ? current.passwordHash() : PasswordHasher.hash(request.password());
        String roleCode = request.roleCode() == null || request.roleCode().isBlank() ? current.roleCode() : normalizeRole(request.roleCode());
        UserView updated = new UserView(id, request.username(), request.displayName(), normalizePhone(request.phone()), roleCode, normalizeStatus(request.status()), current.createdAt(), passwordHash);
        store.persistUser(updated);
        store.users.put(id, updated);
        if (auth != null && (!updated.username().equalsIgnoreCase(current.username()) || !Objects.equals(passwordHash, current.passwordHash())
                || !updated.roleCode().equalsIgnoreCase(current.roleCode()) || !updated.status().equalsIgnoreCase(current.status()))) {
            auth.invalidateUser(current.username());
            if (!updated.username().equalsIgnoreCase(current.username())) auth.invalidateUser(updated.username());
        }
        audit.record("UPDATE_USER", "USER", id, updated.username(), normalizeOperator(operator));
        return updated;
    }

    public void deleteUser(long id) { deleteUser(id, "system"); }
    @Transactional
    public void deleteUser(long id, String operator) {
        UserView current = store.users.get(id);
        if (current == null) throw new NotFoundException("用户不存在：" + id);
        if (isBuiltInAdmin(current)) throw new BadRequestException("内置 admin 账号不能删除");
        store.deleteUser(id);
        store.users.remove(id);
        if (auth != null) auth.invalidateUser(current.username());
        audit.record("DELETE_USER", "USER", id, current.username(), normalizeOperator(operator));
    }

    public UserView setUserStatus(long id, String status) { return setUserStatus(id, status, "system"); }
    @Transactional
    public UserView setUserStatus(long id, String status, String operator) {
        UserView current = store.users.get(id);
        if (current == null) throw new NotFoundException("用户不存在：" + id);
        if (isBuiltInAdmin(current) && "DISABLED".equalsIgnoreCase(status)) throw new BadRequestException("内置 admin 账号不能禁用");
        UserView updated = new UserView(id, current.username(), current.displayName(), current.phone(), current.roleCode(), normalizeStatus(status), current.createdAt(), current.passwordHash());
        store.persistUser(updated);
        store.users.put(id, updated);
        if (auth != null && !updated.status().equalsIgnoreCase(current.status())) auth.invalidateUser(updated.username());
        audit.record("UPDATE_USER_STATUS", "USER", id, updated.status(), normalizeOperator(operator));
        return updated;
    }

    public void resetPassword(long id, AccessRequests.ResetPasswordRequest request) { resetPassword(id, request, "system"); }
    @Transactional
    public void resetPassword(long id, AccessRequests.ResetPasswordRequest request, String operator) {
        UserView current = store.users.get(id);
        if (current == null) throw new NotFoundException("用户不存在：" + id);
        String password = request.newPassword() == null ? "" : request.newPassword();
        if (password.length() < 6) throw new BadRequestException("新密码至少需要 6 位");
        UserView updated = new UserView(id, current.username(), current.displayName(), current.phone(), current.roleCode(),
                current.status(), current.createdAt(), PasswordHasher.hash(password));
        store.persistUser(updated);
        store.users.put(id, updated);
        if (auth != null) auth.invalidateUser(updated.username());
        audit.record("RESET_USER_PASSWORD", "USER", id, updated.username(), normalizeOperator(operator));
    }

    public void forceLogout(long id) { forceLogout(id, "system"); }
    public void forceLogout(long id, String operator) {
        UserView current = store.users.get(id);
        if (current == null) throw new NotFoundException("用户不存在：" + id);
        if (auth != null) auth.invalidateUser(current.username());
        audit.record("FORCE_LOGOUT_USER", "USER", id, current.username(), normalizeOperator(operator));
    }

    public Set<String> permissions(long userId) {
        if (!store.users.containsKey(userId)) throw new NotFoundException("用户不存在：" + userId);
        return effectivePermissions(userId);
    }

    public Set<String> setPermissions(long userId, Set<String> requested) { return setPermissions(userId, requested, "system"); }
    @Transactional
    public Set<String> setPermissions(long userId, Set<String> requested, String operator) {
        if (!store.users.containsKey(userId)) throw new NotFoundException("用户不存在：" + userId);
        Set<String> permissions = new HashSet<>(requested == null ? Set.of() : requested);
        if (!MODULE_PERMISSIONS.containsAll(permissions)) throw new BadRequestException("包含不支持的模块权限");
        permissions.add(PERMISSION_MARKER);
        store.persistUserPermissions(userId, permissions);
        UserView user = store.users.get(userId);
        if (auth != null && user != null) auth.invalidateUser(user.username());
        audit.record("SET_USER_PERMISSIONS", "USER", userId, String.join(",", permissions), normalizeOperator(operator));
        return effectivePermissions(userId);
    }

    public List<RoleView> roles() { return store.roles.values().stream().toList(); }

    public RoleView createRole(AccessRequests.RoleRequest request) { return createRole(request, "system"); }
    @Transactional
    public RoleView createRole(AccessRequests.RoleRequest request, String operator) {
        String roleCode = request.roleCode().trim().toUpperCase();
        if (!roleCode.matches("[A-Z][A-Z0-9_]{1,63}")) throw new BadRequestException("角色编码仅支持大写字母、数字和下划线，且必须以字母开头");
        if (store.roles.values().stream().anyMatch(role -> role.roleCode().equalsIgnoreCase(roleCode))) throw new BadRequestException("角色编码已存在");
        RoleView role = new RoleView(store.nextId(), roleCode, request.roleName().trim(), new HashSet<>());
        store.persistRole(role);
        store.roles.put(role.id(), role);
        audit.record("CREATE_ROLE", "ROLE", role.id(), role.roleCode(), normalizeOperator(operator));
        return role;
    }

    public RoleView grant(long roleId, AccessRequests.PermissionRequest request) { return grant(roleId, request, "system"); }
    @Transactional
    public RoleView grant(long roleId, AccessRequests.PermissionRequest request, String operator) {
        RoleView current = store.roles.get(roleId);
        if (current == null) throw new NotFoundException("角色不存在：" + roleId);
        HashSet<String> permissions = new HashSet<>(current.permissions());
        permissions.add(request.permissionCode());
        RoleView updated = new RoleView(current.id(), current.roleCode(), current.roleName(), permissions);
        store.persistRole(updated);
        store.roles.put(roleId, updated);
        audit.record("GRANT_PERMISSION", "ROLE", roleId, request.permissionCode(), normalizeOperator(operator));
        return updated;
    }

    public RoleView setRolePermissions(long roleId, Set<String> requested) { return setRolePermissions(roleId, requested, "system"); }
    @Transactional
    public RoleView setRolePermissions(long roleId, Set<String> requested, String operator) {
        RoleView current = store.roles.get(roleId);
        if (current == null) throw new NotFoundException("角色不存在：" + roleId);
        Set<String> permissions = new HashSet<>(requested == null ? Set.of() : requested);
        if (!MODULE_PERMISSIONS.containsAll(permissions)) throw new BadRequestException("角色包含不支持的模块权限");
        RoleView updated = new RoleView(current.id(), current.roleCode(), current.roleName(), permissions);
        store.persistRole(updated);
        store.roles.put(roleId, updated);
        if (auth != null) store.users.values().stream()
                .filter(user -> user.roleCode().equalsIgnoreCase(current.roleCode()))
                .filter(user -> !store.userPermissions.getOrDefault(user.id(), Set.of()).contains(PERMISSION_MARKER))
                .forEach(user -> auth.invalidateUser(user.username()));
        audit.record("SET_ROLE_PERMISSIONS", "ROLE", roleId, String.join(",", permissions), normalizeOperator(operator));
        return updated;
    }

    public List<AlertChannelView> channels() { return store.alertChannels.values().stream().toList(); }

    public AlertChannelView createChannel(AccessRequests.AlertChannelRequest request) { return createChannel(request, "system"); }
    @Transactional
    public AlertChannelView createChannel(AccessRequests.AlertChannelRequest request, String operator) {
        AlertChannelView channel = new AlertChannelView(store.nextId(), request.name(), request.channelType(), request.configJson(), request.enabled());
        store.persistAlertChannel(channel);
        store.alertChannels.put(channel.id(), channel);
        audit.record("CREATE_ALERT_CHANNEL", "ALERT_CHANNEL", channel.id(), channel.name(), normalizeOperator(operator));
        return channel;
    }

    private String normalizeStatus(String status) { return "DISABLED".equalsIgnoreCase(status) ? "DISABLED" : "ACTIVE"; }
    private String normalizePhone(String phone) {
        String value = phone == null ? "" : phone.trim();
        if (!value.isBlank() && !value.matches("^\\+?[0-9 -]{6,20}$")) throw new BadRequestException("手机号格式不正确");
        return value;
    }
    private String normalizeRole(String roleCode) {
        String value = roleCode == null || roleCode.isBlank() ? "USER" : roleCode.trim().toUpperCase();
        if (Set.of("ADMIN", "DEVELOPER", "RELEASE_MANAGER", "VIEWER", "USER").contains(value)) return value;
        if (store.roles.values().stream().anyMatch(role -> role.roleCode().equalsIgnoreCase(value))) return value;
        throw new BadRequestException("角色不存在：" + value);
    }
    private boolean isBuiltInAdmin(UserView user) { return user != null && "admin".equalsIgnoreCase(user.username().trim()); }

    public Set<String> effectivePermissions(long userId) {
        UserView user = store.users.get(userId);
        if (user == null) throw new NotFoundException("用户不存在：" + userId);
        Set<String> stored = store.userPermissions.getOrDefault(userId, Set.of());
        if (stored.contains(PERMISSION_MARKER)) return effectivePermissions(stored);
        Set<String> rolePermissions = store.roles.values().stream()
                .filter(role -> role.roleCode().equalsIgnoreCase(user.roleCode()))
                .findFirst().map(RoleView::permissions).map(AccessService::effectivePermissions).orElse(Set.of())
                .stream().filter(MODULE_PERMISSIONS::contains).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return rolePermissions.isEmpty() ? effectivePermissions(stored) : rolePermissions;
    }
    public static Set<String> effectivePermissions(Set<String> stored) {
        if (stored.isEmpty()) return defaultViewPermissions();
        Set<String> result = new HashSet<>();
        for (String permission : stored) {
            if (PERMISSION_MARKER.equals(permission)) continue;
            if (DATA_DEVELOPMENT_PROJECT_ALL.equals(permission)) {
                result.add(DATA_DEVELOPMENT_PROJECT_ALL);
                result.add("DATA_DEVELOPMENT_VIEW");
                result.add("DATA_DEVELOPMENT_EDIT");
            } else if (MODULE_PERMISSIONS.contains(permission)) result.add(permission);
        }
        return Set.copyOf(result);
    }
    public static Set<String> defaultViewPermissions() {
        Set<String> result = new HashSet<>();
        MODULES.forEach(module -> result.add(module + "_VIEW"));
        return Set.copyOf(result);
    }
    private static Set<String> allPermissionCodes() {
        Set<String> result = new HashSet<>();
        MODULES.forEach(module -> { result.add(module + "_VIEW"); result.add(module + "_EDIT"); });
        result.add(DATA_DEVELOPMENT_PROJECT_ALL);
        return Set.copyOf(result);
    }

    public String grantProjectPermission(long projectId, AccessRequests.PermissionBindingRequest request) { return grantProjectPermission(projectId, request, "system"); }
    @Transactional
    public String grantProjectPermission(long projectId, AccessRequests.PermissionBindingRequest request, String operator) {
        if (!store.projects.containsKey(projectId)) throw new NotFoundException("项目不存在：" + projectId);
        if (!store.users.containsKey(request.userId())) throw new NotFoundException("用户不存在：" + request.userId());
        String permission = request.permissionCode() == null ? "" : request.permissionCode().trim().toUpperCase();
        if (!Set.of("VIEW", "EDIT").contains(permission)) throw new BadRequestException("项目权限仅支持 VIEW 或 EDIT");
        String key = projectId + ":" + request.userId() + ":" + permission;
        if (store.projectPermissions.containsKey(key)) return key;
        store.persistProjectPermission(projectId, request.userId(), permission);
        store.projectPermissions.put(key, permission);
        UserView user = store.users.get(request.userId());
        if (auth != null && user != null) auth.invalidateUser(user.username());
        audit.record("GRANT_PROJECT_PERMISSION", "PROJECT", projectId, key, normalizeOperator(operator));
        return key;
    }

    public String grantDatasourcePermission(long datasourceId, AccessRequests.PermissionBindingRequest request) { return grantDatasourcePermission(datasourceId, request, "system"); }
    @Transactional
    public String grantDatasourcePermission(long datasourceId, AccessRequests.PermissionBindingRequest request, String operator) {
        if (!store.dataSources.containsKey(datasourceId)) throw new NotFoundException("数据源不存在：" + datasourceId);
        if (!store.users.containsKey(request.userId())) throw new NotFoundException("用户不存在：" + request.userId());
        String permission = request.permissionCode() == null ? "" : request.permissionCode().trim().toUpperCase();
        if (!Set.of("VIEW", "QUERY", "EDIT").contains(permission)) throw new BadRequestException("数据源权限仅支持 VIEW、QUERY 或 EDIT");
        String key = datasourceId + ":" + request.userId() + ":" + permission;
        if (store.datasourcePermissions.containsKey(key)) return key;
        store.persistDatasourcePermission(datasourceId, request.userId(), permission);
        store.datasourcePermissions.put(key, permission);
        audit.record("GRANT_DATASOURCE_PERMISSION", "DATASOURCE", datasourceId, key, normalizeOperator(operator));
        return key;
    }


    public List<DataSourcePermissionView> datasourcePermissions() {
        return store.datasourcePermissions.keySet().stream().map(key -> {
            String[] parts = key.split(":", 3);
            if (parts.length != 3) return null;
            long dataSourceId = Long.parseLong(parts[0]);
            long userId = Long.parseLong(parts[1]);
            var source = store.dataSources.get(dataSourceId);
            var user = store.users.get(userId);
            if (source == null || user == null) return null;
            return new DataSourcePermissionView(dataSourceId, source.name(), userId, user.username(), user.displayName(), parts[2]);
        }).filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(DataSourcePermissionView::dataSourceName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(DataSourcePermissionView::username, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(DataSourcePermissionView::permissionCode))
                .toList();
    }

    public void revokeDatasourcePermission(long datasourceId, long userId, String permissionCode) { revokeDatasourcePermission(datasourceId, userId, permissionCode, "system"); }
    @Transactional
    public void revokeDatasourcePermission(long datasourceId, long userId, String permissionCode, String operator) {
        if (!store.dataSources.containsKey(datasourceId)) throw new NotFoundException("数据源不存在：" + datasourceId);
        if (!store.users.containsKey(userId)) throw new NotFoundException("用户不存在：" + userId);
        String permission = permissionCode == null ? "" : permissionCode.trim().toUpperCase();
        if (!Set.of("VIEW", "QUERY", "EDIT").contains(permission)) throw new BadRequestException("数据源权限仅支持 VIEW、QUERY 或 EDIT");
        store.deleteDatasourcePermission(datasourceId, userId, permission);
        audit.record("REVOKE_DATASOURCE_PERMISSION", "DATASOURCE", datasourceId, userId + ":" + permission, normalizeOperator(operator));
    }

    private String normalizeOperator(String operator) { return operator == null || operator.isBlank() ? "system" : operator.trim(); }

    public record DataSourcePermissionView(long dataSourceId, String dataSourceName, long userId, String username,
                                           String displayName, String permissionCode) { }
}
