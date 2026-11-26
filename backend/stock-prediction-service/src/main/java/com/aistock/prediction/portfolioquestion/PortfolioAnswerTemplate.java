package com.aistock.prediction.portfolioquestion;
import com.fasterxml.jackson.databind.*;import com.aistock.common.BusinessException;import org.springframework.http.HttpStatus;import java.util.*;import java.util.regex.*;
import static com.aistock.prediction.portfolioquestion.PortfolioQuestionRecords.*;
public final class PortfolioAnswerTemplate {
 private static final Pattern SLOT=Pattern.compile("\\{\\{([A-Z][A-Z0-9_]{0,31})\\}\\}");
 private static final Pattern UNSAFE=Pattern.compile("[0-9０-９<>\\p{Cntrl}]|[零〇一二三四五六七八九十百千万亿两]+(?:日|天|元|成|倍|个|次|只|%)|高于|低于|等于|上行|下行|上涨|下跌|上升|下降|百分之|浮盈|浮亏|正收益|负收益|买入|卖出|加仓|清仓|稳赚|必赚|保证收益|确定上涨|必然|实时|即时|buy|sell",Pattern.CASE_INSENSITIVE);
 private PortfolioAnswerTemplate(){}
 public static Answer render(JsonNode raw,Facts facts,ObjectMapper mapper){
  if(raw==null||!raw.isObject()||raw.size()!=3||!raw.path("status").isTextual()||!raw.path("topic").isTextual()||!raw.path("paragraphs").isArray())throw invalid();
  String status=raw.path("status").asText(),topic=raw.path("topic").asText();if(!Set.of("ANSWERED","OUT_OF_SCOPE").contains(status)||!Set.of("PROFIT","RETURN","CONCENTRATION","COVERAGE","LIMITATION").contains(topic))throw invalid();
  if("OUT_OF_SCOPE".equals(status)){if(!"LIMITATION".equals(topic)||!raw.path("paragraphs").isEmpty())throw invalid();return new Answer(status,topic,mapper.createArrayNode());}
  if(raw.path("paragraphs").isEmpty()||raw.path("paragraphs").size()>2)throw invalid();
  var paragraphs=mapper.createArrayNode();var all=new HashSet<String>();
  for(var part:raw.path("paragraphs")){
   if(!part.isObject()||part.size()!=2||!part.path("template").isTextual()||!part.path("refs").isArray())throw invalid();String template=part.path("template").asText();if(template.isBlank()||template.length()>500)throw invalid();
   var slots=new LinkedHashSet<String>();var match=SLOT.matcher(template);var out=new StringBuffer();while(match.find()){String key=match.group(1);if(!facts.values().containsKey(key))throw invalid();slots.add(key);match.appendReplacement(out,Matcher.quoteReplacement(facts.values().get(key)));}match.appendTail(out);
   String plain=SLOT.matcher(template).replaceAll("");if(plain.contains("{")||plain.contains("}")||UNSAFE.matcher(plain).find())throw invalid();var declared=new LinkedHashSet<String>();for(var ref:part.path("refs")){if(!ref.isTextual()||!facts.values().containsKey(ref.asText())||!declared.add(ref.asText()))throw invalid();}if(slots.isEmpty()||!slots.equals(declared))throw invalid();all.addAll(slots);var item=paragraphs.addObject().put("text",out.toString());item.set("refs",mapper.valueToTree(slots));
  }
  if(!all.containsAll(Set.of("AS_OF","COVERAGE_LIMIT","DATE_LIMIT","FRESHNESS_LIMIT","BASIS_LIMIT")))throw invalid();
  switch(topic){
   case "PROFIT"->{if(!all.contains("PROFIT_RULE"))throw invalid();}
   case "RETURN"->{if(!all.contains("RETURN_RULE")||all.stream().noneMatch(k->k.endsWith("_RETURN")))throw invalid();}
   case "CONCENTRATION"->{if(!all.containsAll(Set.of("THRESHOLD","CONCENTRATION_RULE"))||all.stream().noneMatch(k->k.matches("S[0-9]{4}_WEIGHT")))throw invalid();}
   default->{}
  }
  return new Answer(status,topic,paragraphs);
 }
 private static BusinessException invalid(){return new BusinessException("AI_OUTPUT_INVALID","回答未通过持仓依据核验，原估值与已有回答保留",HttpStatus.UNPROCESSABLE_ENTITY);}
}
