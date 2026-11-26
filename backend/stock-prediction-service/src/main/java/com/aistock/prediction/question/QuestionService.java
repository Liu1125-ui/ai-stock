package com.aistock.prediction.question;
import com.aistock.common.BusinessException;import com.aistock.prediction.analysis.AnalysisRepository;import com.aistock.prediction.explanation.MaasClient;import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.node.ObjectNode;import java.time.*;import java.util.*;import org.springframework.http.HttpStatus;
public final class QuestionService {
 private final QuestionRepository r;private final AnalysisRepository source;private final MaasClient client;private final ObjectMapper m;private final Clock clock;
 public QuestionService(QuestionRepository r,AnalysisRepository source,MaasClient client,ObjectMapper m,Clock clock){this.r=r;this.source=source;this.client=client;this.m=m;this.clock=clock;}
 public JsonNode generate(UUID u,QuestionInput input){if(input==null)throw QuestionInput.invalid();var in=input.normalized();var prior=r.replay(u,in,clock.instant());if(prior!=null)return prior;var saved=source.get(u,in.analysisId());if(!client.enabled())throw new BusinessException("AI_NOT_CONFIGURED","问答尚未配置，原分析仍可查看",HttpStatus.SERVICE_UNAVAILABLE);var facts=QuestionFacts.build(saved,in.question(),client.model(),m);var claim=r.claim(u,in,facts,clock.instant());if(!claim.execute())return claim.receipt();QuestionAnswerTemplate.Answer answer=null;String failure=null;try{answer=QuestionAnswerTemplate.render(client.generateQuestion(in.question(),facts.values()),facts.values(),m);}catch(BusinessException e){failure=e.code();}catch(RuntimeException e){failure="AI_UNAVAILABLE";}return r.complete(in.operationId(),answer,failure,clock.instant());}
 public JsonNode operation(UUID u,UUID op){return r.operation(u,op,clock.instant());}
 public JsonNode get(UUID u,UUID id){return r.get(u,id);}
 public JsonNode list(UUID u,UUID analysis,int page){if(page<1||page>10000)throw QuestionInput.invalid();var n=(ObjectNode)r.list(u,analysis,page,clock.instant());return n.put("configured",client.enabled());}
}
