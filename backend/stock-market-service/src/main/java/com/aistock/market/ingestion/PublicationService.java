package com.aistock.market.ingestion;

import com.aistock.common.BusinessException;
import com.aistock.market.version.MarketVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PublicationService {
    private final MarketVersionRepository repository;private final CandidateValidator validator;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    public PublicationService(MarketVersionRepository repository,CandidateValidator validator){this.repository=repository;this.validator=validator;}
    public Publication publish(MarketCandidate c) {
        return publish(c,null,java.time.Clock.systemUTC());
    }
    public Publication publish(MarketCandidate c,java.time.Instant deadline,java.time.Clock clock) {
        var requestHash=hash(json(c));
        try {
            validator.validate(c);
            return repository.publish(c,requestHash,contentHash(c),deadline,clock);
        } catch(BusinessException error) {
            if(deadline==null && !error.code().equals("MARKET_OPERATION_CONFLICT") && !error.code().equals("MARKET_OPERATION_REJECTED")) repository.reject(c,requestHash,error.code());
            throw error;
        }
    }
    private String contentHash(MarketCandidate c) {
        var content=new LinkedHashMap<String,Object>();
        content.put("instrument",c.instrumentId());content.put("provider",c.provider());content.put("basis",c.priceBasis());
        content.put("bars",c.bars().stream().map(row->{
            var values=new java.util.ArrayList<String>();values.add(row.date().toString());
            for(var value:new BigDecimal[]{row.open(),row.high(),row.low(),row.close(),row.volumeShares(),row.amountYuan(),row.sourceAdjustmentRatio()}) values.add(value==null?null:value.stripTrailingZeros().toPlainString());
            values.add(Integer.toString(row.tradeStatus()));return values;
        }).toList());
        content.put("calendar",c.calendar());
        return hash(json(content));
    }
    private String json(Object value) {
        try{return mapper.writeValueAsString(value);}catch(java.io.IOException e){throw new IllegalArgumentException("Candidate cannot be encoded",e);}
    }
    private String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}
    }
}
