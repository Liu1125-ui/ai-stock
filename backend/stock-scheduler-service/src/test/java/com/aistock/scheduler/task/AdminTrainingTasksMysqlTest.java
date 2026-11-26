package com.aistock.scheduler.task;

import com.aistock.common.BusinessException;
import com.aistock.scheduler.admin.AdminTrainingTasks;
import com.aistock.scheduler.admin.AdminTaskActionRecords.Command;
import com.aistock.scheduler.training.TrainingInbox;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class AdminTrainingTasksMysqlTest {
    @Test void cancelsOnceAndRecordsTheAdministratorAndReason() {
        var fixture=new TaskMysqlTest();
        fixture.repo();
        var clock=Clock.systemUTC();
        var inbox=new TrainingInbox(fixture.jdbc,clock);
        var admin=new AdminTrainingTasks(fixture.jdbc,clock,true);
        var actor=UUID.randomUUID();
        var task=UUID.randomUUID();
        var other=UUID.randomUUID();
        var command=new Command(UUID.randomUUID(),"0","CANCEL_NOT_NEEDED");
        inbox.receive(new TrainingInbox.Envelope(task.toString(),"five-day","a".repeat(64),"b".repeat(64)));
        inbox.receive(new TrainingInbox.Envelope(other.toString(),"five-day","a".repeat(64),"b".repeat(64)));
        try {
            var disabled=new AdminTrainingTasks(fixture.jdbc,clock,false);
            assertEquals("TASK_ACTIONS_DISABLED",assertThrows(BusinessException.class,
                ()->disabled.cancel(actor,task,command)).code());
            var receipt=admin.cancel(actor,task,command);
            assertEquals(receipt,admin.cancel(actor,task,command));
            assertEquals("CANCELLED",inbox.status(task.toString()).state());
            assertEquals("OPERATION_KEY_CONFLICT",assertThrows(BusinessException.class,
                ()->admin.cancel(UUID.randomUUID(),task,command)).code());
            assertEquals("OPERATION_KEY_CONFLICT",assertThrows(BusinessException.class,
                ()->admin.cancel(actor,other,command)).code());
            assertEquals("QUEUED",inbox.status(other.toString()).state());
            var row=admin.list(1).items().stream().filter(item->task.toString().equals(item.taskId())).findFirst().orElseThrow();
            assertEquals(actor.toString(),row.cancellation().actorUserId());
            assertEquals(command.reasonCode(),row.cancellation().reasonCode());
            assertEquals(command.operationId().toString(),row.cancellation().operationId());
        } finally {
            fixture.jdbc.update("DELETE FROM training_admin_cancellation WHERE task_id=?",task.toString());
            fixture.jdbc.update("DELETE FROM training_dispatch_inbox WHERE task_id IN (?,?)",task.toString(),other.toString());
        }
    }
}
