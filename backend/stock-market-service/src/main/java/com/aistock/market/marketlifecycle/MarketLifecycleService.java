package com.aistock.market.marketlifecycle;

import com.aistock.common.marketlifecycle.*;
import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import java.time.Clock;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.aistock.market.marketlifecycle.MarketLifecycleRecords.*;

/** Internal application service; no controller/worker is registered in Task2. */
public final class MarketLifecycleService {
    private final MarketLifecycleStore db;
    private final MarketUseRepository uses;
    private final MarketLifecycleRepository lifecycle;
    private final BooleanSupplier commandsReady;
    public MarketLifecycleService(JdbcTemplate jdbc,Clock clock,BooleanSupplier commandsReady) {
        db=new MarketLifecycleStore(jdbc,clock);uses=new MarketUseRepository(db);lifecycle=new MarketLifecycleRepository(db);this.commandsReady=Objects.requireNonNull(commandsReady);
    }
    public Bundle register(String requestId,UseIdentity identity,Selector requested,boolean enabled) {
        MarketLifecycleContract.id(requestId);MarketLifecycleContract.identity(identity);var selector=MarketLifecycleContract.selector(requested);
        MarketUseRepository.compatible(identity,selector);
        var hash=MarketLifecycleStore.digest(MarketLifecycleStore.json(List.of(requestId,identity,selector)));
        return db.write(()->{
            db.guard("USE","REQUEST|"+requestId);
            var prior=db.jdbc.queryForList("select bundle_id,request_hash from market_version_use_request where request_id=?",requestId);
            if(!prior.isEmpty()) {
                if(!hash.equals(prior.get(0).get("request_hash")))throw MarketLifecycleStore.conflict("MARKET_USE_REQUEST_CONFLICT");
                return uses.binding((String)prior.get(0).get("bundle_id"),identity);
            }
            if(!enabled)throw MarketLifecycleStore.notReady();
            var bundle=uses.acquire(identity,selector);
            db.jdbc.update("insert into market_version_use_request(request_id,bundle_id,request_hash,created_at) values(?,?,?,?)",requestId,bundle.bundleId(),hash,db.now());
            return bundle;
        });
    }
    public Bundle registration(String requestId,UseIdentity identity) {
        MarketLifecycleContract.id(requestId);MarketLifecycleContract.identity(identity);
        return db.safe(()->{
            var rows=db.jdbc.queryForList("select bundle_id from market_version_use_request where request_id=?",String.class,requestId);
            if(rows.size()!=1)throw MarketLifecycleStore.notFound();return uses.binding(rows.get(0),identity);
        });
    }
    public Selector selector(String bundleId,UseIdentity identity) {
        uses.binding(bundleId,identity);
        return db.safe(()->{
            var rows=db.jdbc.queryForList("select selector_json,selector_hash from market_version_use_bundle where bundle_id=?",bundleId);
            if(rows.size()!=1||rows.get(0).get("selector_json")==null)throw MarketLifecycleStore.notReady();
            try {
                var selector=new com.fasterxml.jackson.databind.ObjectMapper().readValue((String)rows.get(0).get("selector_json"),Selector.class);
                selector=MarketLifecycleContract.selector(selector);
                if(!MarketLifecycleContract.selectorHash(selector).equals(rows.get(0).get("selector_hash")))throw MarketLifecycleStore.unavailable();
                return selector;
            }catch(java.io.IOException e){throw MarketLifecycleStore.unavailable();}
        });
    }
    public List<String> pendingUseIds(int limit) {
        if(limit<1||limit>50)throw MarketLifecycleContract.invalid();
        return db.safe(()->db.jdbc.queryForList("select bundle_id from market_version_use_bundle where state='ACTIVE' order by last_checked_at,bundle_id limit ?",String.class,limit));
    }
    public Bundle bundleForCheck(String id){MarketLifecycleContract.id(id);return db.safe(()->uses.bundle(id,false));}
    public void markUseChecked(String bundleId) {
        MarketLifecycleContract.id(bundleId);
        db.safe(()->db.jdbc.update("update market_version_use_bundle set last_checked_at=? where bundle_id=? and state='ACTIVE'",db.now(),bundleId));
    }
    public <T>T withActiveUse(String bundleId,UseIdentity identity,java.util.function.Function<Grant,T> work) {
        MarketLifecycleContract.id(bundleId);MarketLifecycleContract.identity(identity);
        return db.write(()->{
            db.guard("USE",MarketLifecycleStore.useGuardIdentity(identity));var bundle=uses.bundle(bundleId,true);
            if(!bundle.identity().equals(identity))throw MarketLifecycleStore.conflict("MARKET_USE_BINDING_MISMATCH");
            if(!"ACTIVE".equals(bundle.state()))throw MarketLifecycleStore.conflict("MARKET_USE_ALREADY_RELEASED");
            db.lockVersions(bundle.versionIds());uses.verifyUses(bundle);
            for(var version:bundle.versionIds())if(db.state(version).useState()==UseState.DISABLED)throw MarketLifecycleStore.conflict("MARKET_VERSION_USE_BLOCKED");
            var byStock=new TreeMap<String,List<String>>();for(var version:bundle.versionIds())byStock.computeIfAbsent(db.meta(version,false).stock(),k->new ArrayList<>()).add(version);
            return work.apply(new Grant(bundle,selector(bundleId,identity),byStock));
        });
    }
    public Bundle acquire(UseIdentity identity,Selector selector){return uses.acquire(identity,selector);}
    public Bundle binding(String bundleId,UseIdentity identity){return uses.binding(bundleId,identity);}
    public boolean actionsReady(){return commandsReady.getAsBoolean();}
    public Snapshot snapshot(String stock,String version){return lifecycle.snapshot(stock,version);}
    public Receipt action(String stock,String version,String actor,Command command){return lifecycle.action(stock,version,actor,command,commandsReady);}
    public Receipt operation(String stock,String version,String actor,String operation){return lifecycle.operation(stock,version,actor,operation);}
    public History history(String stock,String version,String cursor,int size){return lifecycle.history(stock,version,cursor,size);}

