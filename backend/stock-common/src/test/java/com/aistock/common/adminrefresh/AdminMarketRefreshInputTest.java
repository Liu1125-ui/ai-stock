package com.aistock.common.adminrefresh;

import com.aistock.common.BusinessException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.LinkedMultiValueMap;
import static org.junit.jupiter.api.Assertions.*;

class AdminMarketRefreshInputTest {
    private static final String OP="00000000-0000-0000-0000-000000000001";
    private static final String VERSION="12345678-abcd-1234-abcd-123456789abc";
    private static final String BODY="{\"operationId\":\""+OP+"\",\"baseVersionId\":\""+VERSION
        +"\",\"expectedHeadRevision\":\"0\",\"targetDate\":\"2026-10-24\",\"reasonCode\":\"RECHECK_DATA\"}";
    private byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private void rejected(String text){assertThrows(BusinessException.class,()->AdminMarketRefreshInput.parse(bytes(text)));}

    @Test void readsStrictCommandAndAcceptsZeroRevision() {
        var command=AdminMarketRefreshInput.parse(bytes(BODY));
        assertEquals(OP,command.operationId().toString());
        assertEquals(VERSION,command.baseVersionId().toString());
        assertEquals("0",command.expectedHeadRevision());
        assertEquals("2026-10-24",command.targetDate().toString());
        assertEquals("RECHECK_DATA",command.reasonCode());
    }
    @Test void enforcesBodyByteLimitWithoutRejectingLegalWhitespace() {
        assertNotNull(AdminMarketRefreshInput.parse(bytes(BODY+" ".repeat(4096-bytes(BODY).length))));
        rejected(BODY+" ".repeat(4097-bytes(BODY).length));
        assertThrows(BusinessException.class,()->AdminMarketRefreshInput.parse(null));
        rejected("");
    }
    @ParameterizedTest @ValueSource(strings={"+1","01","1e2","1.0","-1"," 1","9223372036854775808"})
    void rejectsNonCanonicalOrOverflowRevision(String revision){rejected(BODY.replace("\"expectedHeadRevision\":\"0\"","\"expectedHeadRevision\":\""+revision+"\""));}
    @Test void allowsLargestLongRevision(){assertEquals("9223372036854775807",AdminMarketRefreshInput.parse(bytes(BODY.replace("\"expectedHeadRevision\":\"0\"","\"expectedHeadRevision\":\"9223372036854775807\""))).expectedHeadRevision());}
    @Test void rejectsNonStringAndMissingRequiredFields(){
        for(String json:new String[]{BODY.replace("\"expectedHeadRevision\":\"0\"","\"expectedHeadRevision\":0"),
            BODY.replace("\"reasonCode\":\"RECHECK_DATA\"","\"reasonCode\":null"),
            BODY.replace("\"reasonCode\":\"RECHECK_DATA\"","\"reasonCode\":true"),
            BODY.replace(",\"reasonCode\":\"RECHECK_DATA\"",""),"[]","null"})rejected(json);
    }
    @Test void rejectsUnknownDuplicateAndTrailingJson(){
        rejected(BODY.substring(0,BODY.length()-1)+",\"actor\":\"ADMIN\"}");
        rejected(BODY.substring(0,BODY.length()-1)+",\"reasonCode\":\"RECOVER_UPDATE\"}");
        rejected(BODY+" {}");
    }
    @ParameterizedTest @ValueSource(strings={"2026-02-30","2026-2-01","2026-10-24T00:00:00"," 2026-10-24"})
    void rejectsNonDates(String day){rejected(BODY.replace("2026-10-24",day));}
    @Test void rejectsNonCanonicalIdsAndUnknownReason(){
        rejected(BODY.replace(VERSION,VERSION.toUpperCase()));
        rejected(BODY.replace(OP,"1-1-1-1-1"));
        rejected(BODY.replace("RECHECK_DATA","OVERRIDE"));
        assertEquals("RECOVER_UPDATE",AdminMarketRefreshInput.parse(bytes(BODY.replace("RECHECK_DATA","RECOVER_UPDATE"))).reasonCode());
    }
    @Test void validatesStockAndHistorySingletonParameters(){
        assertEquals("SH600030",AdminMarketRefreshInput.instrument("SH600030"));
        assertThrows(BusinessException.class,()->AdminMarketRefreshInput.instrument("SH600030/extra"));
        var q=new LinkedMultiValueMap<String,String>();
        assertEquals(20,AdminMarketRefreshInput.history(q).size());
        q.add("size","50");q.add("cursor","abc_123-XYZ");
        assertEquals(50,AdminMarketRefreshInput.history(q).size());
        q.add("size","50");assertThrows(BusinessException.class,()->AdminMarketRefreshInput.history(q));
        for(String[] item:new String[][]{{"size","51"},{"size","0"},{"size","01"},{"size","1.0"},{"limit","1"},{"cursor","x".repeat(769)},{"cursor","abc="}}){
            var bad=new LinkedMultiValueMap<String,String>();bad.add(item[0],item[1]);
            assertThrows(BusinessException.class,()->AdminMarketRefreshInput.history(bad));
        }
    }
    @Test void requiresCompleteInternalConstraintWithoutTreatingMissingRevisionAsZero() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        String valid="{\"baseVersionId\":\""+VERSION+"\",\"headRevision\":0,\"coverageStart\":\"2025-01-01\",\"policyVersion\":\"admin-market-refresh-v1\"}";
        assertEquals(0,mapper.readValue(valid,AdminMarketRefreshRecords.Constraint.class).headRevision());
        for(String bad:new String[]{valid.replace("\"headRevision\":0,",""),valid.replace("\"headRevision\":0","\"headRevision\":null"),
            valid.replace("\"coverageStart\":\"2025-01-01\"","\"coverageStart\":null"),valid.replace("admin-market-refresh-v1","unsupported"),
            valid.replace("\"headRevision\":0","\"headRevision\":-1")})
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->mapper.readValue(bad,AdminMarketRefreshRecords.Constraint.class));
    }
    @Test void matchesOnlyFourNewResourcesAndNeverOldQueryRoutes(){
        String root="/api/v1/admin/market-data";
        assertTrue(AdminMarketRefreshInput.writePath(root+"/SH600030/refresh-operations"));
        assertTrue(AdminMarketRefreshInput.readPath(root+"/SH600030/refresh-options"));
        assertTrue(AdminMarketRefreshInput.readPath(root+"/SH600030/refresh-operations"));
        assertTrue(AdminMarketRefreshInput.readPath(root+"/refresh-operations/"+OP));
        for(String path:new String[]{root,root+"/SH600030/versions",root+"/SH600030/refresh-operations/",root+"/SH600030/refresh-options/x",root+"/refresh-operations/"+OP+"/retry",root+"/SZ1234567/refresh-options"}){
            assertFalse(AdminMarketRefreshInput.readPath(path));assertFalse(AdminMarketRefreshInput.writePath(path));
        }
        assertFalse(AdminMarketRefreshInput.writePath(root+"/SH600030/refresh-options"));
        assertFalse(AdminMarketRefreshInput.writePath(null));
    }
}