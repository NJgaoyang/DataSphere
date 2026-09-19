package com.company.platform.workflow;

import com.company.platform.common.BadRequestException;
import com.company.platform.development.DevelopmentScheduleService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ProjectTaskRunner {
    private final JdbcTemplate jdbc;
    private final DevelopmentScheduleService schedules;

    public ProjectTaskRunner(JdbcTemplate jdbc, DevelopmentScheduleService schedules) {
        this.jdbc = jdbc;
        this.schedules = schedules;
    }

    public Result run(long fileId, LocalDate businessDate, String operator, AtomicBoolean cancelled) {
        Map<String,Object> file = jdbc.queryForMap("SELECT name,file_type,lifecycle_status FROM dev_file WHERE id=? AND recycled=FALSE", fileId);
        String type = Objects.toString(file.get("file_type"), "SQL").toUpperCase();
        if (!"ONLINE".equalsIgnoreCase(Objects.toString(file.get("lifecycle_status"), ""))) {
            return new Result("FAILED", null, "开发任务已下线", "");
        }
        return switch (type) {
            case "SQL" -> runSql(fileId, businessDate, operator, cancelled);
            case "PYTHON", "SHELL" -> runScript(fileId, type, cancelled);
            default -> new Result("FAILED", null, "项目重跑暂不支持任务类型：" + type, "");
        };
    }

    private Result runSql(long fileId, LocalDate businessDate, String operator, AtomicBoolean cancelled) {
        try {
            DevelopmentScheduleService.ScheduleRuntimeView existing = schedules.runtime(fileId);
            String existingStatus = existing.status() == null ? "" : existing.status().toUpperCase();
            DevelopmentScheduleService.ScheduleRuntimeView started = existingStatus.matches("RUNNING|STARTING|QUEUED|SUBMITTED")
                    && existing.executionId() != null && !existing.executionId().isBlank()
                    ? existing
                    : schedules.runNowForBusinessDate(fileId, businessDate.toString(), operator);
            String executionId = started.executionId();
            while (true) {
                if (cancelled.get()) {
                    try { schedules.killNow(fileId, operator); } catch (RuntimeException ignored) { }
                    return new Result("CANCELED", executionId, "批次已取消", "");
                }
                DevelopmentScheduleService.ScheduleRuntimeView current = schedules.runtime(fileId);
                String status = current.status() == null ? "RUNNING" : current.status().toUpperCase();
                if (status.matches("RUNNING|STARTING|QUEUED|SUBMITTED")) {
                    Thread.sleep(500L);
                    continue;
                }
                if ("SUCCESS".equals(status)) return new Result("SUCCESS", executionId, null, "");
                if (status.matches("CANCELED|CANCELLED|STOPPED|KILLED")) return new Result("CANCELED", executionId, current.errorMessage(), "");
                return new Result("FAILED", executionId, current.errorMessage() == null ? "SQL 任务执行失败" : current.errorMessage(), "");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result("CANCELED", null, "重跑线程被中断", "");
        } catch (RuntimeException ex) {
            return new Result("FAILED", null, message(ex), "");
        }
    }

    private Result runScript(long fileId, String type, AtomicBoolean cancelled) {
        Map<String,Object> version = jdbc.query("SELECT version_no,content FROM dev_file_version WHERE file_id=? AND publish_flag=TRUE ORDER BY version_no DESC LIMIT 1",
                rs -> rs.next() ? Map.of("version", rs.getInt("version_no"), "content", rs.getString("content")) : null, fileId);
        if (version == null) return new Result("FAILED", null, "开发任务还没有生产版本，请先发布任务", "");
        Integer timeout = jdbc.query("SELECT timeout_minutes FROM dev_file_schedule WHERE file_id=?",
                rs -> rs.next() ? rs.getInt(1) : null, fileId);
        int timeoutMinutes = timeout == null || timeout <= 0 ? 120 : timeout;
        String extension = "PYTHON".equals(type) ? ".py" : ".sh";
        String executionId = "project-script-" + UUID.randomUUID().toString().replace("-", "");
        Path script = null;
        Process process = null;
        try {
            script = Files.createTempFile("datasphere-project-", extension);
            Files.writeString(script, Objects.toString(version.get("content"), ""), StandardCharsets.UTF_8);
            ProcessBuilder builder = "PYTHON".equals(type)
                    ? new ProcessBuilder("python3", script.toString())
                    : new ProcessBuilder("bash", script.toString());
            builder.redirectErrorStream(true);
            process = builder.start();
            Process running = process;
            AtomicReference<String> output = new AtomicReference<>("");
            Thread reader = Thread.ofVirtual().name("project-script-log-", 0).start(() -> {
                try (var in = running.getInputStream()) {
                    byte[] bytes = in.readAllBytes();
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    output.set(text.length() > 200_000 ? text.substring(text.length() - 200_000) : text);
                } catch (IOException ignored) { }
            });
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(timeoutMinutes);
            while (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                if (cancelled.get()) {
                    process.destroyForcibly();
                    reader.join(1000);
                    return new Result("CANCELED", executionId, "批次已取消", output.get());
                }
                if (System.nanoTime() > deadline) {
                    process.destroyForcibly();
                    reader.join(1000);
                    return new Result("FAILED", executionId, "脚本执行超时（" + timeoutMinutes + " 分钟）", output.get());
                }
            }
            reader.join(1000);
            int code = process.exitValue();
            return code == 0
                    ? new Result("SUCCESS", executionId, null, output.get())
                    : new Result("FAILED", executionId, "脚本退出码：" + code, output.get());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return new Result("CANCELED", executionId, "重跑线程被中断", "");
        } catch (IOException ex) {
            return new Result("FAILED", executionId, message(ex), "");
        } finally {
            if (script != null) try { Files.deleteIfExists(script); } catch (IOException ignored) { }
        }
    }

    private String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    public record Result(String status, String executionId, String errorMessage, String outputLog) { }
}
