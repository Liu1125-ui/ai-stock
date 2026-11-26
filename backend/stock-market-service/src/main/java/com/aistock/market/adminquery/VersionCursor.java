package com.aistock.market.adminquery;

import com.aistock.common.adminmarket.AdminMarketQuery;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class VersionCursor {
    private final ObjectMapper mapper=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public record Cursor(String instrumentId,String provider,Instant lastPublishedAt,UUID lastVersionId) {}
    public String encode(Cursor c) {
        try {validate(c);var n=mapper.createObjectNode();n.put("v",1);n.put("instrumentId",c.instrumentId());if(c.provider()==null)n.putNull("provider");else n.put("provider",c.provider());n.put("lastPublishedAt",c.lastPublishedAt().toString());n.put("lastVersionId",c.lastVersionId().toString());return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(n));}
        catch(Exception e) {throw AdminMarketQuery.invalid();}
    }
    public Cursor decode(String token,String instrumentId,String provider) {
        try {
            if(token==null||token.length()>512||!token.matches("[A-Za-z0-9_-]+"))throw AdminMarketQuery.invalid();
            var bytes=Base64.getUrlDecoder().decode(token);if(!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(token))throw AdminMarketQuery.invalid();
            var n=mapper.readTree(bytes);var fields=new HashSet<String>();n.fieldNames().forEachRemaining(fields::add);
            if(!n.isObject()||!fields.equals(Set.of("v","instrumentId","provider","lastPublishedAt","lastVersionId"))||!n.get("v").isInt()||n.get("v").intValue()!=1||!n.get("instrumentId").isTextual()||!n.get("lastPublishedAt").isTextual()||!n.get("lastVersionId").isTextual()||!(n.get("provider").isTextual()||n.get("provider").isNull()))throw AdminMarketQuery.invalid();
            String id=n.get("lastVersionId").textValue();if(!id.matches(AdminMarketQuery.UUID_PATTERN))throw AdminMarketQuery.invalid();
            var c=new Cursor(n.get("instrumentId").textValue(),n.get("provider").isNull()?null:n.get("provider").textValue(),Instant.parse(n.get("lastPublishedAt").textValue()),UUID.fromString(id));validate(c);
            if(!c.instrumentId().equals(instrumentId)||!Objects.equals(c.provider(),provider))throw AdminMarketQuery.invalid();return c;
        } catch(Exception e) {throw AdminMarketQuery.invalid();}
    }
    private static void validate(Cursor c) {
        if(c==null||c.instrumentId()==null||!c.instrumentId().matches(AdminMarketQuery.INSTRUMENT)||c.provider()!=null&&!Set.of("baostock","tushare").contains(c.provider())||c.lastPublishedAt()==null||c.lastVersionId()==null||c.lastPublishedAt().getNano()%1000000!=0)throw AdminMarketQuery.invalid();
        int y=c.lastPublishedAt().atZone(ZoneOffset.UTC).getYear();if(y<1990||y>9999)throw AdminMarketQuery.invalid();
    }
}
