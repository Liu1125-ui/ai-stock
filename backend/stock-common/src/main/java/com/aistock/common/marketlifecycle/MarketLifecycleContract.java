package com.aistock.common.marketlifecycle;

import com.aistock.common.BusinessException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import static com.aistock.common.marketlifecycle.MarketUseRecords.*;

public final class MarketLifecycleContract {
    private MarketLifecycleContract() {}
    public static final int MAX_VERSIONS=100;
    public static final String PROTOCOL="market-version-use-v1";
    private static final ObjectMapper JSON=new ObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Map<String,Set<String>> PURPOSES=Map.of(
        "prediction",Set.of("FORECAST","STOCK_ANALYSIS","COMPARISON","PORTFOLIO_VALUATION","PREDICTION_VERIFICATION"),
        "notification",Set.of("ALERT_EVALUATION"),
        "market",Set.of("FORECAST_INPUT_PUBLISH","COMPARISON_EVIDENCE_PUBLISH","ALERT_EVIDENCE_PUBLISH","REALIZATION_EVIDENCE_PUBLISH"));

    public static void id(String value) {
        if(value==null||!value.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))throw invalid();
    }
    public static void instrument(String value) {if(value==null||!value.matches("(SH|SZ)[0-9]{6}"))throw invalid();}
    public static long revision(String value) {
        try {
            if(value==null||!value.matches("0|[1-9][0-9]{0,18}"))throw invalid();
            return Long.parseLong(value);
        } catch(NumberFormatException e){throw invalid();}
    }
    public static long generation(String value) {long n=revision(value);if(n<1)throw invalid();return n;}
    public static void identity(UseIdentity value) {
        if(value==null||value.owner()==null||value.purpose()==null||!PURPOSES.getOrDefault(value.owner(),Set.of()).contains(value.purpose()))throw invalid();
        id(value.ownerOperationId());generation(value.generation());
    }
    public static List<String> versionSet(List<String> versions) {
        bounded(versions);var set=new TreeSet<String>();
        for(var v:versions){id(v);if(!set.add(v))throw invalid();}
        return List.copyOf(set);
    }
    public static Selector selector(Selector value) {
        if(value==null||value.kind()==null)throw invalid();
        return switch(value.kind()) {
            case "RAW_VERSIONS" -> {
                if(value.evidence()!=null||value.instruments()!=null)throw invalid();
                yield new Selector(value.kind(),versionSet(value.versionIds()),null,null);
            }
            case "CURRENT_HEADS" -> {
                if(value.versionIds()!=null||value.evidence()!=null)throw invalid();
                bounded(value.instruments());var stocks=new TreeSet<String>();
                for(var stock:value.instruments()){instrument(stock);if(!stocks.add(stock))throw invalid();}
                yield new Selector(value.kind(),null,null,List.copyOf(stocks));
            }
            case "COMPARISON_EVIDENCE" -> {
                if(value.versionIds()!=null||value.instruments()!=null||value.evidence()==null||value.evidence().size()<2||value.evidence().size()>3)throw invalid();
                var pairs=new TreeMap<String,EvidenceBinding>();var evidence=new HashSet<String>();
                for(var pair:value.evidence()) {
                    if(pair==null)throw invalid();instrument(pair.instrumentId());id(pair.evidenceId());
                    if(pairs.putIfAbsent(pair.instrumentId(),pair)!=null||!evidence.add(pair.evidenceId()))throw invalid();
                }
                yield new Selector(value.kind(),null,List.copyOf(pairs.values()),null);
            }
            default -> throw invalid();
        };
    }
    public static Command command(Command value) {
        if(value==null)throw invalid();id(value.operationId());revision(value.expectedRevision());
        if(!"DISABLE".equals(value.action())&&!"RESTORE".equals(value.action()))throw invalid();
        if(value.reason()==null)throw invalid();
        // Reject controls before trimming so a tab/newline cannot be silently removed.
        if(value.reason().codePoints().anyMatch(Character::isISOControl))throw invalid();
        var reason=value.reason().strip();
        if(reason.isBlank()||reason.codePointCount(0,reason.length())>300)throw invalid();
        return new Command(value.operationId(),value.action(),value.expectedRevision(),reason);
    }
    public static Command parseCommand(byte[] bytes) {
        if(bytes==null||bytes.length>4096)throw invalid();
        try {
            var node=JSON.readTree(bytes);
            if(node==null||!node.isObject()||node.size()!=4)throw invalid();
            for(var field:List.of("operationId","action","expectedRevision","reason"))if(!node.path(field).isTextual())throw invalid();
            return command(JSON.treeToValue(node,Command.class));
        }catch(BusinessException e){throw e;}catch(Exception e){throw invalid();}
    }
    public static String commandHash(String stock,String version,String actor,Command command) {
        instrument(stock);id(version);id(actor);var c=command(command);
        return hash(List.of(PROTOCOL,stock,version,actor,c.operationId(),c.action(),c.expectedRevision(),c.reason()));
    }
    public static String selectorHash(Selector selector) {return hash(selector(selector));}
    public static String versionsHash(List<String> versions) {return hash(versionSet(versions));}
    public static String identityHash(UseIdentity value) {identity(value);return hash(List.of(PROTOCOL,value.owner(),value.purpose(),value.ownerOperationId(),value.generation()));}
    private static void bounded(List<?> values){if(values==null||values.isEmpty()||values.size()>MAX_VERSIONS)throw invalid();}
    private static String hash(Object value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new BusinessException("MARKET_VERSION_HASH_UNAVAILABLE","版本操作暂不可用",HttpStatus.SERVICE_UNAVAILABLE);}
    }
    public static BusinessException invalid(){return new BusinessException("MARKET_VERSION_REQUEST_INVALID","版本使用请求格式不正确",HttpStatus.BAD_REQUEST);}
}
