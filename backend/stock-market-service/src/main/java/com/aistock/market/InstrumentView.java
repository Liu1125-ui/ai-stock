package com.aistock.market;

public record InstrumentView(String instrumentId, String code, String name,
                             boolean demoOnly, String sourceService) {
    public InstrumentView(String instrumentId, String code, String name, boolean demoOnly) {
        this(instrumentId, code, name, demoOnly, "stock-market-service");
    }
}
