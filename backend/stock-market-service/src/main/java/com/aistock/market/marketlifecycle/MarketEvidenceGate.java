package com.aistock.market.marketlifecycle;

import com.aistock.common.BusinessException;
import com.aistock.common.marketlifecycle.*;
import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.aistock.market.marketlifecycle.MarketLifecycleRecords.*;

/** Request scope pins immutable inputs; only local SQL work runs under the use lock. */
public final class MarketEvidenceGate {
    private record Context(MarketLifecycleService service,Grant grant) {}
    private static final ThreadLocal<Context> CURRENT=new ThreadLocal<>();
    private final MarketLifecycleService service;private final boolean enabled;
    public MarketEvidenceGate(MarketLifecycleService service,boolean enabled){this.service=service;this.enabled=enabled;}
    public boolean enabled(){return enabled;}
    public Scope open(HttpServletRequest request,Set<String> allowed){
        return open(header(request,"Bundle"),new UseIdentity(header(request,"Owner"),header(request,"Purpose"),header(request,"Operation"),header(request,"Generation")),allowed);
    }
    public Scope open(String bundleId,UseIdentity identity,Set<String> allowed) {
        MarketLifecycleContract.identity(identity);if(!allowed.contains(identity.purpose()))throw mismatch();
        var grant=service.withActiveUse(bundleId,identity,g->g);return new Scope(new Context(service,grant));
    }
    public static Scope passthrough(){return new Scope(CURRENT.get());}
    public static final class Scope implements AutoCloseable {
        private final Context previous;private boolean closed;
        private Scope(Context context){previous=CURRENT.get();CURRENT.set(context);}
        public void close(){if(!closed){closed=true;if(previous==null)CURRENT.remove();else CURRENT.set(previous);}}
    }
    public static <T>T local(Supplier<T> work) {
        var context=CURRENT.get();if(context==null)return work.get();
        var old=context.grant();return context.service().withActiveUse(old.bundle().bundleId(),old.bundle().identity(),fresh->{
            if(!fresh.bundle().versionIds().equals(old.bundle().versionIds())||!fresh.selector().equals(old.selector()))throw mismatch();return work.get();
        });
    }
    public static void checkpoint(){local(()->null);}
    public static String pinned(String stock) {
        var context=CURRENT.get();if(context==null)return null;MarketLifecycleContract.instrument(stock);
        var versions=context.grant().rawByStock().get(stock);if(versions==null||versions.size()!=1)throw mismatch();return versions.get(0);
    }
    public static void raw(String version) {var c=CURRENT.get();if(c!=null&&(version==null||!c.grant().bundle().versionIds().contains(version)))throw mismatch();}
    public static void stocks(Collection<String> stocks) {var c=CURRENT.get();if(c!=null&&!new TreeSet<>(stocks).equals(c.grant().rawByStock().keySet()))throw mismatch();}
    public static void evidence(String evidence,String stock,String raw) {
        raw(raw);var c=CURRENT.get();if(c!=null&&"COMPARISON".equals(c.grant().bundle().identity().purpose())) {
            var selected=c.grant().selector();if(!"COMPARISON_EVIDENCE".equals(selected.kind())||selected.evidence().stream().noneMatch(e->e.instrumentId().equals(stock)&&e.evidenceId().equals(evidence)))throw mismatch();
        }
    }
    /** A direct local producer without a registered context cannot add references to a blocked version. */
    public static void publishable(JdbcTemplate jdbc,String version) {
        raw(version);if(CURRENT.get()!=null)return;
        var states=jdbc.queryForList("select use_state from market_version_lifecycle where version_id=? for share",String.class,version);
        if(states.size()!=1||!"ENABLED".equals(states.get(0)))throw new BusinessException("MARKET_VERSION_USE_BLOCKED","该行情版本暂不接受新使用",HttpStatus.CONFLICT);
    }
    public static Set<String> purposes(String path) {
        String base="/internal/v1/market/";if(!path.startsWith(base))return Set.of();var p=path.substring(base.length());
        if(Set.of("forecast-raw-window","forecast-window","forecast-inputs").contains(p))return Set.of("FORECAST","FORECAST_INPUT_PUBLISH");
        if(p.startsWith("analysis-evidence/"))return Set.of("STOCK_ANALYSIS");
        if(Set.of("comparison-raw-window","comparison-evidence","comparison-refresh").contains(p))return Set.of("COMPARISON_EVIDENCE_PUBLISH");
        if(p.startsWith("comparison-evidence/"))return Set.of("COMPARISON");
        if(p.equals("valuation-quotes"))return Set.of("PORTFOLIO_VALUATION");
        if(p.equals("alert-evidence/prepare"))return Set.of("ALERT_EVALUATION","ALERT_EVIDENCE_PUBLISH");
        if(p.startsWith("alert-evidence/versions/"))return Set.of("ALERT_EVALUATION");
        if(p.startsWith("realizations/")&&!p.startsWith("realizations/operations/"))return Set.of("PREDICTION_VERIFICATION","REALIZATION_EVIDENCE_PUBLISH");
        return Set.of();
    }
    private static String header(HttpServletRequest r,String suffix){var values=Collections.list(r.getHeaders("X-Market-Use-"+suffix));if(values.size()!=1)throw MarketLifecycleContract.invalid();return values.get(0);}
    private static BusinessException mismatch(){return new BusinessException("MARKET_USE_BINDING_MISMATCH","使用凭据与本次证据不一致",HttpStatus.CONFLICT);}
}
