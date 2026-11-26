package com.aistock.scheduler.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class TrainingCandidateRegistrationWorker {
    private record Candidate(String taskId,String checkpointHash) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final PredictionTaskHttp http;
    private final String algorithm;
    private final String prediction;

    public TrainingCandidateRegistrationWorker(JdbcTemplate jdbc,Clock clock,ObjectMapper mapper,
        String algorithm,String prediction,String token) {
        this.jdbc=jdbc;
        this.clock=clock;
        this.transaction=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        this.transaction.setTimeout(5);
        this.algorithm=fixedBase(algorithm);
        this.prediction=fixedBase(prediction);
        if(token==null||token.isBlank())throw new IllegalArgumentException("Training registration requires a service token");
        this.http=new PredictionTaskHttp(token,mapper);
    }

    public void tick() {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Training registration requires a transaction-free caller");
        var task=claim();
        if(task==null)return;
        try {
            var prepared=data(http.call(algorithm,"/internal/v1/training-executions/"+task.taskId()+"/candidate",
                java.util.Map.of(),Duration.ofSeconds(12)));
            if(!task.taskId().equals(prepared.path("taskId").textValue())
                ||!task.checkpointHash().equals(prepared.path("checkpointHash").textValue())
                ||!prepared.path("registration").isObject())return;
            var registration=prepared.path("registration");
            String artifactId=registration.path("artifactId").textValue();
            String manifestHash=registration.path("manifestHash").textValue();
            if(!isCandidateArtifactId(artifactId)
                ||manifestHash==null||!manifestHash.matches("[a-f0-9]{64}"))return;
            var result=data(http.call(prediction,"/internal/v1/models/candidates",registration,Duration.ofSeconds(18)));
            String id=result.path("modelId").asText();
            if(!id.matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")
                ||!task.checkpointHash().equals(result.path("checkpointHash").textValue())
                ||!artifactId.equals(result.path("artifactId").textValue())
                ||!manifestHash.equals(result.path("manifestHash").textValue()))return;
            jdbc.update("""
                UPDATE training_dispatch_inbox SET registered_model_id=?
                WHERE task_id=? AND checkpoint_hash=? AND registered_model_id IS NULL
                """,id,task.taskId(),task.checkpointHash());
        } catch(RuntimeException error) {
            // Retry the immutable candidate later. The catalog deduplicates by artifact identity.
        }
    }

    private Candidate claim() {
        return transaction.execute(status->{
            var now=clock.instant();
            var rows=jdbc.query("""
                SELECT task_id,checkpoint_hash FROM training_dispatch_inbox
                WHERE execution_state='SUCCEEDED' AND checkpoint_hash IS NOT NULL
                    AND registered_model_id IS NULL AND registration_due_at<=?
                ORDER BY registration_due_at,task_id LIMIT 1 FOR UPDATE SKIP LOCKED
                """,(row,index)->new Candidate(row.getString(1),row.getString(2)),Timestamp.from(now));
            if(rows.isEmpty())return null;
            var row=rows.get(0);
            jdbc.update("UPDATE training_dispatch_inbox SET registration_due_at=? WHERE task_id=?",
                Timestamp.from(now.plusSeconds(90)),row.taskId());
            return row;
        });
    }

    static boolean isCandidateArtifactId(String artifactId) {
        return artifactId!=null
            &&artifactId.matches("transformer-s3-(?:2023|rolling-[0-9]{8})-seed(?:42|43|44)-[a-f0-9]{16}");
    }

    private static JsonNode data(JsonNode reply) {
        if(!"OK".equals(reply.path("code").textValue())||!reply.path("data").isObject())
            throw new IllegalStateException("Invalid training registration response");
        return reply.path("data");
    }

    private static String fixedBase(String base) {
        var uri=URI.create(base);
        if(uri.getScheme()==null||!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null
            ||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null
            ||!Set.of("","/").contains(uri.getPath()))throw new IllegalArgumentException("Invalid registration service address");
        return base;
    }
}
