package com.company.platform.scheduler;

import com.company.platform.common.PlatformStore;
import com.company.platform.integration.IntegrationService;
import com.company.platform.query.QueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class LocalSchedulerGatewayScriptTest {
    @Test
    void executesPublishedShellAndPythonSnapshots() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalSchedulerGateway gateway = new LocalSchedulerGateway(jdbc, mock(Scheduler.class), mock(QueryService.class),
                mock(IntegrationService.class), new PlatformStore(), new ObjectMapper());
        Method executeScript = LocalSchedulerGateway.class.getDeclaredMethod("executeScript",
                com.fasterxml.jackson.databind.JsonNode.class, long.class, AtomicBoolean.class, boolean.class);
        executeScript.setAccessible(true);
        ObjectMapper mapper = new ObjectMapper();

        var shell = mapper.createObjectNode().put("contentBase64", Base64.getEncoder().encodeToString("echo shell-ok".getBytes(StandardCharsets.UTF_8))).put("configJson", "{}");
        var python = mapper.createObjectNode().put("contentBase64", Base64.getEncoder().encodeToString("print('python-ok')".getBytes(StandardCharsets.UTF_8))).put("configJson", "{}");

        assertDoesNotThrow(() -> executeScript.invoke(gateway, shell, 1L, new AtomicBoolean(false), false));
        assertDoesNotThrow(() -> executeScript.invoke(gateway, python, 2L, new AtomicBoolean(false), true));
        gateway.shutdown();
    }
}
