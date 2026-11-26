package com.aistock.prediction.comparison;
import com.aistock.common.BusinessException;import org.springframework.http.HttpStatus;
public final class ComparisonErrors {
 private ComparisonErrors(){}
 public static BusinessException invalid(){return new BusinessException("COMPARISON_REQUEST_INVALID","股票对比参数或依据不满足要求",HttpStatus.UNPROCESSABLE_ENTITY);}
 public static BusinessException unavailable(){return new BusinessException("COMPARISON_MARKET_UNAVAILABLE","股票对比数据暂不可用，已保存结果保留",HttpStatus.SERVICE_UNAVAILABLE);}
 public static BusinessException missing(){return new BusinessException("RESOURCE_NOT_FOUND","未找到对比记录",HttpStatus.NOT_FOUND);}
 public static BusinessException conflict(){return new BusinessException("COMPARISON_REQUEST_CONFLICT","请求编号已绑定其他条件",HttpStatus.CONFLICT);}
}
