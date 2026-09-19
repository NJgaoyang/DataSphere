package com.company.platform.system;

import com.company.platform.common.PlatformStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuditService {
    private final PlatformStore store;
    private final JdbcTemplate jdbc;
    private final Map<Long,AuditLogView> fallback = new ConcurrentHashMap<>();

    public AuditService(PlatformStore store) { this(store, null); }
    @Autowired
    public AuditService(PlatformStore store, JdbcTemplate jdbc) { this.store = store; this.jdbc = jdbc; }

    public AuditLogView record(String action, String resourceType, Long resourceId, String detail, String operator) {
        operator = resolveOperator(operator);
        if (jdbc == null) {
            AuditLogView log = new AuditLogView(store.nextId(), action, resourceType, resourceId, detail, operator, LocalDateTime.now());
            fallback.put(log.id(), log);
            return log;
        }
        KeyHolder key = new GeneratedKeyHolder();
        String finalOperator = operator;
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO operation_audit(action,resource_type,resource_id,detail,operator_name) VALUES(?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, action); ps.setString(2, resourceType);
            if (resourceId == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, resourceId);
            ps.setString(4, detail); ps.setString(5, finalOperator); return ps;
        }, key);
        long id = key.getKey() == null ? 0L : key.getKey().longValue();
        return new AuditLogView(id, action, resourceType, resourceId, detail, operator, LocalDateTime.now());
    }

    public List<AuditLogView> list() { return list(null, null, 200, 0); }
    public List<AuditLogView> list(String action, String operator, int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(0, offset);
        if (jdbc == null) return fallback.values().stream().sorted(java.util.Comparator.comparing(AuditLogView::createdAt).reversed()).skip(safeOffset).limit(safeLimit).toList();
        StringBuilder sql = new StringBuilder("SELECT id,action,resource_type,resource_id,detail,operator_name,created_at FROM operation_audit WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (action != null && !action.isBlank()) { sql.append(" AND action LIKE ?"); args.add("%" + action.trim() + "%"); }
        if (operator != null && !operator.isBlank()) { sql.append(" AND operator_name LIKE ?"); args.add("%" + operator.trim() + "%"); }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?"); args.add(safeLimit); args.add(safeOffset);
        return jdbc.query(sql.toString(), (rs,n) -> new AuditLogView(rs.getLong("id"),rs.getString("action"),rs.getString("resource_type"),
                rs.getObject("resource_id",Long.class),rs.getString("detail"),rs.getString("operator_name"),rs.getTimestamp("created_at").toLocalDateTime()), args.toArray());
    }

    public long count(String action, String operator) {
        if (jdbc == null) return fallback.size();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM operation_audit WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (action != null && !action.isBlank()) { sql.append(" AND action LIKE ?"); args.add("%" + action.trim() + "%"); }
        if (operator != null && !operator.isBlank()) { sql.append(" AND operator_name LIKE ?"); args.add("%" + operator.trim() + "%"); }
        Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    public List<String> operationMessages(int limit) {
        if (jdbc == null) return list(null,null,limit,0).stream().map(log -> log.action() + " " + (log.detail()==null?"":log.detail())).toList();
        int safe = Math.max(1, Math.min(limit, 500));
        return jdbc.query("SELECT CONCAT(action,' ',COALESCE(detail,'')) FROM operation_audit ORDER BY id DESC LIMIT " + safe, (rs,n) -> rs.getString(1));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applyRetentionPolicy() {
        if (jdbc == null) return;
        jdbc.update("DELETE FROM operation_audit WHERE created_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 365 DAY)");
        jdbc.update("DELETE FROM operation_log WHERE created_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 365 DAY)");
    }

    private String resolveOperator(String operator) {
        if ((operator == null || operator.isBlank() || "admin".equalsIgnoreCase(operator))
                && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object current = attributes.getRequest().getAttribute("platform.operator");
            if (current instanceof String value && !value.isBlank()) operator = value;
        }
        return operator == null || operator.isBlank() ? "admin" : operator;
    }

    public record Page(List<AuditLogView> items,long total,int page,int pageSize) { }
    public Page page(String action,String operator,int page,int pageSize) {
        int p=Math.max(1,page), size=Math.max(1,Math.min(pageSize,200));
        return new Page(list(action,operator,size,(p-1)*size),count(action,operator),p,size);
    }
}
