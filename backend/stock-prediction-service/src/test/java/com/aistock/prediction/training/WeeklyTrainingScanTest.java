package com.aistock.prediction.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.*;

class WeeklyTrainingScanTest {
    @TempDir Path feed;
    static final String DATA="a".repeat(64);
    static final UUID OWNER=UUID.fromString("11111111-1111-1111-1111-111111111111");
    final WeeklyTrainingPlanner planner=mock(WeeklyTrainingPlanner.class);
    final TrainingAdmissionRepository admissions=mock(TrainingAdmissionRepository.class);

    @Test void exactEvidenceAdmitsButHistoricalOrTamperedInputDoesNot() throws Exception {
        var scan=scanAt("2026-11-13T11:59:00Z");
        var preset=preset();
        report("2026-11-12",preset,8);
        var ready=new WeeklyTrainingPlanner.Snapshot(WeeklyTrainingPlanner.Readiness.READY,
            java.time.LocalDate.parse("2026-11-12"),DATA,"rolling",preset);
        when(planner.check(ready)).thenReturn(new WeeklyTrainingPlanner.Plan(null,"PLANNED",null,null,
            null,null,null,null,null,null,null,null));
        scan.scan();
        verify(planner).admit(ready,OWNER,admissions);
        clearInvocations(planner);

        scanAt("2026-11-13T12:00:00Z").scan();
        verify(planner).check(null);
        verifyNoMoreInteractions(planner);
        clearInvocations(planner);

        report("2026-11-12",preset,7);
        scan.scan();
        verify(planner).check(null);
        verifyNoMoreInteractions(planner);
        clearInvocations(planner);

        report("2026-11-12",preset,8);
        Files.writeString(feed.resolve(preset+".json"),"{}");
        scan.scan();
        verify(planner).check(null);
        verifyNoMoreInteractions(planner);
    }

    @Test void calendarMustCoverCutoff() throws Exception {
        var scan=scanAt("2026-11-13T12:00:00Z");
        Files.writeString(feed.resolve("calendar.json"),
            "{\"verifiedThrough\":\"2026-11-12\",\"sessions\":[\"2026-11-12\"]}");
        report("2026-11-12",preset(),8);
        scan.scan();
        verify(planner).check(null);
        verifyNoMoreInteractions(planner);
    }


    @Test void scheduledBeanRequiresBothExplicitFlags() {
        var context=new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withUserConfiguration(TrainingDispatchConfiguration.class)
            .withPropertyValues("aistock.internal.token=isolated-test-token")
            .withBean(org.springframework.jdbc.core.JdbcTemplate.class,
                ()->new org.springframework.jdbc.core.JdbcTemplate(mock(javax.sql.DataSource.class)))
            .withBean(Clock.class,Clock::systemUTC)
            .withBean(ObjectMapper.class,ObjectMapper::new)
            .withBean(com.aistock.prediction.modelcatalog.ModelRepository.class,
                ()->mock(com.aistock.prediction.modelcatalog.ModelRepository.class));
        context.withPropertyValues("aistock.training.dispatch-enabled=true")
            .run(ctx->org.assertj.core.api.Assertions.assertThat(ctx).doesNotHaveBean(WeeklyTrainingScan.class));
        context.withPropertyValues("aistock.training.weekly-scan-enabled=true")
            .run(ctx->org.assertj.core.api.Assertions.assertThat(ctx).doesNotHaveBean(WeeklyTrainingScan.class));
        context.withPropertyValues("aistock.training.dispatch-enabled=true",
            "aistock.training.weekly-scan-enabled=true",
            "aistock.training.weekly-calendar-path="+feed.resolve("calendar.json"),
            "aistock.training.weekly-readiness-path="+feed.resolve("readiness.json"),
            "aistock.training.weekly-preset-root="+feed,
            "aistock.training.weekly-configuration-id=rolling",
            "aistock.training.weekly-owner="+OWNER)
            .run(ctx->org.assertj.core.api.Assertions.assertThat(ctx).hasSingleBean(WeeklyTrainingScan.class));
    }

    private WeeklyTrainingScan scanAt(String now) throws Exception {
        Files.writeString(feed.resolve("calendar.json"),
            "{\"verifiedThrough\":\"2026-11-13\",\"sessions\":[\"2026-11-12\",\"2026-11-13\"]}");
        return new WeeklyTrainingScan(planner,admissions,new WeeklyTrainingEvidence(new ObjectMapper(),
            Clock.fixed(Instant.parse(now),ZoneOffset.UTC),feed.resolve("calendar.json"),
            feed.resolve("readiness.json"),feed,"rolling"),OWNER);
    }

    private String preset() throws Exception {
        var body="{\"schemaVersion\":2,\"configurationId\":\"rolling\",\"datasetHash\":\""+DATA+"\"}";
        var digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Files.writeString(feed.resolve(digest+".json"),body);
        return digest;
    }

    private void report(String date,String digest,int fresh) throws Exception {
        var group="{\"total\":10,\"fresh\":"+fresh+",\"latest\":\""+date+"\"}";
        Files.writeString(feed.resolve("readiness.json"),"""
            {"state":"READY","datasetHash":"%s","configurationId":"rolling","configurationHash":"%s",
             "expectedSession":"%s","minimumCoveragePercent":80,"groups":{"training_stock":%s,"heldout_stock":%s},
             "windows":{"train":10,"validation":8,"holdout":7}}
            """.formatted(DATA,digest,date,group,group));
    }
}
