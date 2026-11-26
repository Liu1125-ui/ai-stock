package com.aistock.prediction.modelcatalog;

import com.aistock.prediction.training.WeeklyTrainingPlanner;
import static com.aistock.prediction.training.WeeklyTrainingPlanner.Readiness.*;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class WeeklyTrainingPlannerMysqlTest {
    @Test void onePlanPerShanghaiWeekAndPreviousSnapshotCannotBeReused()throws Exception {
        var fixture=new ModelDatabaseFixture();
        fixture.jdbc.update("DELETE FROM weekly_training_plan");
        var models=new ModelRepository(fixture.jdbc,ModelCatalogMysqlTest.MAPPER);
        var model=ModelCatalogMysqlTest.service(fixture).register(ModelCatalogMysqlTest.candidate(42));
        models.state(model.modelId(),"PUBLISHED",true,2);
        models.head(new ModelRecords.Head(ModelRecords.SCOPE,model.modelId(),3));
        var sundayUtc=Instant.parse("2026-11-08T16:01:00Z");
        var planner=new WeeklyTrainingPlanner(fixture.jdbc,models,Clock.fixed(sundayUtc,ZoneOffset.UTC));
        var target=LocalDate.parse("2026-11-06");
        assertEquals("NO_SNAPSHOT",planner.check(null).reason());
        assertEquals("STALE",planner.check(new WeeklyTrainingPlanner.Snapshot(STALE,target,"a".repeat(64),null,null)).reason());
        var ready=new WeeklyTrainingPlanner.Snapshot(READY,target,"b".repeat(64),"five-day-rolling","c".repeat(64));
        var workers=Executors.newFixedThreadPool(2);
        try {
            var first=workers.submit(()->planner.check(ready));
            var restart=new WeeklyTrainingPlanner(fixture.jdbc,models,Clock.fixed(sundayUtc,ZoneOffset.UTC));
            var second=workers.submit(()->restart.check(ready));
            var a=first.get(8,TimeUnit.SECONDS);
            var b=second.get(8,TimeUnit.SECONDS);
            assertEquals(a,b);
            assertEquals(LocalDate.parse("2026-11-09"),a.weekStart());
            assertEquals("PLANNED",a.state());
            assertNotNull(a.requestId());
            assertEquals(model.modelId(),a.modelId());
            assertEquals(2,a.modelRevision());
            assertEquals(3,a.headRevision());
            assertEquals("c".repeat(64),a.configurationHash());
            assertEquals(a,restart.check(null));
            var next=new WeeklyTrainingPlanner(fixture.jdbc,models,Clock.fixed(sundayUtc.plus(7,java.time.temporal.ChronoUnit.DAYS),ZoneOffset.UTC));
            assertEquals("ALREADY_PROCESSED",next.check(ready).reason());
            assertEquals("ALREADY_PROCESSED",next.check(new WeeklyTrainingPlanner.Snapshot(
                READY,target,"d".repeat(64),"five-day-rolling","c".repeat(64))).reason());
            assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM training_submission",Integer.class));
        } finally {
            workers.shutdownNow();
            fixture.jdbc.update("DELETE FROM weekly_training_plan");
        }
    }

    @Test void admissionReusesFrozenRequestAndRejectsChangedSnapshotOrRecommendation() {
        var fixture=new ModelDatabaseFixture();
        fixture.jdbc.update("DELETE FROM weekly_training_plan");
        var models=new ModelRepository(fixture.jdbc,ModelCatalogMysqlTest.MAPPER);
        var original=ModelCatalogMysqlTest.service(fixture).register(ModelCatalogMysqlTest.candidate(42));
        models.state(original.modelId(),"PUBLISHED",true,2);
        models.head(new ModelRecords.Head(ModelRecords.SCOPE,original.modelId(),3));
        var clock=Clock.fixed(Instant.parse("2026-11-09T04:00:00Z"),ZoneOffset.UTC);
        var planner=new WeeklyTrainingPlanner(fixture.jdbc,models,clock);
        var repo=new com.aistock.prediction.training.TrainingAdmissionRepository(fixture.jdbc,ModelCatalogMysqlTest.MAPPER,clock);
        var owner=java.util.UUID.randomUUID();
        var snapshot=new WeeklyTrainingPlanner.Snapshot(READY,LocalDate.parse("2026-11-09"),"b".repeat(64),
            "five-day-rolling","c".repeat(64));
        try {
            assertTrue(planner.admit(snapshot,owner,repo).isEmpty());
            var plan=planner.check(snapshot);
            var changed=new WeeklyTrainingPlanner.Snapshot(READY,snapshot.session(),"d".repeat(64),
                snapshot.configurationId(),snapshot.configurationHash());
            assertTrue(planner.admit(changed,owner,repo).isEmpty());
            models.head(new ModelRecords.Head(ModelRecords.SCOPE,original.modelId(),4));
            assertTrue(planner.admit(snapshot,owner,repo).isEmpty());
            assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM training_submission",Integer.class));
            models.head(new ModelRecords.Head(ModelRecords.SCOPE,original.modelId(),3));
            var admission=planner.admit(snapshot,owner,repo).orElseThrow();
            assertEquals(plan.requestId(),admission.requestId());
            assertEquals(admission,planner.admit(snapshot,owner,repo).orElseThrow());
            assertEquals(admission.taskId().toString(),repo.receipt(owner,plan.requestId().toString()).get(0).taskId());
            assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM training_submission_item",Integer.class));
        } finally {
            fixture.jdbc.update("DELETE FROM weekly_training_plan");
        }
    }
}
