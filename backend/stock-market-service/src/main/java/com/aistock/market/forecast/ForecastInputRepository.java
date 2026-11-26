package com.aistock.market.forecast;
import com.aistock.common.BusinessException;
import com.aistock.market.version.MarketVersionRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
final class ForecastInputRepository {
 private final JdbcTemplate jdbc;private final MarketVersionRepository market;private final TransactionTemplate tx;
 ForecastInputRepository(MarketVersionRepository market){this.market=market;this.jdbc=market.jdbc();this.tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));}
 ForecastInputService.Publication publish(ForecastCandidate c,String requestHash,String contentHash,String calendarHash,ForecastInputValidator validator,Clock clock){return com.aistock.market.marketlifecycle.MarketEvidenceGate.local(()->{com.aistock.market.marketlifecycle.MarketEvidenceGate.raw(c.rawVersionId());
  return tx.execute(status->{
   jdbc.update("INSERT INTO forecast_input_operation(operation_id,request_hash,created_at) VALUES (?,?,?) ON DUPLICATE KEY UPDATE operation_id=operation_id",c.operationId(),requestHash,Timestamp.from(clock.instant()));
   var op=jdbc.queryForMap("SELECT * FROM forecast_input_operation WHERE operation_id=? FOR UPDATE",c.operationId());
   if(!requestHash.equals(op.get("request_hash")))throw new BusinessException("FORECAST_OPERATION_CONFLICT","同一操作不能改变输入",HttpStatus.CONFLICT);
   if(op.get("input_version_id")!=null)return new ForecastInputService.Publication((String)op.get("input_version_id"),contentHash,true);
   var raw=raw(c.rawVersionId(),true);compatible(raw,c.instrumentId());com.aistock.market.marketlifecycle.MarketEvidenceGate.publishable(jdbc,c.rawVersionId());
   validator.against(c,market.bars(c.rawVersionId()));
   var id=UUID.randomUUID().toString();var origin=c.bars().get(59).raw().date();
   jdbc.update("INSERT INTO forecast_input_version(input_version_id,raw_version_id,instrument_id,provider,definition_code,content_hash,calendar_hash,source_payload_hash,fetched_at,origin_date,calendar_start,calendar_end,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'AVAILABLE') ON DUPLICATE KEY UPDATE input_version_id=input_version_id",
    id,c.rawVersionId(),c.instrumentId(),c.provider(),c.definition(),contentHash,calendarHash,c.sourcePayloadHash(),Timestamp.from(c.fetchedAt()),origin,c.calendar().get(0).date(),c.calendar().get(c.calendar().size()-1).date());
   var saved=jdbc.queryForObject("SELECT input_version_id FROM forecast_input_version WHERE raw_version_id=? AND definition_code=? AND content_hash=? FOR UPDATE",String.class,c.rawVersionId(),c.definition(),contentHash);
   if(id.equals(saved)){
    for(var p:c.bars())jdbc.update("INSERT INTO forecast_input_adjustment VALUES (?,?,?,?)",id,p.raw().date(),p.backwardClose(),validator.ratio(p));
    for(var d:c.calendar())jdbc.update("INSERT INTO forecast_input_calendar VALUES (?,?,?)",id,d.date(),d.open());
   }
   jdbc.update("UPDATE forecast_input_operation SET input_version_id=? WHERE operation_id=?",saved,c.operationId());
   return new ForecastInputService.Publication(saved,contentHash,false);
  });
 });}
 Map<String,Object> rawHead(String instrument){
  var pinned=com.aistock.market.marketlifecycle.MarketEvidenceGate.pinned(instrument);if(pinned!=null){var row=raw(pinned,false);compatible(row,instrument);return row;}
  var rows=jdbc.queryForList("SELECT v.* FROM market_data_version v JOIN instrument_data_head h ON h.version_id=v.version_id WHERE h.instrument_id=? AND h.provider='baostock' AND h.price_basis='RAW'",instrument);
  if(rows.isEmpty())throw missing();var raw=rows.get(0);compatible(raw,instrument);return raw;
 }
 Map<String,Object> raw(String id,boolean lock){
  var rows=jdbc.queryForList("SELECT * FROM market_data_version WHERE version_id=?"+(lock?" FOR SHARE":""),id);
  if(rows.isEmpty())throw missing();return rows.get(0);
 }
 void compatible(Map<String,Object> raw,String instrument){
  com.aistock.market.marketlifecycle.MarketEvidenceGate.raw((String)raw.get("version_id"));
  if(!instrument.equals(raw.get("instrument_id")))throw missing();
  if(!"AVAILABLE".equals(raw.get("status")))throw ForecastInputValidator.error("MARKET_VERSION_UNAVAILABLE");
  if(!"baostock".equals(raw.get("provider")) || !"RAW".equals(raw.get("price_basis")))throw ForecastInputValidator.error("FORECAST_SOURCE_INCOMPATIBLE");
 }
 Map<String,Object> find(String instrument,String id){
  List<Map<String,Object>> rows;
  var pinned=com.aistock.market.marketlifecycle.MarketEvidenceGate.pinned(instrument);
  if(id==null&&pinned!=null)rows=jdbc.queryForList("SELECT * FROM forecast_input_version WHERE raw_version_id=? AND instrument_id=? AND status='AVAILABLE' ORDER BY fetched_at DESC,input_version_id LIMIT 1",pinned,instrument);
  else if(id!=null)rows=jdbc.queryForList("SELECT * FROM forecast_input_version WHERE input_version_id=? AND instrument_id=?",id,instrument);
  else rows=jdbc.queryForList("SELECT f.* FROM forecast_input_version f JOIN instrument_data_head h ON h.version_id=f.raw_version_id WHERE h.instrument_id=? AND h.provider='baostock' AND h.price_basis='RAW' AND f.status='AVAILABLE' ORDER BY f.fetched_at DESC,f.input_version_id LIMIT 1",instrument);
  if(rows.isEmpty()){if(id!=null)throw missing();throw ForecastInputValidator.error("FORECAST_INPUT_NOT_AVAILABLE");}
  var f=rows.get(0);if(!"AVAILABLE".equals(f.get("status")))throw ForecastInputValidator.error("FORECAST_INPUT_NOT_AVAILABLE");return f;
 }
 List<Map<String,Object>> adjustments(String id){return jdbc.queryForList("SELECT * FROM forecast_input_adjustment WHERE input_version_id=? ORDER BY trade_date",id);}
 List<com.aistock.market.quotation.TradingDay> calendar(String id){return jdbc.query("SELECT * FROM forecast_input_calendar WHERE input_version_id=? ORDER BY calendar_date",(rs,n)->new com.aistock.market.quotation.TradingDay(rs.getDate("calendar_date").toLocalDate(),rs.getBoolean("is_open")),id);}
 private BusinessException missing(){return new BusinessException("RESOURCE_NOT_FOUND","预测输入或行情版本不存在",HttpStatus.NOT_FOUND);}
}
