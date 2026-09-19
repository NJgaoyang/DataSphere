package com.company.platform.workflow;

import com.company.platform.common.PlatformStore;
import com.company.platform.development.DevFileView;
import com.company.platform.development.DevProjectView;
import com.company.platform.development.DevelopmentScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectWorkflowServiceTest {
    @Test
    void projectImpactFollowsDevelopmentDependencies() {
        PlatformStore store = new PlatformStore();
        store.projects.clear(); store.files.clear();
        store.projects.put(1L,new DevProjectView(1,"生产项目","","ACTIVE","admin"));
        store.files.put(1L,file(1,"ods_order.sql"));
        store.files.put(2L,file(2,"dwd_order.sql"));
        store.files.put(3L,file(3,"dws_order.sql"));
        DevelopmentScheduleService schedules = mock(DevelopmentScheduleService.class);
        when(schedules.get(1L)).thenReturn(schedule(1L,List.of()));
        when(schedules.get(2L)).thenReturn(schedule(2L,List.of(new DevelopmentScheduleService.DependencyView(1L,"ods_order.sql"))));
        when(schedules.get(3L)).thenReturn(schedule(3L,List.of(new DevelopmentScheduleService.DependencyView(2L,"dwd_order.sql"))));
        ProjectWorkflowService service = new ProjectWorkflowService(store,schedules,mock(JdbcTemplate.class));

        var graph = service.graph(1L);
        assertEquals(3, graph.nodes().size());
        assertEquals(2, graph.edges().size());

        var impact = service.impact(1L,1L,false);
        assertEquals(List.of(2L,3L), impact.tasks().stream().map(ProjectWorkflowService.ImpactTask::fileId).toList());
        assertEquals(List.of(1,2), impact.tasks().stream().map(ProjectWorkflowService.ImpactTask::level).toList());
    }

    private static DevFileView file(long id,String name) {
        return new DevFileView(id,1,null,name,"SQL","select 1","","PUBLISHED",4,
                LocalDateTime.now(),"ONLINE",true,"admin");
    }

    private static DevelopmentScheduleService.ScheduleView schedule(long fileId,List<DevelopmentScheduleService.DependencyView> deps) {
        return new DevelopmentScheduleService.ScheduleView(fileId,1,1,true,"DAILY","02:00","0 0 2 * * ?",
                "Asia/Shanghai",1L,"ods","${system.biz.date-1}",3,5,120,deps,List.of(),4,1);
    }
}
