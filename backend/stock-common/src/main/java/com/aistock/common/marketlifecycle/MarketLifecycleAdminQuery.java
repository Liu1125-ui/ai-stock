package com.aistock.common.marketlifecycle;

import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.util.MultiValueMap;

public final class MarketLifecycleAdminQuery {
    public static final String STATE="/api/v1/admin/market-data/{instrumentId}/versions/{versionId}/lifecycle";
    public static final String HISTORY=STATE+"/history";
    public static final String ACTIONS=STATE+"/actions";
    public static final String OPERATION=ACTIONS+"/{operationId}";
    private static final Pattern READ_PATH=Pattern.compile(
        "/api/v1/admin/market-data/(SH|SZ)[0-9]{6}/versions/[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}/lifecycle(?:/history)?");

    private MarketLifecycleAdminQuery() {}

    public static boolean readPath(String path) {
        return path!=null&&READ_PATH.matcher(path).matches();
    }

    public static boolean actionPath(String path,boolean write) {
        if(path==null)return false;
        int index=path.lastIndexOf("/actions");
        if(index<0)return false;
        String base=path.substring(0,index);
        if(!base.endsWith("/lifecycle")||!readPath(base))return false;
        String suffix=path.substring(index);
        return write?suffix.equals("/actions"):
            suffix.matches("/actions/[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}");
    }

    public record Page(String cursor,int limit) {}

    public static Page parse(MultiValueMap<String,String> query,boolean history) {
        Set<String> keys=history?Set.of("cursor","limit"):Set.of();
        if(!keys.containsAll(query.keySet())||query.values().stream().anyMatch(values->values.size()!=1))
            throw MarketLifecycleContract.invalid();
        String cursor=query.getFirst("cursor"),limit=query.getFirst("limit");
        if(cursor!=null)MarketLifecycleContract.id(cursor);
        if(limit!=null&&!limit.matches("[1-9]|1[0-9]|20"))throw MarketLifecycleContract.invalid();
        return new Page(cursor,limit==null?20:Integer.parseInt(limit));
    }
}
