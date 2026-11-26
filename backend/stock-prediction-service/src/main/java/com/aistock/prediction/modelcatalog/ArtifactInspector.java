package com.aistock.prediction.modelcatalog;
import com.fasterxml.jackson.databind.JsonNode;
public interface ArtifactInspector {JsonNode inspect(String artifactId,String manifestHash);}
