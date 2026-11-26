package com.aistock.market.quotation;
import java.math.BigDecimal;
import java.time.LocalDate;
public record RawBar(LocalDate date,BigDecimal open,BigDecimal high,BigDecimal low,BigDecimal close,
    BigDecimal volumeShares,BigDecimal amountYuan,int tradeStatus,BigDecimal sourceAdjustmentRatio) {}
