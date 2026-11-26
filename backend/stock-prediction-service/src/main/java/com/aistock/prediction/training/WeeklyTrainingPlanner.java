package com.aistock.prediction.training;

import com.aistock.common.training.TrainingSubmission;
import com.aistock.prediction.modelcatalog.ModelRecords;
import com.aistock.prediction.modelcatalog.ModelRepository;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reserves one immutable weekly training plan. Dispatch happens through a separate operation. */
public final class WeeklyTrainingPlanner {
    private static final ZoneId SHANGHAI=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final ModelRepository models;
    private final Clock clock;

    public enum Readiness { READY, STALE, INSUFFICIENT_COVERAGE, ALREADY_PROCESSED }

    public record Snapshot(Readiness readiness,LocalDate session,String datasetHash,String configurationId,String configurationHash) {
        public Snapshot {
            Objects.requireNonNull(readiness,"Missing snapshot readiness");
            Objects.requireNonNull(session,"Missing snapshot session");
            if(!ModelRecords.hash(datasetHash))throw new IllegalArgumentException("Invalid snapshot fingerprint");
            if(readiness==Readiness.READY) {
                if(!TrainingSubmission.isConfigurationId(configurationId)||!ModelRecords.hash(configurationHash))
                    throw new IllegalArgumentException("A ready snapshot requires a frozen configuration");
            } else if(configurationId!=null||configurationHash!=null) {
                throw new IllegalArgumentException("A waiting snapshot cannot bind a configuration");
            }
        }
    }

    public record Plan(LocalDate weekStart,String state,String reason,Instant checkedAt,LocalDate snapshotSession,
                       String datasetHash,String configurationId,String configurationHash,UUID requestId,
                       UUID modelId,Long modelRevision,Long headRevision) {}

    public record Admission(UUID owner,UUID requestId,UUID taskId,Instant admittedAt) {}

    public WeeklyTrainingPlanner(JdbcTemplate jdbc,ModelRepository models,Clock clock) {
        this.jdbc=jdbc;
        this.models=models;
        this.clock=clock;
    }

    public Plan check(Snapshot snapshot) {
        // The existing recommendation lock also serializes weekly scans and model release changes.
        return models.locked(head->{
            var now=clock.instant();
            var today=now.atZone(SHANGHAI).toLocalDate();
            if(snapshot!=null&&snapshot.session().isAfter(today))throw new IllegalArgumentException("Snapshot session is in the future");
            var week=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            var original=read(head.scope(),week);
            if(original!=null&&"PLANNED".equals(original.state()))return original;
            if(snapshot==null)return waiting(head.scope(),week,now,null,"NO_SNAPSHOT");
            if(snapshot.readiness()!=Readiness.READY)return waiting(head.scope(),week,now,snapshot,snapshot.readiness().name());
            if(alreadyPlanned(head.scope(),snapshot))return waiting(head.scope(),week,now,snapshot,"ALREADY_PROCESSED");
            if(head.currentModelId()==null)return waiting(head.scope(),week,now,snapshot,"NO_RECOMMENDATION");
            var model=models.find(head.currentModelId(),false);
            if(!"PUBLISHED".equals(model.state()))return waiting(head.scope(),week,now,snapshot,"NO_RECOMMENDATION");
            waiting(head.scope(),week,now,snapshot,"READY");
            jdbc.update("""
                UPDATE weekly_training_plan SET state='PLANNED',request_id=?,configuration_id=?,configuration_sha256=?,
                    model_id=?,model_revision=?,head_revision=?
                WHERE scope_key=? AND week_start=?
                """,UUID.randomUUID().toString(),snapshot.configurationId(),snapshot.configurationHash(),
                model.modelId().toString(),model.revision(),head.revision(),head.scope(),Date.valueOf(week));
            return read(head.scope(),week);
        });
    }

