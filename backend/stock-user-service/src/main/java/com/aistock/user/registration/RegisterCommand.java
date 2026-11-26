package com.aistock.user.registration;
import java.util.UUID;
public record RegisterCommand(@com.fasterxml.jackson.annotation.JsonAlias("registrationRequestId") UUID requestId, UUID challengeId, String email, String code, String password, String nickname) {
    @Override public String toString() { return "RegisterCommand[requestId=" + requestId + ",challengeId=" + challengeId + "]"; }
}
