package com.aistock.user.registration;
import java.time.Instant;
import java.util.UUID;
public record ChallengeView(UUID challengeId, Instant expiresAt, Instant resendAfter, String state) { }
