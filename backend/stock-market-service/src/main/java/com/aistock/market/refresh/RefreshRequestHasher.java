package com.aistock.market.refresh;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;
import java.util.HexFormat;
import java.util.List;

/** Preserve the exact pre-constraint receipt hash for all legacy requests. */
public final class RefreshRequestHasher {
    private static final ObjectMapper MAPPER=new ObjectMapper();
    private RefreshRequestHasher() {}
    public static String hash(RefreshRequest request) {
        try {
            byte[] bytes;
            if(request.adminConstraint()==null) {
                String legacy="RefreshRequest[operationId="+request.operationId()+", instrumentId="+request.instrumentId()
                    +", targetDate="+request.targetDate()+", leaseUntil="+request.leaseUntil()+"]";
                bytes=legacy.getBytes(StandardCharsets.UTF_8);
            } else {
                var c=request.adminConstraint();
                bytes=MAPPER.writeValueAsBytes(List.of("admin-refresh-request-v2",request.operationId(),request.instrumentId(),
                    request.targetDate().toString(),request.leaseUntil().toString(),c.baseVersionId(),
                    Long.toString(c.headRevision()),c.coverageStart().toString(),c.policyVersion()));
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch(NoSuchAlgorithmException|IOException e) {throw new IllegalStateException("Cannot hash refresh request",e);}
    }
}