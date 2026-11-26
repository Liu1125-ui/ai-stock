package com.aistock.scheduler.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingCandidateRegistrationTest {
    @Test void acceptsSealedLegacyAndRollingCandidates() {
        assertTrue(TrainingCandidateRegistrationWorker.isCandidateArtifactId(
            "transformer-s3-2023-seed42-0123456789abcdef"));
        assertTrue(TrainingCandidateRegistrationWorker.isCandidateArtifactId(
            "transformer-s3-rolling-20260331-seed42-0123456789abcdef"));
        assertFalse(TrainingCandidateRegistrationWorker.isCandidateArtifactId(
            "transformer-s3-rolling-20260331-seed42"));
        assertFalse(TrainingCandidateRegistrationWorker.isCandidateArtifactId("../manifest.json"));
        assertFalse(TrainingCandidateRegistrationWorker.isCandidateArtifactId(null));
    }
}
