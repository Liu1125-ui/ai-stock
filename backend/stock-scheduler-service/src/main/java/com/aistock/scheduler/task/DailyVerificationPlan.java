package com.aistock.scheduler.task;
import java.time.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;import org.slf4j.Logger;import org.slf4j.LoggerFactory;
@Component public final class DailyVerificationPlan {
 private static final Logger LOG=LoggerFactory.getLogger(DailyVerificationPlan.class);private final VerificationScanRepository repo;private final PredictionTaskClient client;private final Clock clock;private final String token;private final boolean enabled;
 public DailyVerificationPlan(VerificationScanRepository repo,PredictionTaskClient client,Clock clock,@Value("${aistock.internal.token:}")String token,@Value("${aistock.scheduler.verification-plan-enabled:false}")boolean enabled){this.repo=repo;this.client=client;this.clock=clock;this.token=token;this.enabled=enabled;}
 @Scheduled(fixedDelayString="${aistock.scheduler.verification-plan-poll-ms:60000}")public synchronized void tick(){if(enabled&&!token.isBlank())runOnce();}
 public synchronized boolean runOnce(){try{var now=clock.instant();var local=now.atZone(ZoneId.of("Asia/Shanghai"));var cutoff=local.toLocalDate().minusDays(local.getHour()<20?1:0);var cursor=repo.cursor(cutoff,now);var page=client.due(cursor.cutoffDate(),cursor.afterId());return repo.publish(cursor,page,clock.instant());}catch(RuntimeException e){LOG.warn("Verification due scan not completed; saved cursor retained");return false;}}
}
