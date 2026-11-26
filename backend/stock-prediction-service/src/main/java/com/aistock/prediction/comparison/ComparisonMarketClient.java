package com.aistock.prediction.comparison;
import com.fasterxml.jackson.databind.JsonNode;import java.util.*;
public interface ComparisonMarketClient {JsonNode options(List<String> ids,String period);JsonNode evidence(UUID id);}
