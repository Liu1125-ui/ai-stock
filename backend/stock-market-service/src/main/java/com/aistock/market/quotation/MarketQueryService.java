package com.aistock.market.quotation;
import com.aistock.common.BusinessException;import com.aistock.market.instrument.InstrumentQueryService;import com.aistock.market.indicator.TechnicalIndicators;import com.aistock.market.version.MarketVersionRepository;
import java.time.*;import java.util.*;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;import org.springframework.beans.factory.annotation.Value;
@Service public class MarketQueryService {
 public record Range(String from,String to){}
 public record Summary(String instrumentId,String state,String versionId,String provider,String priceBasis,String lastTradeDate,String close,String freshness,String lastUpdateError,String displayName,boolean displayNameVerified,String listingStatus,String dailyChangePercent,String forecastReadiness){
  public Summary(String instrumentId,String state,String versionId,String provider,String priceBasis,String lastTradeDate,String close,String freshness,String lastUpdateError){
   this(instrumentId,state,versionId,provider,priceBasis,lastTradeDate,close,freshness,lastUpdateError,null,false,"UNKNOWN",null,null);
  }
 }
 public record InstrumentDetail(String instrumentId,String ticker,String displayName,boolean displayNameVerified,String listingStatus,String forecastCapability,String forecastReason,Summary summary){}
 public record Page(List<InstrumentDetail> items,int page,int size,long total){}
 public record DailyResponse(String instrumentId,String state,String versionId,String provider,String priceBasis,String lastTradeDate,String fetchedAt,String checkedThrough,Range requestedRange,Range actualRange,String quality,String freshness,String lastUpdateError,String formulaVersion,List<String> missingTradingDates,String explanation,List<MarketResponseMapper.DailyPoint> bars){}
 private final MarketVersionRepository repository;private final InstrumentQueryService instruments;private final TechnicalIndicators indicators;private final Clock clock;private final int maxMonths,updateHour;private final com.aistock.market.forecast.ForecastInputService forecast;
 public MarketQueryService(MarketVersionRepository repository,InstrumentQueryService instruments,TechnicalIndicators indicators,Clock clock,int maxMonths,int updateHour){
  this(repository,instruments,indicators,clock,maxMonths,updateHour,new com.aistock.market.forecast.ForecastInputService(repository,clock,updateHour));
 }
 @org.springframework.beans.factory.annotation.Autowired
 public MarketQueryService(MarketVersionRepository repository,InstrumentQueryService instruments,TechnicalIndicators indicators,Clock clock,
   @Value("${aistock.market.max-query-months:12}")int maxMonths,@Value("${aistock.market.update-hour:20}")int updateHour,
   com.aistock.market.forecast.ForecastInputService forecast){
  this.repository=repository;this.instruments=instruments;this.indicators=indicators;this.clock=clock;
  this.maxMonths=maxMonths;this.updateHour=updateHour;this.forecast=forecast;
 }
 public Page search(String q,int page,int size){var p=instruments.search(q,page,size);return new Page(p.items().stream().map(this::detail).toList(),page,size,p.total());}
 public InstrumentDetail instrument(String id){return detail(instruments.get(id));}
 private InstrumentDetail detail(InstrumentQueryService.Instrument i){boolean hongKongPilot="HK00700".equals(i.instrumentId());
  return new InstrumentDetail(i.instrumentId(),i.ticker(),i.displayName(),i.displayNameVerified(),i.listingStatus(),
      hongKongPilot?"UNAVAILABLE":"NOT_EVALUATED",
      hongKongPilot?"港股试点暂不支持五日预测":"预测能力以已发布模型目录和输入检查结果为准",summary(i.instrumentId()));}
 public List<Summary> summaries(List<String> ids){if(ids==null||ids.size()>100)throw InstrumentQueryService.invalid();return new LinkedHashSet<>(ids).stream().map(id->{InstrumentQueryService.validateId(id);try{return summaryWithForecastReadiness(id);}catch(BusinessException e){if(e.status()!=HttpStatus.NOT_FOUND)throw e;return emptySummary(id,"UNAVAILABLE");}}).toList();}
 private Summary summaryWithForecastReadiness(String id){
  var s=summary(id);
  var readiness="AVAILABLE".equals(s.state())?forecast.readiness(id):s.state();
  return new Summary(s.instrumentId(),s.state(),s.versionId(),s.provider(),s.priceBasis(),s.lastTradeDate(),s.close(),
      s.freshness(),s.lastUpdateError(),s.displayName(),s.displayNameVerified(),s.listingStatus(),s.dailyChangePercent(),readiness);
 }
 private Summary emptySummary(String id,String state){return new Summary(id,state,null,null,"RAW",null,null,"UNKNOWN",null);}
 private Map<String,Object> head(String id){return repository.jdbc().queryForList("SELECT v.*,h.last_error_code FROM instrument_data_head h JOIN market_data_version v ON v.version_id=h.version_id WHERE h.instrument_id=? AND h.provider='baostock' AND h.price_basis='RAW'",id).stream().findFirst().orElse(null);}
 private Summary summary(String id){
  var i=instruments.get(id);var s=priceSummary(id);
  return new Summary(s.instrumentId(),s.state(),s.versionId(),s.provider(),s.priceBasis(),s.lastTradeDate(),s.close(),s.freshness(),s.lastUpdateError(),i.displayName(),i.displayNameVerified(),i.listingStatus(),s.dailyChangePercent(),null);
 }
 private Summary priceSummary(String id){var v=head(id);if(v==null)return emptySummary(id,"NO_DATA");String version=(String)v.get("version_id");if(!"AVAILABLE".equals(v.get("status")))return emptySummary(id,"UNAVAILABLE");var bars=repository.bars(version);if(bars.isEmpty())return emptySummary(id,"NO_DATA");var last=bars.get(bars.size()-1);var calendar=repository.calendar(version);return new Summary(id,"AVAILABLE",version,(String)v.get("provider"),"RAW",last.date().toString(),MarketResponseMapper.decimal(last.close()),freshness(bars,calendar).state(),(String)v.get("last_error_code"),null,false,"UNKNOWN",dailyChange(bars,calendar),null);}
 static String dailyChange(List<RawBar> bars,List<TradingDay> calendar) {
  if(bars.size()<2)return null;
  var current=bars.get(bars.size()-1);var previous=bars.get(bars.size()-2);
  if(current.tradeStatus()!=1||previous.tradeStatus()!=1||previous.close().signum()<=0)return null;
  var span=calendar.stream().filter(d->!d.date().isBefore(previous.date())&&!d.date().isAfter(current.date())).toList();
  long days=java.time.temporal.ChronoUnit.DAYS.between(previous.date(),current.date())+1;
  if(span.size()!=days||span.size()<2||!span.get(0).open()||!span.get(span.size()-1).open()
      ||span.stream().filter(TradingDay::open).count()!=2)return null;
  return current.close().subtract(previous.close()).movePointRight(2)
          .divide(previous.close(),4,java.math.RoundingMode.HALF_UP).toPlainString();
 }
 public DailyResponse daily(String id,String from,String to,String basis,String versionId){return com.aistock.market.marketlifecycle.MarketEvidenceGate.local(()->{instruments.get(id);if(!"RAW".equals(basis))throw InstrumentQueryService.invalid();var today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));LocalDate end=parse(to,today),start=parse(from,end.minusMonths(3));if(start.isAfter(end)||end.isAfter(today)||start.isBefore(end.minusMonths(maxMonths)))throw InstrumentQueryService.invalid();var requested=new Range(start.toString(),end.toString());Map<String,Object> v;
  if(versionId==null||versionId.isBlank())v=head(id);else {if(!versionId.matches("[a-fA-F0-9-]{36}"))throw InstrumentQueryService.invalid();v=repository.jdbc().queryForList("SELECT v.*,h.last_error_code FROM market_data_version v LEFT JOIN instrument_data_head h ON h.version_id=v.version_id WHERE v.version_id=? AND v.instrument_id=? AND v.price_basis='RAW'",versionId,id).stream().findFirst().orElseThrow(()->new BusinessException("RESOURCE_NOT_FOUND","版本不存在",HttpStatus.NOT_FOUND));}
  if(v==null)return new DailyResponse(id,"NO_DATA",null,null,"RAW",null,null,null,requested,null,"NO_DATA","UNKNOWN",null,"tech-v1",List.of(),"尚无已发布行情",List.of());
  if(!"AVAILABLE".equals(v.get("status")))throw new BusinessException("MARKET_VERSION_UNAVAILABLE","该行情版本不可用",HttpStatus.UNPROCESSABLE_ENTITY);
  String version=(String)v.get("version_id");com.aistock.market.marketlifecycle.MarketEvidenceGate.raw(version);var bars=repository.bars(version);var points=indicators.calculate(bars);var visible=new ArrayList<MarketResponseMapper.DailyPoint>();for(int i=0;i<bars.size();i++){var b=bars.get(i);if(!b.date().isBefore(start)&&!b.date().isAfter(end))visible.add(MarketResponseMapper.point(b,points.get(i)));}
  var fresh=freshness(bars,repository.calendar(version));String last=bars.isEmpty()?null:bars.get(bars.size()-1).date().toString();var actual=visible.isEmpty()?null:new Range(visible.get(0).date(),visible.get(visible.size()-1).date());return new DailyResponse(id,visible.isEmpty()?"EMPTY":"AVAILABLE",version,(String)v.get("provider"),"RAW",last,((java.time.LocalDateTime)v.get("fetched_at")).toInstant(ZoneOffset.UTC).toString(),fresh.expected()==null?null:fresh.expected().toString(),requested,actual,(String)v.get("quality"),fresh.state(),(String)v.get("last_error_code"),"tech-v1",fresh.missing(),fresh.state().equals("UNKNOWN")?"交易日历未覆盖当前检查范围，无法确认新鲜度":"按交易日历及每日更新时点核验",List.copyOf(visible));
 });}
 private LocalDate parse(String value,LocalDate fallback){try{return value==null?fallback:LocalDate.parse(value);}catch(java.time.format.DateTimeParseException e){throw InstrumentQueryService.invalid();}}
 private record Freshness(String state,LocalDate expected,List<String> missing){}
 private Freshness freshness(List<RawBar> bars,List<TradingDay> calendar){var now=ZonedDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai")));var cutoff=now.getHour()<updateHour?now.toLocalDate().minusDays(1):now.toLocalDate();if(calendar.isEmpty()||bars.isEmpty())return new Freshness("UNKNOWN",null,List.of());var first=calendar.get(0).date();if(first.isAfter(bars.get(0).date())||calendar.get(calendar.size()-1).date().isBefore(cutoff))return new Freshness("UNKNOWN",null,List.of());var days=new HashMap<LocalDate,Boolean>();for(var d:calendar)days.put(d.date(),d.open());LocalDate expected=null;var known=new HashSet<LocalDate>();for(var b:bars)known.add(b.date());var missing=new ArrayList<String>();for(var date=first;!date.isAfter(cutoff);date=date.plusDays(1)){if(!days.containsKey(date))return new Freshness("UNKNOWN",null,List.of());if(days.get(date)){expected=date;if(!known.contains(date))missing.add(date.toString());}}
  if(expected==null)return new Freshness("UNKNOWN",null,List.of());return new Freshness(missing.isEmpty()?"FRESH":"STALE",expected,List.copyOf(missing));}
}
