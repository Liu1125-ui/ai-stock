package com.aistock.market.quotation;
import java.math.BigDecimal;
import java.time.LocalDate;
public record IndicatorPoint(LocalDate date,BigDecimal ma5,BigDecimal ma10,BigDecimal ma20,
    BigDecimal dif,BigDecimal dea,BigDecimal macd,BigDecimal rsi14) {}
