package com.aistock.prediction.marketuse;
import org.springframework.scheduling.annotation.Scheduled;
/** Bounded recovery only; unknown registration never starts a new calculation or POST. */
public final class MarketUseRecovery {
 private final MarketUseOwnerService owner;private final PredictionMarketUseOwner predictions;private final ComparisonMarketUseOwner comparisons;private final PortfolioMarketUseOwner portfolios;private final boolean analysisEnabled,recoveryEnabled;
 public MarketUseRecovery(MarketUseOwnerService owner,boolean enabled){this(owner,null,null,enabled,enabled);}
 public MarketUseRecovery(MarketUseOwnerService owner,PredictionMarketUseOwner predictions,boolean analysisEnabled,boolean recoveryEnabled){this(owner,predictions,null,analysisEnabled,recoveryEnabled);}
 public MarketUseRecovery(MarketUseOwnerService owner,PredictionMarketUseOwner predictions,ComparisonMarketUseOwner comparisons,boolean analysisEnabled,boolean recoveryEnabled){this(owner,predictions,comparisons,null,analysisEnabled,recoveryEnabled);}
 public MarketUseRecovery(MarketUseOwnerService owner,PredictionMarketUseOwner predictions,ComparisonMarketUseOwner comparisons,PortfolioMarketUseOwner portfolios,boolean analysisEnabled,boolean recoveryEnabled){this.portfolios=portfolios;this.comparisons=comparisons;this.owner=owner;this.predictions=predictions;this.analysisEnabled=analysisEnabled;this.recoveryEnabled=recoveryEnabled;}
 @Scheduled(fixedDelayString="${aistock.market-use.recovery-delay-ms:60000}",initialDelayString="${aistock.market-use.recovery-delay-ms:60000}") public void recover(){if(!recoveryEnabled)return;if(analysisEnabled)owner.recoverBatch();if(predictions!=null)predictions.recoverBatch();if(comparisons!=null)comparisons.recoverBatch();if(portfolios!=null)portfolios.recoverBatch();}
}
