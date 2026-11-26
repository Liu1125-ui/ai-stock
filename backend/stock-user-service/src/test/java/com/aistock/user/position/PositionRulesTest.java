package com.aistock.user.position;
import java.math.BigDecimal;import java.util.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
class PositionRulesTest {
 @Test void acceptsExactCostsAndOddLots(){assertEquals("123.456789",PositionRules.cost("123.456789",new BigDecimal("1000000")).toPlainString());assertEquals(1,PositionRules.quantity("1",1000000000));assertEquals("0.000000",PositionRules.cost("0",new BigDecimal("1000000")).toPlainString());}
 @Test void rejectsInvalidQuantity(){for(var v:List.of("0","-1","1.1","1e3","01","1000000001"))assertThrows(com.aistock.common.BusinessException.class,()->PositionRules.quantity(v,1000000000),v);}
 @Test void rejectsInvalidCost(){for(var v:List.of("-1","NaN","1e3","1.1234567","1000000.000001"))assertThrows(com.aistock.common.BusinessException.class,()->PositionRules.cost(v,new BigDecimal("1000000")),v);}
 @Test void hashIgnoresEquivalentScaleAndRequestId(){var a=new PositionRecords.PositionMutation(UUID.randomUUID(),"CREATE",null,"SH600030","1","20.0",null);var b=new PositionRecords.PositionMutation(UUID.randomUUID(),"CREATE",null,"SH600030","1","20.000000",null);assertEquals(PositionRules.hash(a),PositionRules.hash(b));}
}
