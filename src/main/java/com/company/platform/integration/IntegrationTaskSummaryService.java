package com.company.platform.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IntegrationTaskSummaryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final IntegrationRuntimeRepository runtimeRepository;

    public IntegrationTaskSummaryService(JdbcTemplate jdbc, ObjectMapper mapper, IntegrationRuntimeRepository runtimeRepository) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.runtimeRepository = runtimeRepository;
    }

    public void recordCreator(long taskId, String operator) {
        String value = operator == null || operator.isBlank() ? "platform" : operator.trim();
        jdbc.update("UPDATE integration_task SET created_by=? WHERE id=?", value, taskId);
    }

    public Summary summary(long taskId) {
        return states(List.of(taskId)).stream().findFirst().map(TaskState::summary)
                .orElse(new Summary(null, "platform", null, null, null, null));
    }

    public List<TaskState> states(Collection<Long> taskIds) {
        List<Long> ids = taskIds == null ? List.of() : taskIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return List.of();
        String placeholders = placeholders(ids.size());
        Object[] args = ids.toArray();

        Map<Long, TaskMeta> metadata = new HashMap<>();
        jdbc.query("SELECT id,created_at,created_by FROM integration_task WHERE id IN (" + placeholders + ")",
                (rs, n) -> new TaskMeta(rs.getLong("id"), time(rs.getTimestamp("created_at")), rs.getString("created_by")), args)
                .forEach(row -> metadata.put(row.taskId(), row));

        Map<Long, IntegrationBatchView> batches = runtimeRepository.latestBatches(ids);
        Map<Long, IntegrationInstanceView> instances = latestInstances(ids, placeholders, args);
        Map<Long, Long> counts = validationCounts(batches.values());
        Map<Long, LocalDateTime> nextRuns = nextRuns(ids, placeholders, args);

        return ids.stream().map(taskId -> {
            TaskMeta meta = metadata.get(taskId);
            IntegrationBatchView batch = batches.get(taskId);
            LocalDateTime lastRunAt = null;
            Long durationMs = null;
            Long dataCount = null;
            if (batch != null) {
                lastRunAt = batch.startedAt() == null ? batch.createdAt() : batch.startedAt();
                if (batch.startedAt() != null) {
                    LocalDateTime end = batch.finishedAt() == null ? LocalDateTime.now() : batch.finishedAt();
                    durationMs = Math.max(0, Duration.between(batch.startedAt(), end).toMillis());
                }
                dataCount = counts.get(batch.id());
            }
            Summary summary = new Summary(meta == null ? null : meta.createdAt(),
                    meta == null || meta.createdBy() == null || meta.createdBy().isBlank() ? "platform" : meta.createdBy(),
                    lastRunAt, nextRuns.get(taskId), durationMs, dataCount);
            return new TaskState(taskId, batch, instances.get(taskId), summary);
        }).toList();
    }

    private Map<Long, IntegrationInstanceView> latestInstances(List<Long> ids, String placeholders, Object[] args) {
        String sql = "SELECT i.id,i.task_id,i.execution_id,i.status,i.started_at,i.finished_at,i.error_message " +
                "FROM integration_instance i JOIN (SELECT task_id,MAX(id) id FROM integration_instance " +
                "WHERE task_id IN (" + placeholders + ") GROUP BY task_id) latest ON latest.id=i.id";
        Map<Long, IntegrationInstanceView> out = new HashMap<>();
        jdbc.query(sql, (rs, n) -> new IntegrationInstanceView(rs.getLong("id"), rs.getLong("task_id"),
                        rs.getString("execution_id"), rs.getString("status"), time(rs.getTimestamp("started_at")),
                        time(rs.getTimestamp("finished_at")), rs.getString("error_message")), args)
                .forEach(row -> out.put(row.taskId(), row));
        return out;
    }

    private Map<Long, Long> validationCounts(Collection<IntegrationBatchView> batches) {
        List<Long> batchIds = batches.stream().map(IntegrationBatchView::id).distinct().toList();
        if (batchIds.isEmpty()) return Map.of();
        String placeholders = placeholders(batchIds.size());
        Map<Long, Long> out = new HashMap<>();
        jdbc.query("SELECT batch_id,SUM(target_count) data_count,COUNT(id) validation_count FROM integration_validation_result " +
                        "WHERE batch_id IN (" + placeholders + ") GROUP BY batch_id",
                (rs, n) -> new ValidationCount(rs.getLong("batch_id"), rs.getLong("data_count"), rs.getInt("validation_count")), batchIds.toArray())
                .forEach(row -> { if (row.validationCount() > 0) out.put(row.batchId(), row.dataCount()); });
        return out;
    }

    private Map<Long, LocalDateTime> nextRuns(List<Long> ids, String placeholders, Object[] args) {
        Map<Long, ZonedDateTime> earliest = new HashMap<>();
        jdbc.query("SELECT task_id,cron_expression,timezone FROM integration_task_schedule WHERE enabled=TRUE AND task_id IN (" + placeholders + ")",
                (rs, n) -> new DirectSchedule(rs.getLong("task_id"), rs.getString("cron_expression"), rs.getString("timezone")), args)
                .forEach(row -> considerNext(earliest, row.taskId(), row.cron(), row.timezone()));
        try {
            List<ScheduleRef> refs = jdbc.query("SELECT wn.config_json,sc.cron_expression,sc.timezone FROM workflow_node wn " +
                            "JOIN schedule_config sc ON sc.workflow_id=wn.workflow_id WHERE wn.node_type='SEATUNNEL' AND sc.enabled=TRUE",
                    (rs, n) -> new ScheduleRef(rs.getString("config_json"), rs.getString("cron_expression"), rs.getString("timezone")));
            java.util.Set<Long> allowed = java.util.Set.copyOf(ids);
            for (ScheduleRef ref : refs) {
                try {
                    JsonNode config = mapper.readTree(ref.configJson() == null ? "{}" : ref.configJson());
                    long taskId = config.path("integrationTaskId").asLong(0);
                    if (taskId > 0 && allowed.contains(taskId)) considerNext(earliest, taskId, ref.cron(), ref.timezone());
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        Map<Long, LocalDateTime> out = new LinkedHashMap<>();
        earliest.forEach((taskId, value) -> out.put(taskId, value.toLocalDateTime()));
        return out;
    }

    private void considerNext(Map<Long, ZonedDateTime> earliest, long taskId, String cron, String timezone) {
        try {
            ZoneId zone = ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone);
            ZonedDateTime next = CronExpression.parse(cron).next(ZonedDateTime.now(zone));
            if (next == null) return;
            ZonedDateTime current = earliest.get(taskId);
            if (current == null || next.toInstant().isBefore(current.toInstant())) earliest.put(taskId, next);
        } catch (Exception ignored) { }
    }

    private String placeholders(int size) { return String.join(",", Collections.nCopies(size, "?")); }
    private LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }

    public record Summary(LocalDateTime createdAt, String createdBy, LocalDateTime lastRunAt,
                          LocalDateTime nextRunAt, Long durationMs, Long dataCount) { }
    public record TaskState(long taskId, IntegrationBatchView latestBatch, IntegrationInstanceView latestInstance, Summary summary) { }
    private record TaskMeta(long taskId, LocalDateTime createdAt, String createdBy) { }
    private record ValidationCount(long batchId, long dataCount, int validationCount) { }
    private record ScheduleRef(String configJson, String cron, String timezone) { }
    private record DirectSchedule(long taskId, String cron, String timezone) { }
}
