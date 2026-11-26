package com.aistock.scheduler.task;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component public class DailyMarketPlan {
    private final TaskRepository repository;private final boolean enabled;private final List<String> ids;
    public DailyMarketPlan(TaskRepository repository,@Value("${aistock.scheduler.plan-enabled:false}")boolean enabled,@Value("${aistock.scheduler.instruments:}")String instruments){
        this.repository=repository;this.enabled=enabled;this.ids=Arrays.stream(instruments.split(",")).map(String::trim).filter(s->!s.isEmpty()).distinct().toList();
        for(var id:ids)if(!id.matches("(SH|SZ)[0-9]{6}"))throw new IllegalArgumentException("Invalid update plan instrument");
    }
    @Scheduled(fixedDelayString="${aistock.scheduler.plan-poll-ms:60000}")
    public void tick(){if(enabled)repository.enqueueDaily(ids,Instant.now());}
}
