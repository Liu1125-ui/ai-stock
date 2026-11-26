package com.aistock.market.marketlifecycle;

import com.aistock.common.BusinessException;
import com.aistock.common.marketlifecycle.*;
import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.BooleanSupplier;
import static com.aistock.market.marketlifecycle.MarketLifecycleRecords.*;

final class MarketLifecycleRepository {
    private final MarketLifecycleStore db;
    private static final String RECEIPT="select a.*,v.instrument_id,e.lifecycle_revision result_revision from market_version_action a join market_data_version v on v.version_id=a.version_id join market_version_action_event e on e.event_id=(select max(e2.event_id) from market_version_action_event e2 where e2.operation_id=a.operation_id) ";
    MarketLifecycleRepository(MarketLifecycleStore db){this.db=db;}
    Receipt action(String stock,String version,String actor,Command input,BooleanSupplier ready) {
        MarketLifecycleContract.instrument(stock);MarketLifecycleContract.id(version);MarketLifecycleContract.id(actor);
        var command=MarketLifecycleContract.command(input);var hash=MarketLifecycleContract.commandHash(stock,version,actor,command);
        return db.write(()->{
            db.guard("ACTION",command.operationId());
            var previous=db.jdbc.queryForList("select actor_user_id,version_id,request_hash from market_version_action where operation_id=?",command.operationId());
            if(!previous.isEmpty()) {
                var p=previous.get(0);if(!actor.equals(p.get("actor_user_id"))||!version.equals(p.get("version_id"))||!hash.equals(p.get("request_hash")))throw MarketLifecycleStore.conflict("MARKET_VERSION_OPERATION_CONFLICT");
                return operation(stock,version,actor,command.operationId());
            }
            if(!ready.getAsBoolean())throw MarketLifecycleStore.notReady();
            var meta=db.meta(version,false);if(!stock.equals(meta.stock()))throw MarketLifecycleStore.notFound();
            var heads=db.heads(List.of(stock));db.lockVersions(List.of(version));var before=db.state(version);
            State after=before;String error=null;
            try {
                if(before.revision()!=MarketLifecycleContract.revision(command.expectedRevision()))throw MarketLifecycleStore.conflict("MARKET_VERSION_REVISION_CONFLICT");
                after="DISABLE".equals(command.action())?MarketLifecyclePolicy.disable(before,version.equals(heads.get(stock)),db.active(version),UUID.fromString(command.operationId())):MarketLifecyclePolicy.restore(before);
            } catch(BusinessException rejected) {error=rejected.code();}
            var state=error!=null?"REJECTED":after.useState()==UseState.DRAINING?"WAITING":"COMPLETED";
            var cycle=after.cycleId()==null?null:after.cycleId().toString();
            if(error==null) {
                db.save(version,after);
                if("RESTORE".equals(command.action())&&before.useState()==UseState.DRAINING)endDrain(version,before.cycleId(),"SUPERSEDED",after);
            }
            db.jdbc.update("insert into market_version_action(operation_id,actor_user_id,version_id,action,expected_revision,request_hash,reason,cycle_id,state,before_state,after_state,error_code,created_at,finished_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                command.operationId(),actor,version,command.action(),MarketLifecycleContract.revision(command.expectedRevision()),hash,command.reason(),cycle,state,before.useState().name(),after.useState().name(),error,db.now(),"WAITING".equals(state)?null:db.now());
            if(error==null)event(command.operationId(),"ACCEPTED",after);
            if(!"WAITING".equals(state))event(command.operationId(),state,after);
            return operation(stock,version,actor,command.operationId());
        });
    }
    void finishDrain(String version,State before,long active) {
        var next=MarketLifecyclePolicy.finish(before,before.cycleId(),active);
        if(next.isEmpty())return;db.save(version,next.get());endDrain(version,before.cycleId(),"COMPLETED",next.get());
    }
    private void endDrain(String version,UUID cycle,String outcome,State state) {
        var rows=db.jdbc.queryForList("select operation_id from market_version_action where version_id=? and cycle_id=? and state='WAITING' for update",String.class,version,cycle.toString());
        if(rows.size()!=1)throw MarketLifecycleStore.unavailable();var operation=rows.get(0);
        db.jdbc.update("update market_version_action set state=?,after_state=?,finished_at=? where operation_id=? and state='WAITING'",outcome,state.useState().name(),db.now(),operation);
        event(operation,outcome,state);
    }
    private void event(String operation,String type,State state) {
        db.jdbc.update("insert into market_version_action_event(operation_id,event_type,lifecycle_revision,use_state,cycle_id,created_at) values(?,?,?,?,?,?)",operation,type,state.revision(),state.useState().name(),state.cycleId()==null?null:state.cycleId().toString(),db.now());
    }
    Snapshot snapshot(String stock,String version) {
        MarketLifecycleContract.instrument(stock);MarketLifecycleContract.id(version);
        return db.write(()->{var meta=db.meta(version,false);if(!stock.equals(meta.stock()))throw MarketLifecycleStore.notFound();var heads=db.heads(List.of(stock));db.lockVersions(List.of(version));var s=db.state(version);return new Snapshot(stock,version,s.useState().name(),Long.toString(s.revision()),s.cycleId()==null?null:s.cycleId().toString(),db.active(version),version.equals(heads.get(stock)));});
    }
    Receipt operation(String stock,String version,String actor,String operation) {
        MarketLifecycleContract.instrument(stock);MarketLifecycleContract.id(version);MarketLifecycleContract.id(actor);MarketLifecycleContract.id(operation);
        return db.safe(()->{var rows=db.jdbc.query(RECEIPT+"where a.operation_id=? and a.version_id=? and a.actor_user_id=? and v.instrument_id=?",(r,n)->receipt(r),operation,version,actor,stock);if(rows.size()!=1)throw MarketLifecycleStore.notFound();return rows.get(0);});
    }
    History history(String stock,String version,String before,int size) {
        MarketLifecycleContract.instrument(stock);MarketLifecycleContract.id(version);if(size<1||size>20)throw MarketLifecycleContract.invalid();if(before!=null)MarketLifecycleContract.id(before);
        return db.safe(()->{
            if(!stock.equals(db.meta(version,false).stock()))throw MarketLifecycleStore.notFound();
            var args=new ArrayList<Object>();args.add(version);String filter="";
            if(before!=null){var cursor=db.jdbc.queryForList("select created_at from market_version_action where operation_id=? and version_id=?",before,version);if(cursor.size()!=1)throw MarketLifecycleStore.notFound();var time=cursor.get(0).get("created_at");filter=" and (a.created_at<? or (a.created_at=? and a.operation_id<?))";args.add(time);args.add(time);args.add(before);}
            args.add(size+1);var rows=db.jdbc.query(RECEIPT+"where a.version_id=?"+filter+" order by a.created_at desc,a.operation_id desc limit ?",(r,n)->receipt(r),args.toArray());
            var items=rows.stream().limit(size).toList();return new History(items,rows.size()>size?items.get(items.size()-1).operationId():null);
        });
    }
    private Receipt receipt(ResultSet r)throws SQLException {return new Receipt(r.getString("operation_id"),r.getString("instrument_id"),r.getString("version_id"),r.getString("action"),Long.toString(r.getLong("expected_revision")),r.getString("reason"),r.getString("state"),r.getString("error_code"),Long.toString(r.getLong("result_revision")),r.getString("before_state"),r.getString("after_state"),r.getString("cycle_id"),MarketLifecycleStore.time(r,"created_at"),MarketLifecycleStore.time(r,"finished_at"));}
}
