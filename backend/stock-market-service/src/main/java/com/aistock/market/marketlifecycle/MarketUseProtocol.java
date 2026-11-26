package com.aistock.market.marketlifecycle;

import com.aistock.common.BusinessException;
import com.aistock.common.marketlifecycle.*;
import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.*;

final class MarketUseProtocol {
    private MarketUseProtocol() {}
    record Registration(String requestId,UseIdentity identity,Selector selector) {}
    record Receipt(String requestId,String protocolVersion,String bundleId,String owner,String purpose,String ownerOperationId,String generation,String state,List<String> versionIds,List<MarketLifecycleRecords.Use> uses) {}
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static Registration register(byte[] bytes) {
        var n=parse(bytes);fields(n,Set.of("owner","purpose","ownerOperationId","generation","selector","requestId"));
        var identity=identity(n);var request=text(n,"requestId");MarketLifecycleContract.id(request);
        var selector=n.path("selector");if(!selector.isObject())throw invalid();
        var allowed=Set.of("kind","versionIds","evidence","instruments");selector.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw invalid();});
        var evidence=new ArrayList<EvidenceBinding>();
        if(selector.hasNonNull("evidence")) {if(!selector.get("evidence").isArray())throw invalid();for(var p:selector.get("evidence")){fields(p,Set.of("instrumentId","evidenceId"));evidence.add(new EvidenceBinding(text(p,"instrumentId"),text(p,"evidenceId")));}}
        var selected=MarketLifecycleContract.selector(new Selector(text(selector,"kind"),strings(selector.get("versionIds")),selector.hasNonNull("evidence")?evidence:null,strings(selector.get("instruments"))));
        MarketUseRepository.compatible(identity,selected);return new Registration(request,identity,selected);
    }
    static UseIdentity completion(byte[] bytes){var n=parse(bytes);fields(n,Set.of("owner","purpose","ownerOperationId","generation"));return identity(n);}
    static UseIdentity identity(JsonNode n){var id=new UseIdentity(text(n,"owner"),text(n,"purpose"),text(n,"ownerOperationId"),text(n,"generation"));MarketLifecycleContract.identity(id);return id;}
    static Receipt receipt(String requestId,MarketLifecycleRecords.Bundle b) {return new Receipt(requestId,MarketLifecycleContract.PROTOCOL,b.bundleId(),b.identity().owner(),b.identity().purpose(),b.identity().ownerOperationId(),b.identity().generation(),b.state(),b.versionIds(),b.uses());}
    static JsonNode parse(byte[] bytes){if(bytes==null||bytes.length>65536)throw invalid();try{var n=JSON.readTree(bytes);if(n==null||!n.isObject())throw invalid();return n;}catch(IOException e){throw invalid();}}
    static void fields(JsonNode n,Set<String> wanted){if(n==null||!n.isObject()||n.size()!=wanted.size())throw invalid();var keys=new HashSet<String>();n.fieldNames().forEachRemaining(keys::add);if(!keys.equals(wanted))throw invalid();}
    static String text(JsonNode n,String key){if(!n.path(key).isTextual())throw invalid();return n.get(key).asText();}
    private static List<String> strings(JsonNode n){if(n==null||n.isNull())return null;if(!n.isArray())throw invalid();var values=new ArrayList<String>();for(var item:n){if(!item.isTextual())throw invalid();values.add(item.asText());}return values;}
    static BusinessException invalid(){return MarketLifecycleContract.invalid();}
}
