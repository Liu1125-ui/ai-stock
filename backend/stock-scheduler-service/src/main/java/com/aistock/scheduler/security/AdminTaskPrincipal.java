package com.aistock.scheduler.security;
import java.util.UUID;
/** Trusted identity populated only after a successful authoritative ADMIN resolution. */
public record AdminTaskPrincipal(UUID userId,long credentialVersion) {}
