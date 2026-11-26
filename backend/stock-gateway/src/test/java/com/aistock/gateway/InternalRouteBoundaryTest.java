package com.aistock.gateway;
import com.aistock.common.ApiExceptionHandler;import org.junit.jupiter.api.Test;import org.springframework.http.HttpMethod;import org.springframework.web.servlet.resource.NoResourceFoundException;import org.springframework.mock.web.MockHttpServletRequest;import static org.junit.jupiter.api.Assertions.*;
class InternalRouteBoundaryTest {
 @Test void adminTasksNeverExposeInternalExecutionMethods(){assertFalse(ProxyHeaderPolicy.allowed("GET","/internal/v1/tasks"));assertFalse(ProxyHeaderPolicy.allowed("POST","/api/v1/admin/tasks"));assertFalse(ProxyHeaderPolicy.allowed("GET","/api/v1/admin/tasks/00000000-0000-0000-0000-000000000001/retry"));}
 @Test void unexposedInternalRouteRemainsNotFound(){var handler=new ApiExceptionHandler();var request=new MockHttpServletRequest("GET","/internal/v1/instruments/DEMO-001");var response=handler.handleNoResource(new NoResourceFoundException(HttpMethod.GET,"internal/v1/instruments/DEMO-001"),request);assertEquals(404,response.getStatusCode().value());assertEquals("RESOURCE_NOT_FOUND",response.getBody().code());}
}
