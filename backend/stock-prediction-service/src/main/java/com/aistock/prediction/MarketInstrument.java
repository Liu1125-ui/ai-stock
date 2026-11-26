package com.aistock.prediction;

public record MarketInstrument(String instrumentId, String code, String name,
                               boolean demoOnly, String sourceService) { }
