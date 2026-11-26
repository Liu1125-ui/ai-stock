package com.aistock.scheduler.adminrefresh;
import com.aistock.common.adminrefresh.AdminMarketRefreshInput;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.json.JsonMapper;import com.fasterxml.jackson.core.JsonParser;
import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
public final class AdminMarketRefreshCursor {
 public record Key(Instant at,String id){}
 public record Cursor(int v,String instrumentId,int size,Key upper,Key after){}
 private final ObjectMapper mapper=JsonMapper.builder().findAndAddModules().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
  .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
  .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
 public String encode(Cursor c){try{validate(c,c.instrumentId(),c.size());return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(c));}catch(Exception e){throw AdminMarketRefreshInput.invalid();}}
 public Cursor decode(String token,String stock,int size){try{if(token==null||token.length()>768||!token.matches("[A-Za-z0-9_-]+"))throw AdminMarketRefreshInput.invalid();var c=mapper.readValue(Base64.getUrlDecoder().decode(token),Cursor.class);validate(c,stock,size);return c;}catch(Exception e){throw AdminMarketRefreshInput.invalid();}}
 private void validate(Cursor c,String stock,int size){AdminMarketRefreshInput.instrument(stock);if(c==null||c.v()!=1||!stock.equals(c.instrumentId())||size<1||size>50||c.size()!=size)throw AdminMarketRefreshInput.invalid();key(c.upper());key(c.after());if(compare(c.after(),c.upper())>0)throw AdminMarketRefreshInput.invalid();}
 private void key(Key k){if(k==null||k.at()==null||!k.at().equals(k.at().truncatedTo(ChronoUnit.MILLIS))||k.at().isBefore(Instant.parse("1000-01-01T00:00:00Z"))||k.at().isAfter(Instant.parse("9999-12-31T23:59:59.999Z")))throw AdminMarketRefreshInput.invalid();AdminMarketRefreshInput.id(k.id());}
 private int compare(Key a,Key b){int n=a.at().compareTo(b.at());return n==0?a.id().compareTo(b.id()):n;}
}