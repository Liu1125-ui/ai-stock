package com.aistock.market.comparison;
public interface ComparisonCollector {
 record Collected(String state,ComparisonEvidenceRecords.Candidate candidate,String errorCode){}
 Collected collect(ComparisonUpdateRecords.Request request);
}
