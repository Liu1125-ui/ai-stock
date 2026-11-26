package com.aistock.prediction.modelcatalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static com.aistock.prediction.modelcatalog.ModelRecords.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelExpansionTest {
    @Test void expansionEvidenceBindsRegistrationAndManifest() throws Exception {
        var id = "transformer-s3-expansion-sh600519-0123456789abcdef";
        var evidence = new ExpansionEvaluation("SH600519", "2025-01-02", "2026-11-18", 78,
                "0.016158194956", "0.016350436449", "0.556410256410");
        var evaluation = new Evaluation("b".repeat(64), "STOCK_EXPANSION", 43, null,
                null, null, null, null, null, null, evidence);
        var registration = new Registration(id, "a".repeat(64), evaluation);
        var mapper = new ObjectMapper();
        assertEquals(registration, mapper.readValue(mapper.writeValueAsBytes(registration), Registration.class));
        var fixture = ModelCatalogMysqlTest.manifest(ModelCatalogMysqlTest.candidate(43).artifactId()).deepCopy();
        var manifest = (com.fasterxml.jackson.databind.node.ObjectNode) fixture;
        manifest.put("artifactId", id);
        manifest.put("schemaVersion", 3);
        manifest.putArray("instruments").add("SH600519");
        var binding = mapper.<com.fasterxml.jackson.databind.node.ObjectNode>valueToTree(evidence);
        binding.put("parentArtifactId", "transformer-s3-2023-seed43");
        binding.put("parentManifestHash", "a".repeat(64));
        binding.put("evidenceHash", "b".repeat(64));
        manifest.set("expansion", binding);
        assertDoesNotThrow(() -> {
            validate(registration);
            checkManifest(id, manifest);
            checkEvaluationBinding(registration, manifest);
        });
        binding.put("windows", 77);
        assertThrows(com.aistock.common.BusinessException.class, () -> checkEvaluationBinding(registration, manifest));
    }
}
