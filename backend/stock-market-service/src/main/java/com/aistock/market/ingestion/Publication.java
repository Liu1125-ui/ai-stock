package com.aistock.market.ingestion;
public record Publication(String operationId,String versionId,String state,boolean replayed,long headRevision) {}
