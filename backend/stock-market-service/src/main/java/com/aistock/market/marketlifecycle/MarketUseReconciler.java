package com.aistock.market.marketlifecycle;

import org.springframework.scheduling.annotation.Scheduled;

/** Owner IO occurs outside the storage transactions; each unknown item is retained and rotated. */
public final class MarketUseReconciler {
    public record Result(int checked,int released,int retained) {}
    private final MarketLifecycleService service;private final MarketUseProofClient proofs;private final boolean enabled;
    public MarketUseReconciler(MarketLifecycleService service,MarketUseProofClient proofs,boolean enabled){this.service=service;this.proofs=proofs;this.enabled=enabled;}
    @Scheduled(fixedDelayString="${aistock.market.lifecycle.reconcile-poll-ms:60000}") public void tick(){runOnce();}
    public synchronized Result runOnce() {
        if(!enabled)return new Result(0,0,0);int checked=0,released=0,retained=0;
        final java.util.List<String> pending;try{pending=service.pendingUseIds(50);}catch(RuntimeException e){return new Result(0,0,0);}
        for(var id:pending) {
            if(Thread.currentThread().isInterrupted())break;
            checked++;
            try {
                service.markUseChecked(id);var bundle=service.bundleForCheck(id);
                if(!"ACTIVE".equals(bundle.state()))continue;
                var proof=proofs.read(bundle);
                if(proof.isEmpty()){retained++;continue;}
                service.releaseVerified(id,proof.get());released++;
            }catch(RuntimeException unknown){retained++;}
        }
        return new Result(checked,released,retained);
    }
}
