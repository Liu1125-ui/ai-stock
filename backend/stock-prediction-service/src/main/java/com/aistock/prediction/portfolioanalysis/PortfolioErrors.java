package com.aistock.prediction.portfolioanalysis;
import com.aistock.common.BusinessException;import org.springframework.http.HttpStatus;
final class PortfolioErrors {
 private PortfolioErrors(){}
 static BusinessException invalid(){return new BusinessException("PORTFOLIO_SOURCE_INVALID","持仓或报价依据未通过核验，原结果保留",HttpStatus.UNPROCESSABLE_ENTITY);}
 static BusinessException missing(){return new BusinessException("RESOURCE_NOT_FOUND","未找到本人持仓分析记录",HttpStatus.NOT_FOUND);}
 static BusinessException unavailable(){return new BusinessException("PORTFOLIO_SOURCE_UNAVAILABLE","持仓分析依赖暂不可用，原结果保留",HttpStatus.SERVICE_UNAVAILABLE);}
 static BusinessException conflict(){return new BusinessException("PORTFOLIO_OPERATION_CONFLICT","持仓分析请求状态已变化，请查询原请求",HttpStatus.CONFLICT);}
}
