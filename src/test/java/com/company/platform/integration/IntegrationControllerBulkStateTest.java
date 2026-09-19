package com.company.platform.integration;

import com.company.platform.cluster.SeaTunnelClusterService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationControllerBulkStateTest {
    @Test
    void listStatesUsesOnlyVisibleOfflineTasks() {
        IntegrationService service = mock(IntegrationService.class);
        IntegrationTaskSummaryService summaries = mock(IntegrationTaskSummaryService.class);
        IntegrationController controller = new IntegrationController(service, mock(IntegrationMetadataService.class),
                mock(SeaTunnelClusterService.class), summaries, mock(IntegrationTaskScheduleService.class));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("platform.operator")).thenReturn("bob");
        IntegrationTaskView offline = task(1L, "FULL");
        IntegrationTaskView realtime = task(2L, "REALTIME");
        when(service.list("bob")).thenReturn(List.of(offline, realtime));
        var state = state(1L);
        when(summaries.states(List.of(1L))).thenReturn(List.of(state));

        var result = controller.states(request);

        assertEquals(List.of(state), result.data());
        verify(summaries).states(List.of(1L));
    }

    @Test
    void queriedStatesIntersectRequestedIdsWithVisibleTasks() {
        IntegrationService service = mock(IntegrationService.class);
        IntegrationTaskSummaryService summaries = mock(IntegrationTaskSummaryService.class);
        IntegrationController controller = new IntegrationController(service, mock(IntegrationMetadataService.class),
                mock(SeaTunnelClusterService.class), summaries, mock(IntegrationTaskScheduleService.class));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("platform.operator")).thenReturn("bob");
        when(service.list("bob")).thenReturn(List.of(task(1L, "FULL"), task(2L, "FULL")));
        var state = state(1L);
        when(summaries.states(List.of(1L))).thenReturn(List.of(state));

        var result = controller.states(List.of(1L, 999L, 1L), request);

        assertEquals(List.of(state), result.data());
        verify(summaries).states(List.of(1L));
    }

    @Test
    void taskAttemptsRequireVisibleTaskAndUseSingleTaskQuery() {
        IntegrationService service = mock(IntegrationService.class);
        IntegrationTaskSummaryService summaries = mock(IntegrationTaskSummaryService.class);
        IntegrationController controller = new IntegrationController(service, mock(IntegrationMetadataService.class),
                mock(SeaTunnelClusterService.class), summaries, mock(IntegrationTaskScheduleService.class));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("platform.operator")).thenReturn("bob");
        IntegrationAttemptView attempt = new IntegrationAttemptView(7L, 3L, 1, "exec-1", "SUCCESS",
                LocalDateTime.now(), LocalDateTime.now(), null, LocalDateTime.now());
        when(service.attemptsForTask(1L)).thenReturn(List.of(attempt));

        var result = controller.taskAttempts(1L, request);

        assertEquals(List.of(attempt), result.data());
        verify(service).requireTaskView(1L, "bob");
        verify(service).attemptsForTask(1L);
    }

    private IntegrationTaskView task(long id, String mode) {
        return new IntegrationTaskView(id, "task-" + id, "MYSQL", "STARROCKS", mode, "GENERATED", "OFFLINE", "{}", "{}", "{}", "", List.of());
    }

    private IntegrationTaskSummaryService.TaskState state(long taskId) {
        return new IntegrationTaskSummaryService.TaskState(taskId, null, null,
                new IntegrationTaskSummaryService.Summary(LocalDateTime.now(), "bob", null, null, null, null));
    }
}
