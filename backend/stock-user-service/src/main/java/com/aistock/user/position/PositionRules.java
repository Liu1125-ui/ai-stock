package com.aistock.user.position;
import java.math.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import com.aistock.common.BusinessException;import org.springframework.http.HttpStatus;import static com.aistock.user.position.PositionRecords.*;
public final class PositionRules {
 private PositionRules(){}
 public static BusinessException invalid(){return new BusinessException("POSITION_INPUT_INVALID","持仓数量、成本或版本不符合要求",HttpStatus.UNPROCESSABLE_ENTITY);}
 public static long quantity(String text,long max){if(text==null||!text.matches("[1-9][0-9]{0,18}"))throw invalid();try{long n=Long.parseLong(text);if(n>max)throw invalid();return n;}catch(NumberFormatException e){throw invalid();}}
 public static BigDecimal cost(String text,BigDecimal max){if(text==null||text.length()>30||!text.matches("(0|[1-9][0-9]*)(\\.[0-9]{1,6})?"))throw invalid();var n=new BigDecimal(text);if(n.compareTo(max)>0)throw invalid();return n.setScale(6);}
 public static void structure(PositionMutation m){
  if(m==null||m.requestId()==null||m.action()==null||!Set.of("CREATE","UPDATE","DELETE").contains(m.action()))throw invalid();
  if(m.action().equals("CREATE")){if(m.positionId()!=null||m.expectedRevision()!=null||m.instrumentId()==null||!m.instrumentId().matches("(SH|SZ)[0-9]{6}"))throw invalid();}
  else if(m.positionId()==null||m.expectedRevision()==null||m.expectedRevision()<1||m.instrumentId()!=null)throw invalid();
  if(m.action().equals("DELETE")){if(m.quantity()!=null||m.averageCost()!=null)throw invalid();}else{quantity(m.quantity(),Long.MAX_VALUE);cost(m.averageCost(),new BigDecimal("99999999999999.999999"));}
 }
 public static String hash(PositionMutation m){structure(m);String value=String.join("|",m.action(),m.positionId()==null?"":m.positionId().toString(),m.instrumentId()==null?"":m.instrumentId(),m.quantity()==null?"":Long.toString(quantity(m.quantity(),Long.MAX_VALUE)),m.averageCost()==null?"":cost(m.averageCost(),new BigDecimal("99999999999999.999999")).toPlainString(),m.expectedRevision()==null?"":m.expectedRevision().toString());try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
