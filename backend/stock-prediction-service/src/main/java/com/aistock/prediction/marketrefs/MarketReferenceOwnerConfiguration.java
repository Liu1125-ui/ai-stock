package com.aistock.prediction.marketrefs;
import com.aistock.common.marketrefs.*;import com.aistock.common.marketrefs.MarketReferenceRecords.Source;import com.fasterxml.jackson.databind.ObjectMapper;import javax.sql.DataSource;import java.time.Clock;import java.util.*;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.boot.web.servlet.FilterRegistrationBean;
@Configuration public class MarketReferenceOwnerConfiguration {
 @Bean public ReferenceScanner marketReferenceScanner(DataSource source){return new ReferenceScanner(source,"prediction",List.of(
   new Source("forecasts","预测输入快照","forecast_request",List.of(),List.of("snapshot_json")),
   new Source("verifications","预测验证证据","forecast_verification_version",List.of(),List.of("evidence_json")),
   new Source("explanations","预测解释","prediction_explanation",List.of(),List.of("context_json")),
   new Source("explanation_ops","预测解释操作","prediction_explanation_operation",List.of(),List.of("context_json")),
   new Source("analyses","综合分析","stock_analysis_result",List.of("market_version_id"),List.of("context_json")),
   new Source("portfolio_market_uses","持仓执行使用凭据","portfolio_market_use",List.of(),List.of("receipt_json")),
   new Source("comparison_market_uses","比较执行使用凭据","comparison_market_use",List.of(),List.of("receipt_json")),
   new Source("prediction_market_uses","预测验证使用凭据","prediction_market_use",List.of("raw_version_id"),List.of()),
   new Source("analysis_market_uses","分析使用凭据","stock_analysis_market_use",List.of("raw_version_id"),List.of()),
   new Source("analysis_ops","综合分析操作","stock_analysis_operation",List.of("market_version_id"),List.of("context_json")),
   new Source("questions","股票问答","stock_question_result",List.of(),List.of("context_json")),
   new Source("question_ops","股票问答操作","stock_question_operation",List.of(),List.of("context_json")),
   new Source("comparisons","多股对比快照","stock_comparison_result",List.of(),List.of("source_snapshots_json","result_json","summary_json")),
   new Source("comparison_ops","多股对比操作","stock_comparison_operation",List.of(),List.of("input_json")),
   new Source("comparison_ai","对比解释","comparison_ai_result",List.of(),List.of("context_json")),
   new Source("comparison_ai_ops","对比解释操作","comparison_ai_operation",List.of(),List.of("context_json")),
   new Source("portfolio_analyses","持仓估值快照","portfolio_analysis_result",List.of(),List.of("snapshot_json","quotes_json","result_json")),
   new Source("portfolio_questions","持仓问答","portfolio_question_result",List.of(),List.of("context_json")),
   new Source("portfolio_question_ops","持仓问答操作","portfolio_question_operation",List.of(),List.of("context_json"))),Clock.systemUTC());}
 @Bean public FilterRegistrationBean<ReferenceTokenFilter> marketReferenceToken(@Value("${aistock.internal.token:}")String token,ObjectMapper mapper){var bean=new FilterRegistrationBean<ReferenceTokenFilter>(new ReferenceTokenFilter(token,mapper));bean.setOrder(-120);bean.addUrlPatterns(MarketReferenceInput.INTERNAL_BASE,MarketReferenceInput.INTERNAL_BASE+"/*");return bean;}
}
