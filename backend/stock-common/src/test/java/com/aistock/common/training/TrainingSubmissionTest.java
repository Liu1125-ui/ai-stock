package com.aistock.common.training;

import com.aistock.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrainingSubmissionTest {
    private static final String ID="00000000-0000-0000-0000-000000000001";
    private static byte[] input(String configs) {
        return ("{\"requestId\":\""+ID+"\",\"configurationIds\":"+configs+"}").getBytes(StandardCharsets.UTF_8);
    }

    @Test void acceptsOneToThreeDistinctConfigurationsWithStableOrder() {
        var first=TrainingSubmission.parse(input("[\"tf-seed-2\",\"tf-seed-1\",\"tf-seed-3\"]"));
        var replay=TrainingSubmission.parse(input("[\"tf-seed-3\",\"tf-seed-2\",\"tf-seed-1\"]"));
        assertEquals(List.of("tf-seed-1","tf-seed-2","tf-seed-3"),first.configurationIds());
        assertEquals(first,replay);
        assertEquals(List.of("tf-seed-1"),TrainingSubmission.parse(input("[\"tf-seed-1\"]")).configurationIds());
    }

    @Test void rejectsExpandedOverflowDuplicateAndUnregisteredInputShapes() {
        for(String configs:List.of("[]","[\"a\",\"a\"]","[\"a\",\"b\",\"c\",\"d\"]",
                "[\"../weights\"]","[1]","[null]","[{}]")) {
            assertEquals("TRAINING_REQUEST_INVALID",
                assertThrows(BusinessException.class,()->TrainingSubmission.parse(input(configs))).code());
        }
        for(String body:List.of(
            "{\"requestId\":\""+ID+"\",\"configurationIds\":[\"a\"],\"ownerId\":\"spoof\"}",
            "{\"requestId\":\""+ID+"\",\"configurationIds\":[\"a\"],\"configurationIds\":[\"b\"]}",
            new String(input("[\"a\"]"),StandardCharsets.UTF_8)+" {}")) {
            assertThrows(BusinessException.class,()->TrainingSubmission.parse(body.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