    /** Attaches one locally accepted request to its already frozen plan. */
    public Optional<Admission> admit(Snapshot snapshot,UUID owner,TrainingAdmissionRepository admissions) {
        Objects.requireNonNull(owner,"Missing weekly service account");
        if(snapshot==null||snapshot.readiness()!=Readiness.READY)return Optional.empty();
        return models.locked(head->{
            var now=clock.instant();
            var today=now.atZone(SHANGHAI).toLocalDate();
            if(snapshot.session().isAfter(today))throw new IllegalArgumentException("Snapshot session is in the future");
            var week=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            var plan=read(head.scope(),week);
            if(plan==null||!"PLANNED".equals(plan.state())||!snapshot.session().equals(plan.snapshotSession())
                ||!snapshot.datasetHash().equals(plan.datasetHash())||!snapshot.configurationId().equals(plan.configurationId())
                ||!snapshot.configurationHash().equals(plan.configurationHash()))return Optional.empty();
            var accepted=readAdmission(head.scope(),week);
            if(accepted.isPresent())return accepted.filter(value->owner.equals(value.owner()));
            if(!plan.modelId().equals(head.currentModelId())||plan.headRevision()!=head.revision())return Optional.empty();
            var model=models.find(plan.modelId(),false);
            if(!"PUBLISHED".equals(model.state())||plan.modelRevision()!=model.revision())return Optional.empty();
            var submission=new TrainingSubmission(plan.requestId().toString(),List.of(plan.configurationId()));
            var configuration=new TrainingAdmissionRepository.Configuration(plan.configurationHash(),plan.datasetHash());
            var items=admissions.admit(owner,submission,id->configuration);
            if(items.size()!=1||!plan.configurationId().equals(items.get(0).configurationId())
                ||!configuration.configurationHash().equals(items.get(0).configurationHash())
                ||!configuration.datasetHash().equals(items.get(0).datasetHash()))
                throw new IllegalStateException("Weekly training admission does not match frozen plan");
            jdbc.update("""
                UPDATE weekly_training_plan SET admitted_owner=?,admitted_task_id=?,admitted_at=?
                WHERE scope_key=? AND week_start=? AND admitted_task_id IS NULL
                """,owner.toString(),items.get(0).taskId(),Timestamp.from(now),head.scope(),Date.valueOf(week));
            return readAdmission(head.scope(),week);
        });
    }

    private Optional<Admission> readAdmission(String scope,LocalDate week) {
        return jdbc.query("""
            SELECT request_id,admitted_owner,admitted_task_id,admitted_at
            FROM weekly_training_plan WHERE scope_key=? AND week_start=? AND admitted_task_id IS NOT NULL
            """,(row,index)->new Admission(uuid(row,"admitted_owner"),uuid(row,"request_id"),
                uuid(row,"admitted_task_id"),row.getTimestamp("admitted_at").toInstant()),scope,Date.valueOf(week))
            .stream().findFirst();
    }

    private boolean alreadyPlanned(String scope,Snapshot snapshot) {
        return !jdbc.queryForList("""
            SELECT 1 FROM weekly_training_plan WHERE scope_key=? AND state='PLANNED'
                AND (dataset_sha256=? OR snapshot_session>=?) LIMIT 1
            """,Integer.class,scope,snapshot.datasetHash(),Date.valueOf(snapshot.session())).isEmpty();
    }

    private Plan waiting(String scope,LocalDate week,Instant now,Snapshot snapshot,String reason) {
        jdbc.update("""
            INSERT INTO weekly_training_plan(scope_key,week_start,state,reason,checked_at,snapshot_session,dataset_sha256)
            VALUES (?,?,'WAITING',?,?,?,?)
            ON DUPLICATE KEY UPDATE reason=VALUES(reason),checked_at=VALUES(checked_at),
                snapshot_session=VALUES(snapshot_session),dataset_sha256=VALUES(dataset_sha256)
            """,scope,Date.valueOf(week),reason,Timestamp.from(now),
            snapshot==null?null:Date.valueOf(snapshot.session()),snapshot==null?null:snapshot.datasetHash());
        return read(scope,week);
    }

    private Plan read(String scope,LocalDate week) {
        var rows=jdbc.query("SELECT * FROM weekly_training_plan WHERE scope_key=? AND week_start=?",
            (row,index)->new Plan(week,row.getString("state"),row.getString("reason"),row.getTimestamp("checked_at").toInstant(),
                localDate(row,"snapshot_session"),row.getString("dataset_sha256"),row.getString("configuration_id"),
                row.getString("configuration_sha256"),uuid(row,"request_id"),uuid(row,"model_id"),
                row.getObject("model_revision",Long.class),row.getObject("head_revision",Long.class)),scope,Date.valueOf(week));
        return rows.isEmpty()?null:rows.get(0);
    }

    private static UUID uuid(ResultSet row,String field)throws SQLException {
        var value=row.getString(field);
        return value==null?null:UUID.fromString(value);
    }
    private static LocalDate localDate(ResultSet row,String field)throws SQLException {
        var value=row.getDate(field);
        return value==null?null:value.toLocalDate();
    }
}
