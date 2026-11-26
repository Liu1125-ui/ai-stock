package com.aistock.user.session;
import java.time.Instant;
public record IssuedSession(@com.fasterxml.jackson.annotation.JsonIgnore String rawToken,Instant expiresAt,Identity identity) {
    @Override public String toString(){return "IssuedSession[expiresAt="+expiresAt+"]";}
}
