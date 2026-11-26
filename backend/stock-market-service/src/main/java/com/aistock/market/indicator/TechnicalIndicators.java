package com.aistock.market.indicator;

import com.aistock.market.quotation.IndicatorPoint;
import com.aistock.market.quotation.RawBar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** tech-v1: SMA-seeded EMA and Wilder RSI, evaluated on the whole immutable version. */
@Component
public class TechnicalIndicators {
    private static final int SCALE=12;
    private static final BigDecimal TWO=BigDecimal.valueOf(2);
    public List<IndicatorPoint> calculate(List<RawBar> rows) {
        var result=new ArrayList<IndicatorPoint>();
        var closes=new ArrayList<BigDecimal>();
        var fast=new Ema(12);var slow=new Ema(26);var signal=new Ema(9);
        BigDecimal last=null,gainSum=BigDecimal.ZERO,lossSum=BigDecimal.ZERO,avgGain=null,avgLoss=null;
        int changes=0;
        for(var row:rows) {
            if(row.tradeStatus()==0) {
                result.add(new IndicatorPoint(row.date(),null,null,null,null,null,null,null));
                continue;
            }
            var close=row.close();closes.add(close);
            BigDecimal rsi=null;
            if(last!=null) {
                var delta=close.subtract(last);var gain=delta.max(BigDecimal.ZERO);var loss=delta.negate().max(BigDecimal.ZERO);
                changes++;
                if(changes<=14) {
                    gainSum=gainSum.add(gain);lossSum=lossSum.add(loss);
                    if(changes==14) {avgGain=divide(gainSum,14);avgLoss=divide(lossSum,14);}
                } else {
                    avgGain=divide(avgGain.multiply(BigDecimal.valueOf(13)).add(gain),14);
                    avgLoss=divide(avgLoss.multiply(BigDecimal.valueOf(13)).add(loss),14);
                }
                if(avgGain!=null) {
                    var total=avgGain.add(avgLoss);
                    rsi=total.signum()==0?BigDecimal.valueOf(50):avgGain.multiply(BigDecimal.valueOf(100)).divide(total,SCALE,RoundingMode.HALF_UP);
                }
            }
            last=close;
            var ema12=fast.next(close);var ema26=slow.next(close);
            var dif=ema26==null?null:ema12.subtract(ema26);
            var dea=dif==null?null:signal.next(dif);
            var macd=dea==null?null:dif.subtract(dea).multiply(TWO);
            result.add(new IndicatorPoint(row.date(),ma(closes,5),ma(closes,10),ma(closes,20),dif,dea,macd,rsi));
        }
        return List.copyOf(result);
    }
    private BigDecimal ma(List<BigDecimal> prices,int period) {
        if(prices.size()<period) return null;
        var sum=BigDecimal.ZERO;
        for(int i=prices.size()-period;i<prices.size();i++) sum=sum.add(prices.get(i));
        return divide(sum,period);
    }
    private static BigDecimal divide(BigDecimal value,int denominator) {
        return value.divide(BigDecimal.valueOf(denominator),SCALE,RoundingMode.HALF_UP);
    }
    private static final class Ema {
        private final int period;private int count;private BigDecimal sum=BigDecimal.ZERO,previous;
        Ema(int period){this.period=period;}
        BigDecimal next(BigDecimal value) {
            if(previous==null) {
                sum=sum.add(value);count++;
                if(count==period) previous=divide(sum,period);
            } else previous=previous.add(divide(value.subtract(previous).multiply(TWO),period+1));
            return previous;
        }
    }
}
