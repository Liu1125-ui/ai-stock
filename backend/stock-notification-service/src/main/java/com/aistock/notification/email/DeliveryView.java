package com.aistock.notification.email;
import java.time.Instant;
import java.util.UUID;
public record DeliveryView(UUID requestId, String state, Instant expiresAt) { }
