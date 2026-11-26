package com.aistock.prediction.training;

import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;

final class WeeklyTrainingScan {
    private final WeeklyTrainingPlanner planner;
    private final TrainingAdmissionRepository admissions;
    private final WeeklyTrainingEvidence evidence;
    private final UUID owner;

    WeeklyTrainingScan(WeeklyTrainingPlanner planner,TrainingAdmissionRepository admissions,
                       WeeklyTrainingEvidence evidence,UUID owner) {
        this.planner=planner;
        this.admissions=admissions;
        this.evidence=evidence;
        this.owner=owner;
    }

    @Scheduled(fixedDelayString="${aistock.training.weekly-scan-delay-ms:3600000}",
        initialDelayString="${aistock.training.weekly-scan-delay-ms:3600000}")
    public void scan() {
        var snapshot=evidence.current();
        var plan=planner.check(snapshot);
        if(snapshot!=null&&snapshot.readiness()==WeeklyTrainingPlanner.Readiness.READY
            &&"PLANNED".equals(plan.state()))
            planner.admit(snapshot,owner,admissions);
    }
}
