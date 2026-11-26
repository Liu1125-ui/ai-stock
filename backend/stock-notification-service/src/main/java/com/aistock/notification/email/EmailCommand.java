package com.aistock.notification.email;
import java.time.Instant;
import java.util.UUID;

public record EmailCommand(UUID requestId, UUID challengeId, String email, String code, Instant expiresAt) {
    @Override public String toString() { return "EmailCommand[requestId=" + requestId + ",expiresAt=" + expiresAt + "]"; }
}
