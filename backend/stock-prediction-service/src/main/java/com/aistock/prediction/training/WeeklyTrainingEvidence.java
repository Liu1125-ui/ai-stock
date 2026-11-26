package com.aistock.prediction.training;

import com.aistock.common.training.TrainingSubmission;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

/** Reads evidence published by the controlled data pipeline, never by API clients. */
final class WeeklyTrainingEvidence {
    private static final int LIMIT=65536;
    private static final ZoneId SHANGHAI=ZoneId.of("Asia/Shanghai");
    private final ObjectReader reader;
    private final Clock clock;
    private final Path calendar, readiness, presets;
    private final String configurationId;

    WeeklyTrainingEvidence(ObjectMapper mapper,Clock clock,Path calendar,Path readiness,
                           Path presets,String configurationId) {
        if(!TrainingSubmission.isConfigurationId(configurationId))
            throw new IllegalArgumentException("Invalid weekly configuration ID");
        this.reader=mapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.clock=clock;
        this.calendar=calendar;
        this.readiness=readiness;
        this.presets=presets;
        this.configurationId=configurationId;
    }

    WeeklyTrainingPlanner.Snapshot current() {
        try {
            var session=completedSession();
            if(session==null)return null;
            var report=reader.readTree(read(readiness,LIMIT));
            if(!session.equals(date(report.path("expectedSession"))))return null;
            var dataset=report.path("datasetHash").asText("");
            var state=WeeklyTrainingPlanner.Readiness.valueOf(report.path("state").asText(""));
            if(state!=WeeklyTrainingPlanner.Readiness.READY)
                return new WeeklyTrainingPlanner.Snapshot(state,session,dataset,null,null);
            var hash=report.path("configurationHash").asText("");
            if(!hash.matches("[a-f0-9]{64}")||!configurationId.equals(report.path("configurationId").textValue())
                ||!coverage(report,session))return null;
            var raw=read(presets.resolve(hash+".json"),4096);
            if(!hash.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw))))return null;
            var preset=reader.readTree(raw);
            if(!preset.path("schemaVersion").isIntegralNumber()||preset.path("schemaVersion").intValue()!=2
                ||!configurationId.equals(preset.path("configurationId").textValue())
                ||!dataset.equals(preset.path("datasetHash").textValue()))return null;
            return new WeeklyTrainingPlanner.Snapshot(state,session,dataset,configurationId,hash);
        } catch(IOException|IllegalArgumentException|DateTimeException error) {
            return null;
        } catch(NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable",error);
        }
    }

    private LocalDate completedSession() throws IOException {
        var now=clock.instant().atZone(SHANGHAI);
        var cutoff=now.toLocalTime().isBefore(LocalTime.of(20,0))
            ?now.toLocalDate().minusDays(1):now.toLocalDate();
        var data=reader.readTree(read(calendar,LIMIT));
        var through=date(data.path("verifiedThrough"));
        var sessions=data.path("sessions");
        if(through.isBefore(cutoff)||!sessions.isArray()||sessions.isEmpty())return null;
        LocalDate previous=null, latest=null;
        for(var value:sessions) {
            var day=date(value);
            if(day.isAfter(through)||(previous!=null&&!day.isAfter(previous)))return null;
            if(!day.isAfter(cutoff))latest=day;
            previous=day;
        }
        return latest;
    }

    private static boolean coverage(JsonNode report,LocalDate session) {
        int minimum=integer(report.path("minimumCoveragePercent"));
        if(minimum<80||minimum>100)return false;
        for(var role:List.of("training_stock","heldout_stock")) {
            var group=report.path("groups").path(role);
            int total=integer(group.path("total")), fresh=integer(group.path("fresh"));
            if(total<=0||fresh<=0||fresh>total||fresh*100L<total*(long)minimum
                ||!session.equals(date(group.path("latest"))))return false;
        }
        for(var split:List.of("train","validation","holdout"))
            if(integer(report.path("windows").path(split))<=0)return false;
        return true;
    }

    private static int integer(JsonNode node) {
        return node.isIntegralNumber()&&node.canConvertToInt()?node.intValue():-1;
    }

    private static LocalDate date(JsonNode node) {
        if(!node.isTextual())throw new IllegalArgumentException("Invalid trading date");
        return LocalDate.parse(node.textValue());
    }

    private static byte[] read(Path file,int limit) throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing evidence");
        try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
            var raw=input.readNBytes(limit+1);
            if(raw.length==0||raw.length>limit)throw new IOException("Invalid evidence size");
            return raw;
        }
    }
}
