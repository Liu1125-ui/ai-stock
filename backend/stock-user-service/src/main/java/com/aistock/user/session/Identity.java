package com.aistock.user.session;
import java.util.UUID;
public record Identity(UUID userId,String role,boolean experimentAllowed,long credentialVersion) { }
