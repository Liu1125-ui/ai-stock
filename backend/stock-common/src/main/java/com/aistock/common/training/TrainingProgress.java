package com.aistock.common.training;

import java.time.Instant;
import java.util.UUID;

public record TrainingProgress(String taskId,String state,Instant startedAt,Instant finishedAt,
                               String checkpointHash,UUID registeredModelId) {}
