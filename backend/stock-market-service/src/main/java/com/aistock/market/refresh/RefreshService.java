package com.aistock.market.refresh;
import com.aistock.common.BusinessException;
import com.aistock.market.ingestion.*;
import com.aistock.market.quotation.StockValuationStore;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
@Service public class RefreshService {
    private static final int FIRST_HISTORY_DAYS=180;
    private final RefreshRepository repository;
    private final PublicationService publisher;
    private final MarketCollector collector;
    private final Clock clock;
    private final StockValuationStore valuations;

    @Autowired
    public RefreshService(RefreshRepository repository, PublicationService publisher, MarketCollector collector,
            Clock clock, StockValuationStore valuations) {
        this.repository=repository;
        this.publisher=publisher;
        this.collector=collector;
        this.clock=clock;
        this.valuations=valuations;
    }
    public RefreshService(RefreshRepository repository, PublicationService publisher, MarketCollector collector, Clock clock) {
        this(repository, publisher, collector, clock, null);
    }
    public RefreshResult refresh(RefreshRequest r) {
        validate(r);var replay=repository.begin(r,clock);if(replay!=null)return replay;
        RefreshRepository.Head head=null;
        try {
            checkLease(r);head=repository.currentHead(r.instrumentId());
            boolean first=head==null;
            if(first && (r.adminConstraint()!=null || !repository.canStart(r.instrumentId())))throw RefreshRepository.missingHead();
            checkAdminConstraint(r,head);
            var from=first ? r.targetDate().minusDays(FIRST_HISTORY_DAYS) : head.start();
            if(from.isBefore(LocalDate.of(1990,1,1)))from=LocalDate.of(1990,1,1);
            if(r.targetDate().isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from,r.targetDate())>1500)throw invalid("REFRESH_RANGE_INVALID");
            var source=collector.collect(r.operationId(),r.instrumentId(),from,r.targetDate());checkLease(r);
            if(source==null || !r.operationId().equals(source.operationId()))throw invalid("SOURCE_INVALID");
            if(!"AVAILABLE".equals(source.state())) {
                if("LEGITIMATE_EMPTY".equals(source.state()))throw invalid("MARKET_DATA_INCOMPLETE");
                var code=Set.of("SOURCE_TIMEOUT","SOURCE_BUSY","SOURCE_UNAVAILABLE","SOURCE_INVALID").contains(source.state())?source.state():"SOURCE_INVALID";throw invalid(code);
            }
            var c=source.candidate();complete(c,r,from,first);
            var calendar=first ? c.calendar().stream().filter(day->!day.date().isBefore(c.bars().get(0).date())).toList() : c.calendar();
            var guarded=new MarketCandidate(c.operationId(),c.instrumentId(),c.ticker(),c.displayName(),c.displayNameVerified(),c.listingStatus(),c.provider(),c.priceBasis(),c.fetchedAt(),c.sourceVersionId(),c.sourcePayloadHash(),c.bars(),calendar,first ? 0L : head.revision());
            var publication=publisher.publish(guarded,r.leaseUntil(),clock);
            saveValuation(guarded, publication.versionId(), source.valuation());
            return new RefreshResult(r.operationId(),"AVAILABLE",publication.versionId(),null);
        } catch(BusinessException e) {
            return failed(r,head,r.adminConstraint()!=null && "REAL_MARKET_VERSION_REQUIRED".equals(e.code())?"ADMIN_REFRESH_CONTEXT_CHANGED":e.code());
        } catch(Exception e) {
            return failed(r,head,"SOURCE_UNAVAILABLE");
        }
    }
    private void saveValuation(MarketCandidate candidate, String version, StockValuationStore.Sample sample) {
        if (valuations == null || sample == null) return;
        try {
            var target = valuations.target(candidate.instrumentId(), version);
            valuations.save(target, sample, candidate.fetchedAt());
        } catch (RuntimeException unavailable) {
            org.slf4j.LoggerFactory.getLogger(RefreshService.class).warn(
                    "Stock valuation was not saved for {}", candidate.instrumentId());
        }
    }

    private RefreshResult failed(RefreshRequest r,RefreshRepository.Head head,String code) {
        var result=repository.finish(new RefreshResult(r.operationId(),"FAILED",null,code),clock.instant());
        if(head!=null && "FAILED".equals(result.state()) && !code.equals("ATTEMPT_EXPIRED"))
            repository.failure(r.instrumentId(),head.revision(),code);
        return result;
    }
    private void complete(MarketCandidate c,RefreshRequest r,LocalDate start,boolean first) {
        if(c==null || !r.operationId().equals(c.operationId()) || !r.instrumentId().equals(c.instrumentId()) || !"baostock".equals(c.provider()) || !"RAW".equals(c.priceBasis()) || c.bars()==null || c.bars().isEmpty() || c.calendar()==null)throw invalid("SOURCE_INVALID");
        new CandidateValidator().validate(c);
        var firstBar=c.bars().get(0).date();
        if(!first && !firstBar.equals(start))throw invalid("MARKET_DATA_INCOMPLETE");
        var barDates=new HashSet<LocalDate>();for(var bar:c.bars()){if(bar.date().isBefore(start)||bar.date().isAfter(r.targetDate()))throw invalid("SOURCE_INVALID");barDates.add(bar.date());}
        var expected=start;
        for(var day:c.calendar()) {if(!day.date().equals(expected) || day.date().isAfter(r.targetDate()))throw invalid("MARKET_DATA_INCOMPLETE");if(day.open()&&(!first||!day.date().isBefore(firstBar))&&!barDates.contains(day.date()))throw invalid("MARKET_DATA_INCOMPLETE");expected=expected.plusDays(1);}
        if(!expected.equals(r.targetDate().plusDays(1)))throw invalid("MARKET_DATA_INCOMPLETE");
    }
    private void validate(RefreshRequest r) {
        if(r==null || r.operationId()==null || !r.operationId().matches("[A-Za-z0-9_-]{1,128}") || r.instrumentId()==null || !r.instrumentId().matches("(SH|SZ)[0-9]{6}") || r.targetDate()==null || r.targetDate().isBefore(LocalDate.of(1990,1,1)) || r.targetDate().isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")))) || r.leaseUntil()==null || r.leaseUntil().isAfter(clock.instant().plusSeconds(120)))throw invalid("REFRESH_REQUEST_INVALID");
    }
    private void checkAdminConstraint(RefreshRequest r,RefreshRepository.Head head) {
        var c=r.adminConstraint();if(c==null)return;
        if(!c.baseVersionId().equals(head.versionId())||c.headRevision()!=head.revision()||!c.coverageStart().equals(head.start()))throw invalid("ADMIN_REFRESH_CONTEXT_CHANGED");
    }
    private void checkLease(RefreshRequest r){if(!clock.instant().isBefore(r.leaseUntil()))throw invalid("ATTEMPT_EXPIRED");}
    private BusinessException invalid(String code){return new BusinessException(code,"行情刷新未通过检查，已有版本保留",HttpStatus.UNPROCESSABLE_ENTITY);}
}
