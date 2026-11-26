package com.aistock.common.marketlifecycle;

import com.aistock.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.common.marketlifecycle.MarketUseRecords.*;

class MarketLifecycleContractTest {
    private final String one="00000000-0000-0000-0000-000000000001", two="00000000-0000-0000-0000-000000000002";
    private UseIdentity identity(){return new UseIdentity("prediction","COMPARISON",one,"1");}
    @Test void decimalRevisionAndGenerationRemainExact() {
        assertEquals(9007199254740993L,MarketLifecycleContract.revision("9007199254740993"));
        assertEquals(Long.MAX_VALUE,MarketLifecycleContract.generation(Long.toString(Long.MAX_VALUE)));
        for(var bad:List.of("-1","01","1.0","1e2","9223372036854775808",""))
            assertThrows(BusinessException.class,()->MarketLifecycleContract.revision(bad));
        assertThrows(BusinessException.class,()->MarketLifecycleContract.generation("0"));
    }
    @Test void rejectsUnknownOwnerPurposeAndInvalidIdentity() {
        assertDoesNotThrow(()->MarketLifecycleContract.identity(identity()));
        for(var bad:List.of(new UseIdentity("scheduler","COMPARISON",one,"1"),new UseIdentity("prediction","NOT_A_PURPOSE",one,"1"),
                new UseIdentity("notification","FORECAST",one,"1"),new UseIdentity("prediction","COMPARISON","bad","1")))
            assertThrows(BusinessException.class,()->MarketLifecycleContract.identity(bad));
    }
    @Test void rawVersionSetsAreCanonicalBoundedAndNeverSilentlyDuplicated() {
        assertEquals(List.of(one,two),MarketLifecycleContract.versionSet(List.of(two,one)));
        for(var bad:List.of(List.<String>of(),List.of(one,one),List.of("bad")))
            assertThrows(BusinessException.class,()->MarketLifecycleContract.versionSet(bad));
        var oversized=new ArrayList<String>();for(int i=0;i<101;i++)oversized.add(UUID.randomUUID().toString());
        assertThrows(BusinessException.class,()->MarketLifecycleContract.versionSet(oversized));
    }
    @Test void actionParsingRejectsAmbiguousJsonAndNormalizesReason() {
        var json="{\"operationId\":\""+one+"\",\"action\":\"DISABLE\",\"expectedRevision\":\"0\",\"reason\":\"  核对异常数据  \"}";
        var c=MarketLifecycleContract.parseCommand(json.getBytes(StandardCharsets.UTF_8));
        assertEquals("核对异常数据",c.reason());
        for(var bad:List.of(json.replace("\"DISABLE\"","\"DELETE\""),json.replace("\"0\"","0"),json+"{}",
                json.replace("\"action\":","\"action\":\"RESTORE\",\"action\":"),json.replace("  核对异常数据  ","\\n"),json.replace("  核对异常数据  ","a\\tb")))
            assertThrows(BusinessException.class,()->MarketLifecycleContract.parseCommand(bad.getBytes(StandardCharsets.UTF_8)));
        assertThrows(BusinessException.class,()->MarketLifecycleContract.parseCommand(new byte[4097]));
    }
    @Test void fingerprintsAreStableAndBindActorVersionAndPayload() {
        var c=new Command(one,"DISABLE","0","核对异常数据");
        var hash=MarketLifecycleContract.commandHash("SH600030",one,two,c);
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals(hash,MarketLifecycleContract.commandHash("SH600030",one,two,c));
        assertNotEquals(hash,MarketLifecycleContract.commandHash("SH600030",one,one,c));
        assertNotEquals(hash,MarketLifecycleContract.commandHash("SH600030",two,two,c));
        assertNotEquals(hash,MarketLifecycleContract.commandHash("SH600030",one,two,new Command(one,"RESTORE","0","核对异常数据")));
        assertEquals(MarketLifecycleContract.versionsHash(List.of(one,two)),MarketLifecycleContract.versionsHash(List.of(two,one)));
    }
    @Test void comparisonBindingsKeepPairsWhenCanonicalizingAndDefensivelyCopy() {
        var original=new ArrayList<>(List.of(new EvidenceBinding("SH600030",one),new EvidenceBinding("SH600276",two)));
        var selector=new Selector("COMPARISON_EVIDENCE",null,original,null);
        var a=MarketLifecycleContract.selectorHash(selector);
        original.clear();assertEquals(2,selector.evidence().size());
        assertEquals(a,MarketLifecycleContract.selectorHash(new Selector("COMPARISON_EVIDENCE",null,List.of(new EvidenceBinding("SH600276",two),new EvidenceBinding("SH600030",one)),null)));
        assertNotEquals(a,MarketLifecycleContract.selectorHash(new Selector("COMPARISON_EVIDENCE",null,List.of(new EvidenceBinding("SH600030",two),new EvidenceBinding("SH600276",one)),null)));
        assertThrows(BusinessException.class,()->MarketLifecycleContract.selectorHash(new Selector("RAW_VERSIONS",List.of(one),null,List.of("SH600030"))));
        assertThrows(BusinessException.class,()->MarketLifecycleContract.selectorHash(new Selector("COMPARISON_EVIDENCE",null,List.of(new EvidenceBinding("SH600030",one),new EvidenceBinding("SH600030",two)),null)));
        assertThrows(BusinessException.class,()->MarketLifecycleContract.selectorHash(new Selector("CURRENT_HEADS",null,null,List.of("SH600030","SH600030"))));
    }
}
