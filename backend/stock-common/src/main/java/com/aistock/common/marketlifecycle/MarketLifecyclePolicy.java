package com.aistock.common.marketlifecycle;

import com.aistock.common.BusinessException;
import java.util.*;
import org.springframework.http.HttpStatus;
import static com.aistock.common.marketlifecycle.MarketUseRecords.*;

/** Pure decisions only. Call under the common version/lifecycle database lock. */
public final class MarketLifecyclePolicy {
    private MarketLifecyclePolicy() {}
    public static void requireNewUse(State state) {
        validate(state);
        if(state.useState()!=UseState.ENABLED)throw error("MARKET_VERSION_USE_BLOCKED","该行情版本暂不接受新使用");
    }
    public static State disable(State state, boolean currentHead, long activeUses, UUID cycle) {
        validate(state); count(activeUses);
        if(cycle==null)throw MarketLifecycleContract.invalid();
        if(currentHead)throw error("MARKET_VERSION_CURRENT_HEAD","当前行情头不能停用，请先发布新的可用行情");
        if(state.useState()!=UseState.ENABLED)throw conflict();
        return new State(activeUses==0?UseState.DISABLED:UseState.DRAINING,next(state),cycle);
    }
    public static State restore(State state) {
        validate(state);
        if(state.useState()==UseState.ENABLED)throw conflict();
        return new State(UseState.ENABLED,next(state),null);
    }
    public static Optional<State> finish(State state, UUID observedCycle, long activeUses) {
        validate(state); count(activeUses);
        if(observedCycle==null)throw MarketLifecycleContract.invalid();
        if(state.useState()!=UseState.DRAINING||!observedCycle.equals(state.cycleId())||activeUses!=0)return Optional.empty();
        return Optional.of(new State(UseState.DISABLED,next(state),state.cycleId()));
    }
    private static long next(State state) {
        if(state.revision()==Long.MAX_VALUE)throw error("MARKET_VERSION_REVISION_EXHAUSTED","版本操作修订号已达上限，请联系管理员核对");
        return state.revision()+1;
    }
    private static void validate(State state) {
        if(state==null||state.useState()==null||state.revision()<0||
            (state.useState()==UseState.ENABLED)!=(state.cycleId()==null))throw MarketLifecycleContract.invalid();
    }
    private static void count(long value) { if(value<0)throw MarketLifecycleContract.invalid(); }
    private static BusinessException conflict() {return error("MARKET_VERSION_STATE_CONFLICT","版本使用状态已改变，请刷新后核对");}
    private static BusinessException error(String code,String message){return new BusinessException(code,message,HttpStatus.CONFLICT);}
}
