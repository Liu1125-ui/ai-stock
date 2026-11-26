package com.aistock.scheduler.task;

import com.aistock.common.ApiExceptionHandler;
import com.aistock.scheduler.training.TrainingInbox;
import com.aistock.scheduler.training.TrainingInboxController;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("mysql")
class TrainingInboxMysqlTest {
    @Test void authenticatedReceiveIsIdempotentAndConflictsDoNotReplaceOriginal() throws Exception {
        var fixture=new TaskMysqlTest();
        fixture.repo();
        var mapper=new ObjectMapper();
        var inbox=new TrainingInbox(fixture.jdbc,Clock.systemUTC());
        var mvc=MockMvcBuilders.standaloneSetup(new TrainingInboxController(inbox))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new TaskSecurityFilter("training-fixture",8192)).build();
        String path="/internal/v1/tasks/training-inbox";
        var item=new TrainingInbox.Envelope(UUID.randomUUID().toString(),"five-day","a".repeat(64),"b".repeat(64));
        byte[] payload=mapper.writeValueAsBytes(item);
        mvc.perform(post(path).contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(get(path+"/"+item.taskId()).header("X-Service-Token","training-fixture")).andExpect(status().isNotFound());
        for(int attempt=0;attempt<2;attempt++)
            mvc.perform(post(path).header("X-Service-Token","training-fixture").contentType("application/json").content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.taskId").value(item.taskId()));
        String statusPath=path+"/"+item.taskId()+"/status";
        mvc.perform(get(statusPath)).andExpect(status().isUnauthorized());
        mvc.perform(get(statusPath).header("X-Service-Token","training-fixture"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.data.state").value("QUEUED"))
            .andExpect(jsonPath("$.data.taskId").value(item.taskId()))
            .andExpect(jsonPath("$.data.datasetHash").doesNotExist());
        var altered=new TrainingInbox.Envelope(item.taskId(),item.configurationId(),item.configurationHash(),"c".repeat(64));
        mvc.perform(post(path).header("X-Service-Token","training-fixture").contentType("application/json")
                .content(mapper.writeValueAsBytes(altered)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRAINING_DISPATCH_CONFLICT"));
        mvc.perform(get(path+"/"+item.taskId()).header("X-Service-Token","training-fixture"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.data.datasetHash").value(item.datasetHash()));
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM training_dispatch_inbox WHERE task_id=?",Integer.class,item.taskId()));
    }

    @Test void cancelIsAuthenticatedAndIdempotentWithoutInterruptingRunningWork() throws Exception {
        var fixture=new TaskMysqlTest();
        fixture.repo();
        var inbox=new TrainingInbox(fixture.jdbc,Clock.systemUTC());
        var mvc=MockMvcBuilders.standaloneSetup(new TrainingInboxController(inbox))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new TaskSecurityFilter("training-fixture",8192)).build();
        var queued=inbox.receive(new TrainingInbox.Envelope(UUID.randomUUID().toString(),"five-day","a".repeat(64),"b".repeat(64)));
        var running=inbox.receive(new TrainingInbox.Envelope(UUID.randomUUID().toString(),"five-day","a".repeat(64),"b".repeat(64)));
        try {
            fixture.jdbc.update("UPDATE training_dispatch_inbox SET execution_state='RUNNING',execution_token=?,started_at=CURRENT_TIMESTAMP(3) WHERE task_id=?",
                UUID.randomUUID().toString(),running.taskId());
            String base="/internal/v1/tasks/training-inbox/";
            String cancel=base+queued.taskId()+"/cancel";
            mvc.perform(post(cancel)).andExpect(status().isUnauthorized());
            mvc.perform(post(cancel).header("X-Service-Token","training-fixture").content("{}")).andExpect(status().isBadRequest());
            mvc.perform(post(base+running.taskId()+"/cancel").header("X-Service-Token","training-fixture"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRAINING_TASK_NOT_CANCELLABLE"));
            mvc.perform(post(base+UUID.randomUUID()+"/cancel").header("X-Service-Token","training-fixture"))
                .andExpect(status().isNotFound());
            for(int attempt=0;attempt<2;attempt++) {
                mvc.perform(post(cancel).header("X-Service-Token","training-fixture"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(jsonPath("$.data.state").value("CANCELLED"));
            }
            assertEquals("RUNNING",inbox.status(running.taskId()).state());
            assertEquals("CANCELLED",inbox.status(queued.taskId()).state());
        } finally {
            fixture.jdbc.update("DELETE FROM training_dispatch_inbox WHERE task_id IN (?,?)",queued.taskId(),running.taskId());
        }
    }
}
