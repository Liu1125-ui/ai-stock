package com.aistock.prediction.modelcatalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
@EnabledIfEnvironmentVariable(named="TRAINING_TEST_ALGORITHM_URL",matches="http://127[.]0[.]0[.]1:[0-9]+")
class TrainedCandidateLiveMysqlTest {
    @Test void realTrainedArtifactRegistersIdempotentlyAsPending() throws Exception {
        var fixture=new ModelDatabaseFixture();
        var mapper=new ObjectMapper().findAndRegisterModules();
        var repo=new ModelRepository(fixture.jdbc,mapper);
        var inspector=new AlgorithmArtifactInspector(System.getenv("TRAINING_TEST_ALGORITHM_URL"),
            "training-integration-fixture",mapper);
        var catalog=new ModelCatalogService(repo,inspector,Clock.systemUTC(),mapper);
        var registration=mapper.readValue(Files.readAllBytes(Path.of(System.getenv("TRAINING_TEST_REGISTRATION"))),
            ModelRecords.Registration.class);
        var first=catalog.register(registration);
        var replay=catalog.register(registration);
        assertEquals("PENDING",first.state());
        assertEquals(first.modelId(),replay.modelId());
        assertEquals(first.registrationHash(),replay.registrationHash());
        assertTrue(catalog.published().isEmpty());
        assertNull(catalog.head().currentModelId());
    }
}
