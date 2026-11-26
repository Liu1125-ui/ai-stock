package com.aistock.prediction.comparison;
import java.time.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static com.aistock.prediction.comparison.ComparisonTestEvidence.*;
class ComparisonEvidenceVerifierTest {
 @Test void validatesMetadataBeyondHash(){var n=sample("SH600030",new String[]{"10","11","12"},new String[]{"1","2","3"});var verifier=new ComparisonEvidenceVerifier(Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"),ZoneOffset.UTC));assertDoesNotThrow(()->verifier.verify(n));n.put("basis","RAW");assertThrows(RuntimeException.class,()->verifier.verify(n));n.put("basis","BACKWARD").put("capturedAt","2030-01-01T00:00:00Z");assertThrows(RuntimeException.class,()->verifier.verify(n));}
 @Test void rejectsNonpositivePricesAndNumericCells(){var n=sample("SH600030",new String[]{"0","11","12"},new String[]{"1","2","3"});assertThrows(RuntimeException.class,()->new ComparisonEvidenceVerifier().verify(n));}
}
