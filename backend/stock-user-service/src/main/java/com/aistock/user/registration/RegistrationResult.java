package com.aistock.user.registration;
import java.util.UUID;
public record RegistrationResult(UUID userId, boolean replayed) { }
