package com.aistock.market.ingestion;

import com.aistock.common.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CandidateValidator {
    public void validate(MarketCandidate candidate) {
        if(candidate==null || candidate.operationId()==null || !candidate.operationId().matches("[A-Za-z0-9_-]{1,128}")
            || candidate.instrumentId()==null || !candidate.instrumentId().matches("(SH|SZ)[0-9]{6}")
            || !candidate.instrumentId().equals(candidate.ticker()) || candidate.displayName()==null
            || candidate.displayName().isBlank() || candidate.displayName().codePointCount(0,candidate.displayName().length())>128
            || candidate.displayName().codePoints().anyMatch(Character::isISOControl)
            || !Set.of("ACTIVE","DELISTED","UNKNOWN").contains(candidate.listingStatus()==null?"":candidate.listingStatus())
            || !Set.of("baostock","tushare").contains(candidate.provider()==null?"":candidate.provider())
            || !"RAW".equals(candidate.priceBasis()) || candidate.fetchedAt()==null
            || candidate.sourcePayloadHash()==null || !candidate.sourcePayloadHash().matches("[a-fA-F0-9]{64}")
            || candidate.sourceVersionId()!=null && candidate.sourceVersionId().length()>128
            || candidate.expectedRevision()!=null && candidate.expectedRevision()<0
            || candidate.bars()==null || candidate.bars().isEmpty() || candidate.calendar()==null) throw invalid();
        if(candidate.bars().size()>1500 || candidate.calendar().size()>3000)
            throw new BusinessException("MARKET_PAYLOAD_TOO_LARGE","行情候选超过当前处理上限",HttpStatus.PAYLOAD_TOO_LARGE);
        if(candidate.fetchedAt().isAfter(java.time.Instant.now().plusSeconds(300))) throw invalid();
        var latest=candidate.fetchedAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        var earliest=LocalDate.of(1990,1,1);
        LocalDate previous=null;
        for(var row:candidate.bars()) {
            if(row==null || row.date()==null || row.date().isBefore(earliest) || row.date().isAfter(latest)
                || previous!=null && !row.date().isAfter(previous)
                || !decimal(row.open(),20,6,true) || !decimal(row.high(),20,6,true)
                || !decimal(row.low(),20,6,true) || !decimal(row.close(),20,6,true)
                || row.high().compareTo(row.open().max(row.close()).max(row.low()))<0
                || row.low().compareTo(row.open().min(row.close()).min(row.high()))>0
                || row.tradeStatus()!=0 && row.tradeStatus()!=1) throw invalid();
            if(row.volumeShares()==null && row.tradeStatus()!=0 || row.amountYuan()==null && row.tradeStatus()!=0
                || row.volumeShares()!=null && !decimal(row.volumeShares(),20,4,false)
                || row.amountYuan()!=null && !decimal(row.amountYuan(),24,6,false)
                || row.tradeStatus()==1 && (row.volumeShares().signum()==0 || row.amountYuan().signum()==0)
                || row.sourceAdjustmentRatio()!=null && !decimal(row.sourceAdjustmentRatio(),24,12,true)) throw invalid();
            previous=row.date();
        }
        previous=null;
        var closedDays=new HashSet<LocalDate>();
        for(var day:candidate.calendar()) {
            if(day==null || day.date()==null || day.date().isBefore(earliest) || day.date().isAfter(latest)
                || previous!=null && !day.date().isAfter(previous)) throw invalid();
            if(!day.open()) closedDays.add(day.date());
            previous=day.date();
        }
        if(candidate.bars().stream().anyMatch(row->closedDays.contains(row.date()))) throw invalid();
    }
    private boolean decimal(BigDecimal value,int precision,int scale,boolean positive) {
        if(value==null || positive && value.signum()<=0 || !positive && value.signum()<0) return false;
        var exact=value.stripTrailingZeros();
        return exact.scale()<=scale && exact.precision()-exact.scale()<=precision-scale;
    }
    private BusinessException invalid() {
        return new BusinessException("MARKET_DATA_INVALID","行情字段、日期或数值口径不符合发布条件",HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
