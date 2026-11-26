package com.aistock.common.adminmarket;

import com.aistock.common.BusinessException;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

public final class AdminMarketQuery {
    public static final String BASE="/api/v1/admin/market-data";
    public static final String INSTRUMENT="(SH|SZ)[0-9]{6}";
    public static final String UUID_PATTERN="[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}";
    public static final int DEFAULT_SIZE=20, MAX_SIZE=50, MAX_PAGE=10000, DEFAULT_DAYS=90, MAX_DAYS=366;
    public static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private AdminMarketQuery() {}
    public record ListFilter(String q,String provider,String headState,int page,int size) {}
    public record HistoryFilter(String provider,String cursor,int size) {}
    public record DetailFilter(LocalDate from,LocalDate to) {}
    public static boolean readPath(String path) {
        return path!=null && (path.equals(BASE)||path.matches(BASE+"/"+INSTRUMENT+"/versions")||path.matches(BASE+"/"+INSTRUMENT+"/versions/"+UUID_PATTERN));
    }
    public static ListFilter listing(MultiValueMap<String,String> q) {
        validate(q,Set.of("q","provider","headState","page","size"));
        String search=q.getFirst("q");
        if(search!=null) { search=search.strip(); if(search.codePointCount(0,search.length())>64||search.codePoints().anyMatch(Character::isISOControl))throw invalid(); if(search.isEmpty())search=null; }
        String state=q.getFirst("headState");
        if(state!=null&&!Set.of("AVAILABLE","STAGING","REJECTED","UNAVAILABLE","NO_VERSION","REFERENCE_ERROR").contains(state))throw invalid();
        return new ListFilter(search,provider(q),state,number(q,"page",1,MAX_PAGE),number(q,"size",DEFAULT_SIZE,MAX_SIZE));
    }
    public static HistoryFilter history(MultiValueMap<String,String> q) {
        validate(q,Set.of("provider","cursor","size")); String cursor=q.getFirst("cursor");
        if(cursor!=null&&(cursor.length()>512||!cursor.matches("[A-Za-z0-9_-]+")))throw invalid();
        return new HistoryFilter(provider(q),cursor,number(q,"size",DEFAULT_SIZE,MAX_SIZE));
    }
    public static DetailFilter detail(MultiValueMap<String,String> q,Clock clock) {
        validate(q,Set.of("from","to"));LocalDate today=LocalDate.now(clock.withZone(ZONE));
        if(!q.containsKey("from")&&!q.containsKey("to"))return new DetailFilter(today.minusDays(DEFAULT_DAYS-1),today);
        if(!q.containsKey("from")||!q.containsKey("to"))throw invalid();
        try {var from=date(q.getFirst("from"));var to=date(q.getFirst("to"));
            if(from.isBefore(LocalDate.of(1990,1,1))||to.isAfter(today)||to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>=MAX_DAYS)throw invalid();
            return new DetailFilter(from,to);
        } catch(DateTimeParseException e) {throw invalid();}
    }
    private static LocalDate date(String value) {if(value==null||!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid();return LocalDate.parse(value);}
    private static String provider(MultiValueMap<String,String> q) {var p=q.getFirst("provider");if(p!=null&&!Set.of("baostock","tushare").contains(p))throw invalid();return p;}
    private static int number(MultiValueMap<String,String> q,String name,int fallback,int max) {var v=q.getFirst(name);if(v==null)return fallback;if(!v.matches("[1-9][0-9]{0,4}"))throw invalid();int n=Integer.parseInt(v);if(n>max)throw invalid();return n;}
    private static void validate(MultiValueMap<String,String> q,Set<String> allowed) {
        if(q==null||q.size()>allowed.size())throw invalid();q.forEach((k,v)->{if(!allowed.contains(k)||v==null||v.size()!=1||v.get(0)==null)throw invalid();});
    }
    public static BusinessException invalid() {return new BusinessException("MARKET_ADMIN_QUERY_INVALID","查询条件不正确，请核对后重试",HttpStatus.BAD_REQUEST);}
}
