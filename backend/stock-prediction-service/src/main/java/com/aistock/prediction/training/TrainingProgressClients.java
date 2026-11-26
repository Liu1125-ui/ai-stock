package com.aistock.prediction.training;

import com.aistock.common.ApiResponse;
import com.aistock.common.training.TrainingProgress;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

public final class TrainingProgressClients {
    private TrainingProgressClients() {}

    @FeignClient(name="stock-scheduler-service",contextId="trainingProgressScheduler",
        url="${aistock.training.scheduler-url:http://127.0.0.1:18084}")
    public interface Scheduler {
        @GetMapping("/internal/v1/tasks/training-inbox/{taskId}/status")
        ApiResponse<TrainingProgress> status(@PathVariable("taskId") String taskId,@RequestHeader("X-Service-Token") String token);
    }

    @FeignClient(name="training-algorithm",contextId="trainingProgressAlgorithm",
        url="${aistock.algorithm.url:http://127.0.0.1:18090}")
    public interface Algorithm {
        @GetMapping("/internal/v1/training-executions/{taskId}/metrics")
        ApiResponse<JsonNode> metrics(@PathVariable("taskId") String taskId,@RequestHeader("X-Service-Token") String token);
    }
}
