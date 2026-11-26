package com.aistock.prediction.portfolioquestion;
import com.fasterxml.jackson.databind.JsonNode;import java.time.Instant;import java.util.*;
public final class PortfolioQuestionRecords {
 private PortfolioQuestionRecords(){}
 public record Request(UUID operationId,UUID analysisId,String question,boolean disclosureAccepted){@Override public String toString(){return "PortfolioQuestionRequest[redacted]";}}
 public record Facts(SortedMap<String,String> values,JsonNode localContext,String contextHash,String promptVersion,String providerModel){@Override public String toString(){return "PortfolioQuestionFacts[redacted]";}}
 public record Answer(String status,String topic,JsonNode paragraphs){}
 public record Receipt(UUID operationId,UUID analysisId,String question,String state,UUID answerId,String errorCode,Instant deadlineAt){@Override public String toString(){return "PortfolioQuestionReceipt[redacted]";}}
 public record Claim(boolean execute,Receipt receipt){}
 public record Page(List<JsonNode> items,String upper,String nextBefore,Receipt latestAttempt,boolean configured){}
}
