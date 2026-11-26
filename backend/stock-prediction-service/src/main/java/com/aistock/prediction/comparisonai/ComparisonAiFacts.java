package com.aistock.prediction.comparisonai;

import com.aistock.common.BusinessException;
import com.aistock.prediction.comparison.ComparisonRecords;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;

/** Frozen business facts; AI never receives raw series or supplies numbers. */
public final class ComparisonAiFacts {
    public static final String PROMPT="grounded-comparison-v2";
    public record Facts(Map<String,String> values,JsonNode context,String hash) {}
    private static final Map<String,String> LABELS=Map.of("RETURN","区间涨跌幅","VOLATILITY","年化波动率","DRAWDOWN","最大回撤","VOLUME","日均成交量");
    private static final Map<String,String> FIELDS=Map.of("RETURN","intervalReturn","VOLATILITY","annualizedVolatility","DRAWDOWN","maxDrawdown","VOLUME","meanVolume");
    private ComparisonAiFacts() {}

    public static Facts build(JsonNode saved,String model,ObjectMapper mapper) {
        try {
            check(saved!=null&&saved.isObject()&&model!=null&&!model.isBlank()&&model.length()<=128);
            String id=text(saved,"comparisonId");ComparisonRecords.uuid(id);
            check("comparison-v1".equals(text(saved,"formulaVersion"))&&"baostock".equals(text(saved,"provider"))&&"BACKWARD".equals(text(saved,"basis")));
            String sourceHash=text(saved,"sourceHash");check(sourceHash.matches("[a-f0-9]{64}")&&saved.path("annualizationDays").asInt()==252);
            String requested=range(saved.path("requestedRange"));
            String actual=saved.path("actualRange").isNull()?"共同有效端点不足，不能形成可比价格区间":range(saved.path("actualRange"));
            var array=saved.path("stocks");check(array.isArray()&&array.size()>=2&&array.size()<=3);
            var stocks=new ArrayList<JsonNode>();array.forEach(stocks::add);stocks.sort(Comparator.comparing(n->text(n,"instrumentId")));
            var ids=new HashSet<String>();var facts=new TreeMap<String,String>();
            for(int i=0;i<stocks.size();i++) {
                var stock=stocks.get(i);String instrument=text(stock,"instrumentId");check(instrument.matches("(SH|SZ)[0-9]{6}")&&ids.add(instrument));
                for(String key:List.of("RETURN","VOLATILITY","DRAWDOWN","VOLUME")) {
                    var metric=stock.path(FIELDS.get(key));validateMetric(metric,key);
                    String value="AVAILABLE".equals(metric.path("status").asText())?text(metric,"value")+text(metric,"unit"):"不可计算（"+reason(text(metric,"reason"))+")";
                    facts.put("S"+(i+1)+"_"+key,instrument+"的"+LABELS.get(key)+"为"+value);
                }
            }
            facts.put("STOCKS","本次比较"+String.join("、",stocks.stream().map(n->text(n,"instrumentId")).toList()));
            facts.put("REQUEST_RANGE","请求区间为"+requested);facts.put("ACTUAL_RANGE",actual);
            facts.put("BASIS_LIMIT","口径为baostock后复权收盘价，指标按comparison-v1计算，年化约定252个交易日；成交量单位为股，股数不等同于资金流动性");
            facts.put("FRESHNESS_LIMIT",saved.path("actualRange").isNull()
                ?"这份历史记录缺少共同有效区间，行情覆盖以各股保存证据为准"
                :"这份历史记录的共同有效区间截止日为"+text(saved.path("actualRange"),"to")+"。行情覆盖以当次保存证据为准");
            facts.put("HISTORY_LIMIT","历史差异只用于理解这段样本，不能据此确定未来表现或收益");
            facts.put("DATA_LIMIT",issues(stocks));
            facts.put("RETURN_RELATIONS",relations(stocks,"RETURN"));facts.put("VOL_RELATIONS",relations(stocks,"VOLATILITY"));
            facts.put("DRAWDOWN_RELATIONS",relations(stocks,"DRAWDOWN"));facts.put("VOLUME_RELATIONS",relations(stocks,"VOLUME"));
            for(String value:facts.values())check(value.length()<=1000);
            check(mapper.writeValueAsBytes(facts).length<=24576);
            var context=mapper.createObjectNode().put("comparisonId",id).put("sourceHash",sourceHash).put("formulaVersion","comparison-v1").put("promptVersion",PROMPT).put("providerModel",model);
            context.set("facts",mapper.valueToTree(facts));
            return new Facts(Collections.unmodifiableMap(facts),context,ComparisonRecords.hash(context));
        } catch(BusinessException e) {throw e;} catch(Exception e) {throw invalid();}
    }
    private static String range(JsonNode n) {
        LocalDate from=LocalDate.parse(text(n,"from")),to=LocalDate.parse(text(n,"to"));check(!from.isAfter(to));return from+"至"+to;
    }
    private static void validateMetric(JsonNode metric,String key) {
        check(metric.isObject()&&metric.path("observationCount").isIntegralNumber()&&metric.path("observationCount").asInt()>=0);
        check((key.equals("VOLUME")?"股":"%").equals(text(metric,"unit")));
        String status=text(metric,"status");
        if(status.equals("UNAVAILABLE")){check(metric.path("value").isNull());text(metric,"reason");return;}
        check(status.equals("AVAILABLE")&&metric.path("reason").isNull());String value=text(metric,"value");check(value.matches("-?[0-9]{1,16}(\\.[0-9]{1,12})?"));
        BigDecimal number=new BigDecimal(value);check(key.equals("RETURN")?number.compareTo(new BigDecimal("-100"))>0:number.signum()>=0);
    }
    private static String reason(String code) {
        return switch(code){case "NO_COMMON_ENDPOINTS"->"共同有效端点不足";case "PRICE_GAP_OR_SUSPENSION"->"价格缺失或停牌，具体日期见数据说明";case "VOLUME_GAP_OR_SUSPENSION"->"成交量数据缺失或停牌，具体日期见数据说明";case "INSUFFICIENT_RETURNS"->"有效日收益观测不足";default->"固定依据标记不可用";};
    }
    private static String relations(List<JsonNode> stocks,String key) {
        var statements=new ArrayList<String>();
        for(int i=0;i<stocks.size();i++)for(int j=i+1;j<stocks.size();j++) {
            var a=stocks.get(i);var b=stocks.get(j);var x=a.path(FIELDS.get(key));var y=b.path(FIELDS.get(key));String pair=text(a,"instrumentId")+"与"+text(b,"instrumentId")+"的"+LABELS.get(key);
            if(!x.path("status").asText().equals("AVAILABLE")||!y.path("status").asText().equals("AVAILABLE")){statements.add(pair+"不可比较，至少一项不可用");continue;}
            int comparison=new BigDecimal(text(x,"value")).compareTo(new BigDecimal(text(y,"value")));
            statements.add(pair+"按已保存展示值比较，前者"+(comparison==0?"在当前展示精度下与后者相同":comparison>0?"大于后者":"小于后者"));
        }
        return String.join("；",statements);
    }
    private static String issues(List<JsonNode> stocks) {
        var descriptions=new ArrayList<String>();
        for(var stock:stocks) {
            var grouped=new TreeMap<String,List<String>>();var data=stock.path("dataIssues");check(data.isArray()&&data.size()<=740);
            for(var issue:data) {
                String state=text(issue,"state"),field=text(issue,"field"),date=text(issue,"date");LocalDate.parse(date);
                check(Set.of("MISSING","SUSPENDED").contains(state)&&Set.of("backwardClose","volumeShares").contains(field));
                grouped.computeIfAbsent(field+":"+state,k->new ArrayList<>()).add(date);
            }
            for(var entry:grouped.entrySet()) {
                var dates=entry.getValue().stream().sorted().toList();String label=entry.getKey().startsWith("backwardClose")?"价格":"成交量";
                descriptions.add(text(stock,"instrumentId")+label+(entry.getKey().endsWith("SUSPENDED")?"停牌":"数据缺失")+dates.size()+"条，日期示例"+String.join("、",dates.stream().limit(2).toList()));
            }
        }
        return descriptions.isEmpty()?"当前固定样本没有记录价格或成交量异常，结论仍限于这段历史":"已记录"+String.join("；",descriptions)+"。示例不等于全部日期，完整清单见固定比较结果";
    }
    static String text(JsonNode n,String key){var v=n.path(key);check(v.isTextual()&&!v.asText().isBlank()&&v.asText().length()<=128);return v.asText();}
    static void check(boolean condition){if(!condition)throw invalid();}
    public static BusinessException invalid(){return new BusinessException("COMPARISON_AI_EVIDENCE_INVALID","比较依据不完整或不一致，原对比结果仍可查看",HttpStatus.UNPROCESSABLE_ENTITY);}
}
