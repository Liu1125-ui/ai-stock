package com.aistock.market.ingestion;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
class CandidateRequestFilterTest {
    @Test void deniesUnauthenticatedBodyBeforeDispatch() throws Exception {
        var filter=new CandidateRequestFilter("private-service",64);
        var request=new MockHttpServletRequest("POST","/internal/v1/market/versions");
        request.setContent(new byte[100]);var response=new MockHttpServletResponse();var called=new AtomicBoolean();
        filter.doFilter(request,response,(req,res)->called.set(true));
        assertEquals(401,response.getStatus());assertFalse(called.get());
        assertTrue(response.getContentAsString().contains("INTERNAL_AUTH_REQUIRED"));
    }
    @Test void boundsAuthenticatedPayloadAndPreservesValidBody() throws Exception {
        var filter=new CandidateRequestFilter("private-service",64);
        var request=new MockHttpServletRequest("POST","/internal/v1/market/versions");request.addHeader("X-Service-Token","private-service");
        request.setContent(new byte[65]);var response=new MockHttpServletResponse();var called=new AtomicBoolean();
        filter.doFilter(request,response,(req,res)->called.set(true));
        assertEquals(413,response.getStatus());assertFalse(called.get());
        var small=new MockHttpServletRequest("POST","/internal/v1/market/versions");small.addHeader("X-Service-Token","private-service");small.setContent("{}".getBytes());
        var success=new MockHttpServletResponse();
        filter.doFilter(small,success,(req,res)->assertEquals("{}",new String(req.getInputStream().readAllBytes())));
        assertEquals(200,success.getStatus());
    }
    @Test void receiptQueryRequiresServiceIdentity()throws Exception {
        var f=new CandidateRequestFilter("service-private",64);
        var q=new MockHttpServletRequest("GET","/internal/v1/market/refresh/op-1");var r=new MockHttpServletResponse();
        f.doFilter(q,r,(a,b)->fail("receipt endpoint must authenticate"));
        assertEquals(401,r.getStatus());assertFalse(r.getContentAsString().contains("service-private"));
    }
    @Test void forecastEndpointsAuthenticateBeforeReadingBody()throws Exception {
        for(var path:new String[]{"/internal/v1/market/forecast-inputs","/internal/v1/market/forecast-window","/internal/v1/market/forecast-raw-window"}) {
            var f=new CandidateRequestFilter("service-private",64);
            var q=new MockHttpServletRequest(path.endsWith("inputs")?"POST":"GET",path);
            q.setContent("{".getBytes());var r=new MockHttpServletResponse();
            f.doFilter(q,r,(a,b)->fail("forecast endpoints must authenticate before JSON"));
            assertEquals(401,r.getStatus());
        }
    }
    @Test void mappedPathVariantsCannotBypassAuthenticationOrBodyLimit()throws Exception {
        for(var path:new String[]{"/internal/v1/market/forecast-window;x","/internal/v1/market/forecast-inputs;x","/internal/v1/market/forecast-raw-window;x","/internal/v1/market;v/forecast-window","/internal/v1/market/%66orecast-window"}){
            var f=new CandidateRequestFilter("service-private",64);
            var q=new MockHttpServletRequest("POST",path);q.setContent(new byte[65]);var r=new MockHttpServletResponse();
            f.doFilter(q,r,(a,b)->fail("mapped internal routes must not bypass authentication"));
            assertEquals(401,r.getStatus(),path);
            q=new MockHttpServletRequest("POST",path);q.addHeader("X-Service-Token","service-private");q.setContent(new byte[65]);r=new MockHttpServletResponse();
            f.doFilter(q,r,(a,b)->fail("mapped internal routes must not bypass body limit"));assertEquals(413,r.getStatus(),path);
        }
    }
}
