package com.aistock.prediction.verification;
import com.fasterxml.jackson.databind.*;import java.nio.file.*;import java.time.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
/** Explicit manual probe; no network, database, model call or fabricated forecast. */
class RealSourceContractProbe {
 @Test void actualMs02EvidenceIsReadableByMs03()throws Exception{
  var sample=System.getenv("REALIZATION_PROBE_FILE");assertNotNull(sample,"Provide sanitized real MS-02 probe file explicitly");var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);var report=mapper.readTree(Files.readAllBytes(Path.of(sample)));assertTrue(report.path("completed").booleanValue());var body=report.path("historicalFixedEvidence").path("response").path("data");var request=new VerificationRequest(UUID.randomUUID(),LocalDate.parse(body.path("dueThrough").asText()),Instant.now().plusSeconds(90));var evidence=VerificationEvidence.read(mapper,body,request,Clock.systemUTC());assertEquals("SH600030",evidence.instrumentId());assertEquals("baostock",evidence.provider());assertEquals("baostock-backward-close-over-raw-v1",evidence.definition());assertEquals(body.path("realizationVersionId").asText(),evidence.versionId());assertEquals(5,evidence.observations().size());assertTrue(evidence.observations().stream().allMatch(d->d.state()==VerificationRecords.PointState.VERIFIED));
 }
}
