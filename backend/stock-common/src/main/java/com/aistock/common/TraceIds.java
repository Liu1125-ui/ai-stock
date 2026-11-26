package com.aistock.common;

import java.util.UUID;
import java.util.regex.Pattern;

public final class TraceIds {
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private TraceIds() { }
    public static String normalize(String candidate) {
        return candidate != null && SAFE.matcher(candidate).matches()
                ? candidate : UUID.randomUUID().toString();
    }
}
