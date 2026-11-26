package com.aistock.prediction.verification;
import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import static com.aistock.prediction.verification.VerificationRecords.*;
final class VerificationValidator {
 record Fixed(BigDecimal origin,List<BigDecimal> prices){}
 Fixed validate(JsonNode snapshot,Evidence e){
  try{
   if(snapshot==null||e==null||e.versionId()==null||!e.versionId().matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")||e.instrumentId()==null||!e.instrumentId().matches("(SH|SZ)[0-9]{6}")||!"baostock".equals(e.provider())||!"baostock-backward-close-over-raw-v1".equals(e.definition())||e.originDate()==null||e.dueThrough()==null||e.dueThrough().isBefore(e.originDate())||e.dueThrough().isAfter(e.originDate().plusDays(90))||e.targetDates()==null||e.targetDates().size()!=5||e.observations()==null||e.observations().size()>5)throw invalid();
   var input=snapshot.path("input");var prediction=snapshot.path("prediction");
   if(!input.isObject()||!prediction.isObject()||!prediction.path("formalInference").isBoolean()||!prediction.path("formalInference").booleanValue()||!prediction.path("validationOnly").isBoolean()||prediction.path("validationOnly").booleanValue()||!"ORIGIN_SHARE_BASIS".equals(text(prediction,"referenceBasis"))||!e.instrumentId().equals(text(input,"instrumentId"))||!e.instrumentId().equals(text(prediction,"instrumentId"))||!e.originDate().toString().equals(text(input,"originDate"))||!e.originDate().toString().equals(text(prediction,"originDate")))throw invalid();
   var p0=number(text(input,"originRawClose"));if(p0.compareTo(number(text(prediction,"originRawClose")))!=0)throw invalid();positive(e.originRawClose());positive(e.originBackwardClose());
   var dates=input.path("targetDates");var points=prediction.path("points");if(!dates.isArray()||dates.size()!=5||!points.isArray()||points.size()!=5)throw invalid();
   var prices=new ArrayList<BigDecimal>();LocalDate last=e.originDate();
   for(int i=0;i<5;i++){
    var date=e.targetDates().get(i);var p=points.get(i);
    if(date==null||!date.isAfter(last)||date.isAfter(e.originDate().plusDays(90))||!dates.get(i).isTextual()||!date.toString().equals(dates.get(i).textValue())||!p.path("step").isIntegralNumber()||!p.path("step").canConvertToInt()||p.path("step").intValue()!=i+1||!date.toString().equals(text(p,"targetDate")))throw invalid();
    prices.add(number(text(p,"referencePrice")));last=date;
   }
   var seen=new HashSet<LocalDate>();
   for(var d:e.observations()){
    if(d==null||d.date()==null||!e.targetDates().contains(d.date())||d.date().isAfter(e.dueThrough())||!seen.add(d.date())||d.state()==null||d.state()==PointState.WAITING)throw invalid();
    if(d.state()==PointState.VERIFIED){positive(d.rawClose());positive(d.backwardClose());}
    else {if(d.rawClose()!=null)positive(d.rawClose());if(d.backwardClose()!=null)positive(d.backwardClose());}
   }
   return new Fixed(p0,List.copyOf(prices));
  }catch(BusinessException ex){throw ex;}catch(RuntimeException ex){throw invalid();}
 }
 private String text(JsonNode node,String key){var value=node.get(key);if(value==null||!value.isTextual())throw invalid();return value.textValue();}
 private BigDecimal number(String text){if(text==null||!text.matches("[0-9]{1,16}(\\.[0-9]{1,12})?"))throw invalid();var n=new BigDecimal(text);positive(n);return n;}
 private void positive(BigDecimal n){if(n==null||n.signum()<=0||n.precision()>28||n.scale()>12||n.precision()-n.scale()>16)throw invalid();}
 static BusinessException invalid(){return new BusinessException("VERIFICATION_INPUT_INVALID","预测快照与真实验证证据不匹配或数值无效",HttpStatus.UNPROCESSABLE_ENTITY);}
}
