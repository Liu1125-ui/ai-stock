package com.aistock.market.quotation;
import java.math.BigDecimal;
public final class MarketResponseMapper {
 private MarketResponseMapper(){}
 public record DailyPoint(String date,String open,String high,String low,String close,String volumeShares,String amountYuan,int tradeStatus,String ma5,String ma10,String ma20,String dif,String dea,String macd,String rsi14){}
 public static String decimal(BigDecimal n){return n==null?null:n.toPlainString();}
 public static DailyPoint point(RawBar b,IndicatorPoint i){return new DailyPoint(b.date().toString(),decimal(b.open()),decimal(b.high()),decimal(b.low()),decimal(b.close()),decimal(b.volumeShares()),decimal(b.amountYuan()),b.tradeStatus(),decimal(i.ma5()),decimal(i.ma10()),decimal(i.ma20()),decimal(i.dif()),decimal(i.dea()),decimal(i.macd()),decimal(i.rsi14()));}
}
