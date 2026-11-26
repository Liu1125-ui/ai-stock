package com.aistock.prediction.comparisonai;

import com.aistock.common.BusinessException;
import com.aistock.prediction.explanation.ExplanationTemplate;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

public final class ComparisonAiTemplate {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ComparisonAiTemplate.class);
    private static final Pattern SLOT=Pattern.compile("\\{\\{([A-Z][A-Z0-9_]{0,31})\\}\\}");
    private static final Pattern UNSUPPORTED=Pattern.compile("更强|更弱|更好|更差|更稳|优于|劣于|排名|推荐|看好|领先|落后|并非|不是|否认|反之|所以|因此|导致|肯定|必然|预示|将会|预测|实时|即时");
    private ComparisonAiTemplate() {}
    public static Set<String> required(String kind,Map<String,String> facts) {
        var keys=new LinkedHashSet<String>();
        switch(kind){case "overview"->keys.addAll(List.of("STOCKS","REQUEST_RANGE","ACTUAL_RANGE","RETURN_RELATIONS"));case "readout"->keys.addAll(List.of("VOL_RELATIONS","DRAWDOWN_RELATIONS"));case "risk"->keys.addAll(List.of("BASIS_LIMIT","VOLUME_RELATIONS","DATA_LIMIT","HISTORY_LIMIT","FRESHNESS_LIMIT"));default->throw invalid();}
        for(int i=1;i<=3;i++)if(facts.containsKey("S"+i+"_RETURN")) {
            if(kind.equals("overview"))keys.add("S"+i+"_RETURN");
            if(kind.equals("readout")){keys.add("S"+i+"_VOLATILITY");keys.add("S"+i+"_DRAWDOWN");}
            if(kind.equals("risk"))keys.add("S"+i+"_VOLUME");
        }
        return Collections.unmodifiableSet(keys);
    }
    public static ArrayNode render(JsonNode raw,ComparisonAiFacts.Facts facts,ObjectMapper mapper) {
        try {
            var rendered=ExplanationTemplate.render(raw,facts.values(),mapper);
            for(var part:raw.path("paragraphs")) {
                String template=part.path("template").asText();String plain=SLOT.matcher(template).replaceAll("");
                if(UNSUPPORTED.matcher(plain).find())throw invalid("UNSUPPORTED_LITERAL");
                var declared=new HashSet<String>();part.path("refs").forEach(n->declared.add(n.asText()));
                if(!declared.containsAll(required(part.path("kind").asText(),facts.values())))throw invalid("REQUIRED_REFERENCE_MISSING");
                for(String clause:template.split("[。；;！!？?：:]",-1)) {
                    String trimmed=clause.strip();
                    if(!trimmed.isEmpty()&&!trimmed.matches("(?:先看|再看|同时|另外)?\\{\\{[A-Z][A-Z0-9_]{0,31}\\}\\}"))throw invalid("UNSUPPORTED_CLAUSE");
                }
            }
            for(var part:rendered)if(part.path("text").asText().length()>2000)throw invalid("RENDERED_LENGTH");
            return rendered;
        }catch(BusinessException e){throw invalid();}
    }
    private static BusinessException invalid(String reason){LOG.warn("Comparison explanation rejected rule={}",reason);return invalid();}
    static BusinessException invalid(){return new BusinessException("AI_OUTPUT_INVALID","比较解释未通过一致性核验，原结果与已有解释保留",HttpStatus.UNPROCESSABLE_ENTITY);}
}
