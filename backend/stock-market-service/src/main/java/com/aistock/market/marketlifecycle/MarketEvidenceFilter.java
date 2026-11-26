package com.aistock.market.marketlifecycle;

import com.aistock.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/** Runs after CandidateRequestFilter, which authenticates and bounds the original payload. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE+11)
public final class MarketEvidenceFilter extends OncePerRequestFilter {
    private final boolean localComparison,localAlert;private final MarketEvidenceGate gate;private final ObjectMapper json;
    public MarketEvidenceFilter(MarketEvidenceGate gate,ObjectMapper json){this(gate,json,false);}
    public MarketEvidenceFilter(MarketEvidenceGate gate,ObjectMapper json,boolean localComparison){this(gate,json,localComparison,false);}
    @org.springframework.beans.factory.annotation.Autowired public MarketEvidenceFilter(MarketEvidenceGate gate,ObjectMapper json,@org.springframework.beans.factory.annotation.Value("${aistock.market.lifecycle.local-comparison-enabled:false}")boolean localComparison,@org.springframework.beans.factory.annotation.Value("${aistock.market.lifecycle.local-alert-enabled:false}")boolean localAlert){this.localAlert=localAlert;this.localComparison=localComparison;this.gate=gate;this.json=json;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){var path=new UrlPathHelper().getPathWithinApplication(request);boolean local="POST".equals(request.getMethod())&&(localComparison&&"/internal/v1/market/comparison-refresh".equals(path)||localAlert&&"/internal/v1/market/alert-evidence/prepare".equals(path));return local||!gate.enabled()||MarketEvidenceGate.purposes(path).isEmpty();}

    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws IOException,ServletException {
        s.setHeader("Cache-Control","no-store");
        try(var scope=gate.open(r,MarketEvidenceGate.purposes(new UrlPathHelper().getPathWithinApplication(r)))) {chain.doFilter(r,s);}
        catch(BusinessException e){if(s.isCommitted())throw e;s.setStatus(e.status().value());s.setContentType("application/json;charset=UTF-8");json.writeValue(s.getOutputStream(),ApiResponse.failure(e.code(),e.getMessage(),TraceIds.normalize(r.getHeader("X-Trace-Id"))));}
    }
}
