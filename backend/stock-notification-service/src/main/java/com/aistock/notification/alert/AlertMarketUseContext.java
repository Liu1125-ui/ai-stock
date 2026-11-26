package com.aistock.notification.alert;
import java.util.*;
/** Credentials scoped only to the fixed evidence body GET, never user/email calls. */
public final class AlertMarketUseContext {
 private AlertMarketUseContext(){}private record Context(Map<String,String> headers,String raw){}private static final ThreadLocal<Context> CURRENT=new ThreadLocal<>();
 public static Map<String,String> headers(){var c=CURRENT.get();return c==null?Map.of():c.headers();}public static String expectedRaw(){var c=CURRENT.get();return c==null?null:c.raw();}
 public static Scope empty(){return new Scope(null);}public static Scope open(AlertMarketUseClient.Request r,AlertMarketUseClient.Receipt b){if(!"ACTIVE".equals(b.state())||!r.selector().versionIds().equals(b.versionIds()))throw AlertMarketUseClient.invalid();return new Scope(new Context(Map.of("X-Market-Use-Bundle",b.bundleId(),"X-Market-Use-Owner","notification","X-Market-Use-Purpose","ALERT_EVALUATION","X-Market-Use-Operation",r.operationId(),"X-Market-Use-Generation",r.generation()),b.versionIds().get(0)));}
 public static final class Scope implements AutoCloseable {private final Context previous;private boolean closed;private Scope(Context next){previous=CURRENT.get();if(next==null)CURRENT.remove();else CURRENT.set(next);}public void close(){if(!closed){closed=true;if(previous==null)CURRENT.remove();else CURRENT.set(previous);}}}
}
