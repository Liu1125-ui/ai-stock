package com.aistock.scheduler.task;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
class TaskSecurityTest {
    @Test void recheckIdentityBeforeBody()throws Exception{var f=new TaskSecurityFilter("secret",64);var q=new MockHttpServletRequest("POST","/internal/v1/tasks/verification-rechecks");q.setContent("bad-json".getBytes());var r=new MockHttpServletResponse();f.doFilter(q,r,(a,b)->fail());assertEquals(401,r.getStatus());}
    @Test void identityCheckedBeforeMalformedBody()throws Exception {
        var f=new TaskSecurityFilter("secret",64);
        var q=new MockHttpServletRequest("POST","/internal/v1/tasks/verifications");q.setContent("bad-json".getBytes());var r=new MockHttpServletResponse();
        f.doFilter(q,r,(a,b)->fail());assertEquals(401,r.getStatus());assertFalse(r.getContentAsString().contains("secret"));
    }
    @Test void boundsAuthenticatedBodyAndProtectsReadRoutes()throws Exception {
        var f=new TaskSecurityFilter("secret",64);var q=new MockHttpServletRequest("POST","/internal/v1/tasks/verifications");
        q.addHeader("X-Service-Token","secret");q.setContent(new byte[65]);var r=new MockHttpServletResponse();f.doFilter(q,r,(a,b)->fail());assertEquals(413,r.getStatus());
        q=new MockHttpServletRequest("GET","/internal/v1/tasks/123");r=new MockHttpServletResponse();f.doFilter(q,r,(a,b)->fail());assertEquals(401,r.getStatus());
    }
}
