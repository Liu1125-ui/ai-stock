package com.aistock.market.instrument;
import com.aistock.common.BusinessException;import java.util.*;import org.springframework.http.HttpStatus;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;
@Service public class InstrumentQueryService {
 public record Instrument(String instrumentId,String ticker,String displayName,boolean displayNameVerified,String listingStatus){}
 public record Page(List<Instrument> items,int page,int size,long total){}
 private final JdbcTemplate jdbc;public InstrumentQueryService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Page search(String q,int page,int size){
  if(page<1||size<1||size>100||page>100000)throw invalid();
  String query=q==null?"":q.strip();
  if(query.codePointCount(0,query.length())>64)throw invalid();
  String code=query.toUpperCase(Locale.ROOT);
  if (code.equals("HK.00700") || code.equals("00700.HK")) code="HK00700";
  String codePrefix=escapeLike(code)+"%";
  String namePrefix=escapeLike(query)+"%";
  String codeContains="%"+codePrefix;
  String nameContains="%"+namePrefix;
  String condition=" demo_only=false AND (instrument_id LIKE ? ESCAPE '!' OR ticker LIKE ? ESCAPE '!' OR display_name LIKE ? ESCAPE '!')";
  var total=jdbc.queryForObject("SELECT COUNT(*) FROM instrument WHERE"+condition,Long.class,codeContains,codeContains,nameContains);
  String order="""
       ORDER BY CASE
           WHEN instrument_id=? OR ticker=? OR SUBSTRING(instrument_id,3)=? OR display_name=? THEN 0
           WHEN instrument_id LIKE ? ESCAPE '!' OR ticker LIKE ? ESCAPE '!' OR SUBSTRING(instrument_id,3) LIKE ? ESCAPE '!' OR display_name LIKE ? ESCAPE '!' THEN 1
           ELSE 2
       END, instrument_id LIMIT ? OFFSET ?
       """;
  var items=jdbc.query("SELECT * FROM instrument WHERE"+condition+order,(rs,n)->map(rs),
      codeContains,codeContains,nameContains,code,code,code,query,
      codePrefix,codePrefix,codePrefix,namePrefix,size,(page-1)*size);
  return new Page(items,page,size,total);
 }
 private String escapeLike(String value){return value.replace("!","!!").replace("%","!%").replace("_","!_");}
 public Instrument get(String id){validateId(id);return jdbc.query("SELECT * FROM instrument WHERE instrument_id=? AND demo_only=false",(rs,n)->map(rs),id).stream().findFirst().orElseThrow(()->new BusinessException("RESOURCE_NOT_FOUND","证券不存在",HttpStatus.NOT_FOUND));}
 private Instrument map(java.sql.ResultSet rs)throws java.sql.SQLException{return new Instrument(rs.getString("instrument_id"),rs.getString("ticker"),rs.getString("display_name"),rs.getBoolean("name_verified"),rs.getString("listing_status"));}
 public static void validateId(String id){if(id==null||!id.matches("(?:SH|SZ)[0-9]{6}|HK00700"))throw invalid();}
 public static BusinessException invalid(){return new BusinessException("VALIDATION_FAILED","请求参数不正确",HttpStatus.BAD_REQUEST);}
}
