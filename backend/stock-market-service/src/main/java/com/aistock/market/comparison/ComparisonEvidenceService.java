package com.aistock.market.comparison;
import com.aistock.market.version.MarketVersionRepository;
import com.fasterxml.jackson.databind.*;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.aistock.market.comparison.ComparisonEvidenceRecords.*;

@Service
public class ComparisonEvidenceService {
    private final MarketVersionRepository market;
    private final Clock clock;
    private final ComparisonEvidenceRepository repository;
    private final ComparisonEvidenceValidator validator;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    public ComparisonEvidenceService(MarketVersionRepository market,Clock clock) {
        this.market=market; this.clock=clock;
        repository=new ComparisonEvidenceRepository(market,mapper,clock);
        validator=new ComparisonEvidenceValidator(clock);
    }
    public Publication publish(Candidate candidate) {
        if(candidate==null||candidate.operationId()==null||!candidate.operationId().matches("[A-Za-z0-9_-]{1,128}")
            ||candidate.rawVersionId()==null||candidate.instrumentId()==null)
            throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
        return repository.publish(candidate,hash(candidate),validator);
    }
    public record RawPoint(java.time.LocalDate date,String open,String high,String low,String close,
        String volumeShares,String amountYuan,int tradeStatus) {}
    private static String number(java.math.BigDecimal value) {
        return value==null?null:value.stripTrailingZeros().toPlainString();
    }
    public record RawWindow(String instrumentId,UUID rawVersionId,String rawContentHash,String provider,
        String priceBasis,java.time.LocalDate from,java.time.LocalDate to,
        List<RawPoint> bars,List<com.aistock.market.quotation.TradingDay> calendar) {}
    public RawWindow rawWindow(String instrument,java.time.LocalDate from,java.time.LocalDate to) {return com.aistock.market.marketlifecycle.MarketEvidenceGate.local(()->{
        if(instrument==null||!instrument.matches("(SH|SZ)[0-9]{6}")||from==null||to==null
            ||from.isAfter(to)||from.isBefore(java.time.LocalDate.of(1990,1,1))||from.isBefore(to.minusYears(1))
            ||to.isAfter(clock.instant().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate()))
            throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
        var pinned=com.aistock.market.marketlifecycle.MarketEvidenceGate.pinned(instrument);var rows=pinned==null?market.jdbc().queryForList("SELECT v.* FROM instrument_data_head h JOIN market_data_version v ON v.version_id=h.version_id JOIN instrument i ON i.instrument_id=v.instrument_id WHERE h.instrument_id=? AND h.provider='baostock' AND h.price_basis='RAW' AND v.instrument_id=h.instrument_id AND v.provider=h.provider AND v.price_basis=h.price_basis AND v.status='AVAILABLE' AND i.demo_only=false AND i.listing_status='ACTIVE'",instrument):market.jdbc().queryForList("SELECT v.* FROM market_data_version v JOIN instrument i ON i.instrument_id=v.instrument_id WHERE v.version_id=? AND v.instrument_id=? AND v.provider='baostock' AND v.price_basis='RAW' AND v.status='AVAILABLE' AND i.demo_only=false AND i.listing_status='ACTIVE'",pinned,instrument);
        if(rows.size()!=1)throw ComparisonEvidenceRepository.error("RESOURCE_NOT_FOUND",org.springframework.http.HttpStatus.NOT_FOUND);
        var row=rows.get(0);String id=(String)row.get("version_id");
        var bars=market.bars(id).stream().filter(b->!b.date().isBefore(from)&&!b.date().isAfter(to)).toList();
        var calendar=market.calendar(id).stream().filter(d->!d.date().isBefore(from)&&!d.date().isAfter(to)).toList();
        var expected=from;
        for(var d:calendar) {
            if(!expected.equals(d.date()))throw ComparisonEvidenceValidator.invalid("COMPARISON_CALENDAR_UNAVAILABLE");
            expected=expected.plusDays(1);
        }
        if(!expected.equals(to.plusDays(1)))throw ComparisonEvidenceValidator.invalid("COMPARISON_CALENDAR_UNAVAILABLE");
        if(bars.isEmpty())throw ComparisonEvidenceValidator.invalid("COMPARISON_RAW_UNAVAILABLE");
        return new RawWindow(instrument,UUID.fromString(id),(String)row.get("content_hash"),"baostock","RAW",from,to,bars.stream().map(b->new RawPoint(b.date(),number(b.open()),number(b.high()),number(b.low()),number(b.close()),number(b.volumeShares()),number(b.amountYuan()),b.tradeStatus())).toList(),calendar);
    });}
    public ComparisonEvidenceOptions.Options options(List<String> ids,String period){return repository.options(ids,period);}
    public Evidence version(UUID id){return com.aistock.market.marketlifecycle.MarketEvidenceGate.local(()->{return repository.version(id);});}
    private String hash(Candidate c){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(c)));}
        catch(Exception e){throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");}
    }
}