    /** Caller must be a trusted owner-terminal reader (fixed authenticated client or same-service repository), never a browser-supplied proof. */
    public void releaseVerified(String bundleId,TerminalProof proof) {
        MarketLifecycleContract.id(bundleId);
        if(proof==null)throw MarketLifecycleContract.invalid();MarketLifecycleContract.identity(proof.identity());
        if(!Set.of("SUCCEEDED","FAILED","CANCELLED").contains(proof.state()==null?"":proof.state())||proof.terminalAt()==null||proof.terminalAt().isAfter(db.clock.instant()))throw mismatch();
        MarketLifecycleContract.generation(proof.fencingRevision());MarketLifecycleContract.id(proof.resultIdentity());
        var versions=MarketLifecycleContract.versionSet(proof.versionIds());
        var hash=MarketLifecycleStore.digest(MarketLifecycleStore.json(List.of(proof.identity(),proof.state(),proof.terminalAt().toString(),proof.fencingRevision(),proof.resultIdentity(),versions)));
        var initial=uses.binding(bundleId,proof.identity());
        if(!initial.versionIds().equals(versions))throw mismatch();
        db.write(()->{
            db.guard("USE",MarketLifecycleStore.useGuardIdentity(proof.identity()));
            var bundle=uses.bundle(bundleId,true);
            if(!bundle.identity().equals(proof.identity())||!bundle.versionIds().equals(versions))throw mismatch();
            if("RELEASED".equals(bundle.state())) {
                var prior=db.jdbc.queryForObject("select terminal_proof_hash from market_version_use_bundle where bundle_id=?",String.class,bundleId);
                if(!hash.equals(prior))throw mismatch();return null;
            }
            if(!"ACTIVE".equals(bundle.state()))throw MarketLifecycleStore.unavailable();
            db.lockVersions(versions);var states=new TreeMap<String,State>();for(var version:versions)states.put(version,db.state(version));
            uses.verifyUses(bundle);
            db.jdbc.update("update market_version_use set state='RELEASED',released_at=?,terminal_proof_hash=? where bundle_id=? and state='ACTIVE'",db.now(),hash,bundleId);
            for(var version:versions) {
                var state=states.get(version);
                if(state.useState()==UseState.DRAINING)lifecycle.finishDrain(version,state,db.active(version));
            }
            db.jdbc.update("update market_version_use_bundle set state='RELEASED',released_at=?,terminal_proof_hash=? where bundle_id=? and state='ACTIVE'",db.now(),hash,bundleId);
            return null;
        });
    }
    private static com.aistock.common.BusinessException mismatch(){return MarketLifecycleStore.conflict("MARKET_USE_PROOF_MISMATCH");}
}
