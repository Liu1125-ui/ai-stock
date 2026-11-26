package com.aistock.market.marketlifecycle;

import com.aistock.common.marketlifecycle.*;
import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import java.util.*;
import static com.aistock.market.marketlifecycle.MarketLifecycleRecords.*;

final class MarketUseRepository {
    private final MarketLifecycleStore db;
    MarketUseRepository(MarketLifecycleStore db){this.db=db;}

    Bundle acquire(UseIdentity identity,Selector requested) {
        MarketLifecycleContract.identity(identity);var selector=MarketLifecycleContract.selector(requested);
        compatible(identity,selector);var selectorHash=MarketLifecycleContract.selectorHash(selector);
        return db.write(()->{
            db.guard("USE",MarketLifecycleStore.useGuardIdentity(identity));
            var existing=db.jdbc.queryForList("select bundle_id,selector_hash,purpose,selector_json from market_version_use_bundle where owner=? and owner_operation_id=? and generation=? for update",identity.owner(),identity.ownerOperationId(),MarketLifecycleContract.generation(identity.generation()));
            if(!existing.isEmpty()) {
                var row=existing.get(0);
                if(existing.size()!=1||!identity.purpose().equals(row.get("purpose"))||!selectorHash.equals(row.get("selector_hash")))throw conflict();
                if(row.get("selector_json")==null)db.jdbc.update("update market_version_use_bundle set selector_json=? where bundle_id=? and selector_json is null",MarketLifecycleStore.json(selector),row.get("bundle_id"));
                var bundle=bundle((String)row.get("bundle_id"),true);
                if(!"ACTIVE".equals(bundle.state()))throw MarketLifecycleStore.conflict("MARKET_USE_ALREADY_RELEASED");
                db.lockVersions(bundle.versionIds());verifyUses(bundle);
                for(var version:bundle.versionIds())if(db.state(version).useState()==UseState.DISABLED)throw MarketLifecycleStore.unavailable();
                return bundle;
            }
            var versions=resolve(selector);
            db.lockVersions(versions);
            var states=new TreeMap<String,State>();
            for(var version:versions){var state=db.state(version);MarketLifecyclePolicy.requireNewUse(state);states.put(version,state);}
            var bundleId=UUID.randomUUID().toString();
            db.jdbc.update("insert into market_version_use_bundle(bundle_id,owner,purpose,owner_operation_id,generation,selector_hash,versions_hash,version_ids_json,selector_json,state,created_at) values(?,?,?,?,?,?,?,?,?,'ACTIVE',?)",
                bundleId,identity.owner(),identity.purpose(),identity.ownerOperationId(),MarketLifecycleContract.generation(identity.generation()),selectorHash,MarketLifecycleContract.versionsHash(versions),MarketLifecycleStore.json(versions),MarketLifecycleStore.json(selector),db.now());
            for(var version:versions)db.jdbc.update("insert into market_version_use(use_id,bundle_id,version_id,state,created_at) values(?,?,?,'ACTIVE',?)",UUID.randomUUID().toString(),bundleId,version,db.now());
            return bundle(bundleId,false);
        });
    }
    private List<String> resolve(Selector selector) {
        if("CURRENT_HEADS".equals(selector.kind())) {
            var heads=db.heads(selector.instruments());var versions=MarketLifecycleContract.versionSet(new ArrayList<>(heads.values()));
            for(var pair:heads.entrySet())if(!pair.getKey().equals(db.meta(pair.getValue(),false).stock()))throw MarketLifecycleStore.unavailable();
            return versions;
        }
        var versions=new TreeSet<String>();var stocks=new TreeSet<String>();
        if("RAW_VERSIONS".equals(selector.kind()))versions.addAll(selector.versionIds());
        else for(var pair:selector.evidence()) {
            var rows=db.jdbc.queryForList("select raw_version_id,instrument_id from comparison_evidence_version where evidence_id=?",pair.evidenceId());
            if(rows.size()!=1||!pair.instrumentId().equals(rows.get(0).get("instrument_id")))throw MarketLifecycleStore.notFound();
            var version=(String)rows.get(0).get("raw_version_id");
            if(!pair.instrumentId().equals(db.meta(version,false).stock()))throw MarketLifecycleStore.unavailable();
            versions.add(version);
        }
        for(var version:versions)stocks.add(db.meta(version,false).stock());
        db.heads(stocks);return List.copyOf(versions);
    }
    Bundle bundle(String id,boolean lock) {
        var rows=db.jdbc.query("select * from market_version_use_bundle where bundle_id=?"+(lock?" for update":""),(r,n)->{
            var identity=new UseIdentity(r.getString("owner"),r.getString("purpose"),r.getString("owner_operation_id"),Long.toString(r.getLong("generation")));
            var versions=MarketLifecycleStore.versions(r.getString("version_ids_json"));
            if(!MarketLifecycleContract.versionsHash(versions).equals(r.getString("versions_hash")))throw MarketLifecycleStore.unavailable();
            return new Bundle(r.getString("bundle_id"),identity,r.getString("state"),versions,List.of());
        },id);
        if(rows.size()!=1)throw MarketLifecycleStore.notFound();var b=rows.get(0);
        var uses=db.jdbc.query("select u.use_id,u.version_id,l.revision from market_version_use u join market_version_lifecycle l on l.version_id=u.version_id where u.bundle_id=? order by u.version_id",(r,n)->new Use(r.getString(1),r.getString(2),Long.toString(r.getLong(3))),id);
        if(!b.versionIds().equals(uses.stream().map(Use::versionId).toList()))throw MarketLifecycleStore.unavailable();
        return new Bundle(b.bundleId(),b.identity(),b.state(),b.versionIds(),uses);
    }
    void verifyUses(Bundle bundle) {
        var states=db.jdbc.queryForList("select state from market_version_use where bundle_id=?",String.class,bundle.bundleId());
        if(states.size()!=bundle.versionIds().size()||states.stream().anyMatch(s->!bundle.state().equals(s)))throw MarketLifecycleStore.unavailable();
    }
    Bundle binding(String id,UseIdentity identity) {
        MarketLifecycleContract.id(id);MarketLifecycleContract.identity(identity);
        return db.safe(()->{var b=bundle(id,false);if(!b.identity().equals(identity))throw MarketLifecycleStore.notFound();return b;});
    }
    static void compatible(UseIdentity identity,Selector selector) {
        var kind=selector.kind();var count="RAW_VERSIONS".equals(kind)?selector.versionIds().size():"CURRENT_HEADS".equals(kind)?selector.instruments().size():selector.evidence().size();
        var valid=switch(identity.purpose()) {
            case "FORECAST","PREDICTION_VERIFICATION" -> count==1&&Set.of("RAW_VERSIONS","CURRENT_HEADS").contains(kind);
            case "STOCK_ANALYSIS","ALERT_EVALUATION" -> count==1&&"RAW_VERSIONS".equals(kind);
            case "COMPARISON" -> "COMPARISON_EVIDENCE".equals(kind);
            case "PORTFOLIO_VALUATION" -> "CURRENT_HEADS".equals(kind);
            default -> "market".equals(identity.owner())&&"RAW_VERSIONS".equals(kind);
        };
        if(!valid)throw MarketLifecycleContract.invalid();
    }
    private static com.aistock.common.BusinessException conflict(){return MarketLifecycleStore.conflict("MARKET_USE_OPERATION_CONFLICT");}
}
