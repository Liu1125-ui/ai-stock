package com.aistock.user.position;
import com.aistock.common.*;
import com.aistock.user.session.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class PositionSnapshotContractTest {
 private final PositionService positions=mock(PositionService.class);
 private final SessionService sessions=mock(SessionService.class);
 private final String token="a".repeat(43);
 private MockMvc mvc(String secret){return MockMvcBuilders.standaloneSetup(new PositionSnapshotController(positions,sessions,new com.fasterxml.jackson.databind.ObjectMapper(),secret)).setControllerAdvice(new ApiExceptionHandler()).build();}
 @Test void dependencyFailureIsNeverMisreportedAsInvalidUserInput()throws Exception{
  when(sessions.resolve(token)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic database unavailable"));
  mvc("test").perform(post("/internal/v1/positions/snapshot").header("X-Service-Token","test").contentType("application/json").content("{\"token\":\""+token+"\"}")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("POSITION_SOURCE_UNAVAILABLE"));
  verifyNoInteractions(positions);
 }
 @Test void wrongServiceIdentityRejectsBeforeBodyAndSessionResolution()throws Exception{
  mvc("test").perform(post("/internal/v1/positions/snapshot").header("X-Service-Token","wrong").content("not-json")).andExpect(status().isUnauthorized());verifyNoInteractions(sessions,positions);
 }
 @Test void fixedOwnerAndEmptyRevisionAreReturnedWithoutCreatingRecords()throws Exception{
  var owner=new Identity(UUID.randomUUID(),"ADMIN",false,1);
  when(sessions.resolve(token)).thenReturn(owner);when(positions.list(owner)).thenReturn(new PositionRecords.PositionBook("0",List.of()));
  mvc("test").perform(post("/internal/v1/positions/revision").header("X-Service-Token","test").content("{\"token\":\""+token+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.ownerId").value(owner.userId().toString())).andExpect(jsonPath("$.data.bookRevision").value("0")).andExpect(jsonPath("$.data.items").doesNotExist());
  verify(positions).list(owner);verifyNoMoreInteractions(positions);
 }
 @Test void noConfigurationRejectsBeforeParsing()throws Exception{mvc("").perform(post("/internal/v1/positions/snapshot").content("not-json")).andExpect(status().isServiceUnavailable());verifyNoInteractions(sessions,positions);}
}
