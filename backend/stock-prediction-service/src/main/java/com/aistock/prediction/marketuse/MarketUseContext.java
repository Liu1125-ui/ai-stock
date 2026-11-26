package com.aistock.prediction.marketuse;
import java.util.*;
/** Only market/algorithm dependency clients copy these headers; never attach to MaaS. */
public final class MarketUseContext {
 private MarketUseContext(){}private record Context(Map<String,String> headers,List<String> versions){}private static final ThreadLocal<Context> CURRENT=new ThreadLocal<>();
 public static Map<String,String> headers(){var value=CURRENT.get();return value==null?Map.of():value.headers();}
 public static String expectedRaw(){var value=CURRENT.get();return value==null||value.versions().size()!=1?null:value.versions().get(0);}
 public static List<String> expectedVersions(){var value=CURRENT.get();return value==null?List.of():value.versions();}
 public static Scope empty(){return new Scope(null);}
 public static Scope open(MarketUseClient.Request r,MarketUseClient.Receipt b){if(!"ACTIVE".equals(b.state())||b.versionIds().size()!=(switch(r.selector().kind()){case "COMPARISON_EVIDENCE"->r.selector().evidence().size();case "CURRENT_HEADS"->r.selector().instruments().size();default->r.selector().versionIds().size();})||r.selector().versionIds()!=null&&!r.selector().versionIds().equals(b.versionIds()))throw MarketUseClient.invalid();return new Scope(new Context(Map.of("X-Market-Use-Bundle",b.bundleId(),"X-Market-Use-Owner","prediction","X-Market-Use-Purpose",r.purpose(),"X-Market-Use-Operation",r.operationId(),"X-Market-Use-Generation",r.generation()),b.versionIds()));}
 public static final class Scope implements AutoCloseable {private final Context previous;private boolean closed;private Scope(Context next){previous=CURRENT.get();if(next==null)CURRENT.remove();else CURRENT.set(next);}public void close(){if(!closed){closed=true;if(previous==null)CURRENT.remove();else CURRENT.set(previous);}}}
}
