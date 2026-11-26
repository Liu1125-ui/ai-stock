package com.aistock.gateway.adminmarket;
import com.aistock.common.*;
import com.aistock.gateway.*;
import com.aistock.gateway.security.AdminReadAccess;
import com.aistock.gateway.statistics.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AdminMarketDataProxyTest {
 static final String BASE="/api/v1/admin/market-data", HISTORY=BASE+"/SH600030/versions", DETAIL=HISTORY+"/00000000-0000-0000-0000-000000000001";
 StatisticsIdentityClient identity;MarketPublicClient market;UserPublicClient user;MockMvc mvc;final Cookie cookie=new Cookie("AISTOCK_SESSION","admin");
 @BeforeEach void init(){
  identity=mock(StatisticsIdentityClient.class);market=mock(MarketPublicClient.class);user=mock(UserPublicClient.class);
  when(identity.resolve(any(),anyString())).thenReturn(ApiResponse.success(new StatisticsIdentityClient.Identity(UUID.randomUUID(),"ADMIN",0),"test"));
  when(market.get(anyString(),anyMap(),anyMap())).thenReturn(reply(200,"{\"code\":\"OK\",\"data\":{}}"));
  var config=new AdminMarketDataCorsConfiguration();
  mvc=MockMvcBuilders.standaloneSetup(new AdminMarketDataProxyController(market,new AdminReadAccess(identity,"server-test"),new ObjectMapper(),Clock.fixed(Instant.parse("2026-10-22T04:00:00Z"),ZoneOffset.UTC)),new UserApiProxyController(user,new ObjectMapper())).setControllerAdvice(new ApiExceptionHandler()).addFilters(config.adminMarketDataCors("http://127.0.0.1:15173").getFilter()).build();
 }
 static ResponseEntity<byte[]> reply(int status,String text){return ResponseEntity.status(status).body(text.getBytes(StandardCharsets.UTF_8));}
 @Test void routesOnlyThreeReadsAndPreservesParametersWithoutIdentityHeaders() throws Exception {
  mvc.perform(get(BASE).cookie(cookie).param("q","_%").param("provider","tushare").param("page","2").header("X-Role","ADMIN").header("X-User","forged").header("X-Service-Token","external")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(get(HISTORY).cookie(cookie).param("size","10")).andExpect(status().isOk());
  mvc.perform(get(DETAIL).cookie(cookie).param("from","2026-10-01").param("to","2026-10-20")).andExpect(status().isOk());
  verify(market).get(eq("admin/market-data"),eq(Map.of("q","_%","provider","tushare","page","2")),argThat(h->!h.containsKey("X-Role")&&!h.containsKey("X-User")&&!h.containsKey("X-Service-Token")));
  verifyNoInteractions(user);
 }
 @Test void rejectsRepeatedUnknownBodyAndWriteRequestsBeforeBusinessCall()throws Exception {
  mvc.perform(get(BASE).cookie(cookie).param("page","1","2")).andExpect(status().isBadRequest());
  mvc.perform(get(BASE).cookie(cookie).param("token","bad")).andExpect(status().isBadRequest());
  mvc.perform(get(HISTORY).cookie(cookie).param("page","2")).andExpect(status().isBadRequest());
  mvc.perform(get(DETAIL).cookie(cookie).param("from","2026-10-01")).andExpect(status().isBadRequest());
  mvc.perform(get(BASE).cookie(cookie).content("{}")).andExpect(status().isBadRequest());
  mvc.perform(post(BASE).cookie(cookie)).andExpect(status().isNotFound());
  mvc.perform(get(BASE+"/internal/v1").cookie(cookie)).andExpect(status().isNotFound());
  mvc.perform(get(BASE+"/SH600030/versions/not-a-version").cookie(cookie)).andExpect(status().isNotFound());
  verifyNoInteractions(market,user);
 }
 @Test void guardsRolesAndSeparatesDependencyFailures()throws Exception {
  mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
  mvc.perform(get(BASE).cookie(cookie,cookie)).andExpect(status().isUnauthorized());
  when(identity.resolve(any(),anyString())).thenReturn(ApiResponse.success(new StatisticsIdentityClient.Identity(UUID.randomUUID(),"USER",0),"test"));
  mvc.perform(get(BASE).cookie(cookie).header("X-Role","ADMIN")).andExpect(status().isForbidden());
  when(identity.resolve(any(),anyString())).thenThrow(new IllegalStateException("private-secret"));
  mvc.perform(get(BASE).cookie(cookie)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("USER_SERVICE_UNAVAILABLE"));verifyNoInteractions(market);
 }
 @Test void safeUpstreamErrorsAndMalformedResponses()throws Exception {
  for(int status:new int[]{400,401,403,404,503}) {
   String code=switch(status){case 400->"MARKET_ADMIN_QUERY_INVALID";case 404->"MARKET_ADMIN_RESOURCE_NOT_FOUND";default->"UNKNOWN";};
   when(market.get(anyString(),anyMap(),anyMap())).thenReturn(reply(status,"{\"code\":\""+code+"\",\"message\":\"private-secret\"}"));
   mvc.perform(get(BASE).cookie(cookie)).andExpect(status().is(status)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-secret"))));
  }
  for(String bad:List.of("not-json","null","{\"code\":\"OK\",\"data\":null}","{\"code\":\"OK\",\"data\":{}} trailing")) {
   when(market.get(anyString(),anyMap(),anyMap())).thenReturn(reply(200,bad));mvc.perform(get(BASE).cookie(cookie)).andExpect(status().isServiceUnavailable());
  }
 }
 @Test void enforcesDecodedResponseLimitIncludingBoundary()throws Exception {
  String json="{\"code\":\"OK\",\"data\":{}}";
  when(market.get(anyString(),anyMap(),anyMap())).thenReturn(reply(200,json+" ".repeat(262144-json.length())));
  mvc.perform(get(BASE).cookie(cookie)).andExpect(status().isOk());
  when(market.get(anyString(),anyMap(),anyMap())).thenReturn(ResponseEntity.ok(new byte[262145]));mvc.perform(get(BASE).cookie(cookie)).andExpect(status().isServiceUnavailable());
 }
 @Test void preflightHasNoIdentityOrBusinessSideEffects()throws Exception {
  for(String path:List.of(BASE,HISTORY,DETAIL))mvc.perform(options(path).header("Origin","http://127.0.0.1:15173").header("Access-Control-Request-Method","GET")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(options(BASE).header("Origin","https://outside.invalid").header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden());
  mvc.perform(options(BASE).header("Origin","http://127.0.0.1:15173").header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());verifyNoInteractions(identity,market,user);
 }

 @Test void lifecycleReadsRequireAdminAndPreserveValidatedHistoryPagination() throws Exception {
  String path=DETAIL+"/lifecycle";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  when(identity.resolve(any(),anyString())).thenReturn(ApiResponse.success(new StatisticsIdentityClient.Identity(UUID.randomUUID(),"USER",0),"test"));
  mvc.perform(get(path).cookie(cookie)).andExpect(status().isForbidden());
  when(identity.resolve(any(),anyString())).thenReturn(ApiResponse.success(new StatisticsIdentityClient.Identity(UUID.randomUUID(),"ADMIN",0),"test"));
  mvc.perform(post(path).cookie(cookie)).andExpect(status().isNotFound());
  mvc.perform(get(path+"/history").cookie(cookie).param("limit","21")).andExpect(status().isBadRequest());
  mvc.perform(get(path+"/history").cookie(cookie).param("limit","1","2")).andExpect(status().isBadRequest());
  verifyNoInteractions(market);
  when(market.get(anyString(),anyMap(),anyMap())).thenReturn(reply(200,"{\"code\":\"OK\",\"data\":{\"items\":[],\"nextCursor\":null}}"));
  mvc.perform(get(path+"/history").cookie(cookie).param("limit","3")).andExpect(status().isOk())
      .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.items").isEmpty());
  verify(market).get(eq(path.substring("/api/v1/".length())+"/history"),eq(Map.of("limit","3")),anyMap());
  mvc.perform(get(path).cookie(cookie)).andExpect(status().isServiceUnavailable());
  String state="""
      {"code":"OK","data":{"instrumentId":"SH600030","versionId":"00000000-0000-0000-0000-000000000001",
      "useState":"DRAINING","revision":"2","cycleId":"00000000-0000-0000-0000-000000000002",
      "activeUses":1,"currentHead":false,"actionsReady":false}}
      """;
  when(market.get(anyString(),anyMap(),anyMap())).thenReturn(reply(200,state));
  mvc.perform(get(path).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.data.useState").value("DRAINING"));
  when(market.get(anyString(),anyMap(),anyMap())).thenReturn(reply(200,state.replace("SH600030","SZ000001")));
  mvc.perform(get(path).cookie(cookie)).andExpect(status().isServiceUnavailable());
  when(market.get(anyString(),anyMap(),anyMap())).thenReturn(reply(200,state.replace("\"activeUses\":1","\"activeUses\":1,\"unexpected\":\"private-data\"")));
  mvc.perform(get(path).cookie(cookie)).andExpect(status().isServiceUnavailable())
      .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-data"))));
  String history="""
      {"code":"OK","data":{"items":[{"operationId":"00000000-0000-0000-0000-000000000003",
      "instrumentId":"SH600030","versionId":"00000000-0000-0000-0000-000000000001",
      "action":"DISABLE","expectedRevision":"1","reason":"fixture","state":"SUCCEEDED",
      "errorCode":null,"revision":"2","beforeState":"ENABLED","afterState":"DISABLED",
      "cycleId":null,"createdAt":"2026-11-10T00:00:00Z","finishedAt":"2026-11-10T00:00:01Z"}],"nextCursor":null}}
      """;
  when(market.get(anyString(),anyMap(),anyMap())).thenReturn(reply(200,history));
  mvc.perform(get(path+"/history").cookie(cookie)).andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items[0].action").value("DISABLE"));
  when(market.get(anyString(),anyMap(),anyMap())).thenReturn(reply(200,history.replace("\"errorCode\":null","\"errorCode\":{}")));
  mvc.perform(get(path+"/history").cookie(cookie)).andExpect(status().isServiceUnavailable());
 }

 @Test void lifecycleActionRequiresOriginAndBindsResponseToTheOriginalCommand() throws Exception {
  var actionClient=mock(MarketReviewPublicClient.class);
  String origin="http://127.0.0.1:15173";
  var actionMvc=MockMvcBuilders.standaloneSetup(new MarketLifecycleActionProxyController(
      actionClient,new AdminReadAccess(identity,"server-test"),new ObjectMapper(),origin))
      .setControllerAdvice(new ApiExceptionHandler()).build();
  String path=DETAIL+"/lifecycle/actions";
  String operation="00000000-0000-0000-0000-000000000003";
  String body="""
      {"operationId":"00000000-0000-0000-0000-000000000003","action":"DISABLE","expectedRevision":"1","reason":"fixture"}
      """;
  actionMvc.perform(post(path).cookie(cookie).contentType("application/json").content(body))
      .andExpect(status().isForbidden());
  actionMvc.perform(post(path).cookie(cookie).header("Origin",origin).header("X-Aistock-Admin-Action","1")
      .contentType("application/json").content(body.replace("fixture","fixture\\nline"))).andExpect(status().isBadRequest());
  verifyNoInteractions(actionClient);
  String response="""
      {"code":"OK","data":{"operationId":"00000000-0000-0000-0000-000000000003",
      "instrumentId":"SH600030","versionId":"00000000-0000-0000-0000-000000000001",
      "action":"DISABLE","expectedRevision":"1","reason":"fixture","state":"WAITING",
      "errorCode":null,"revision":"2","beforeState":"ENABLED","afterState":"DRAINING",
      "cycleId":null,"createdAt":"2026-11-10T00:00:00Z","finishedAt":null}}
      """;
  when(actionClient.post(anyString(),any(),anyMap())).thenReturn(reply(200,response));
  actionMvc.perform(post(path).cookie(cookie).header("Origin",origin).header("X-Aistock-Admin-Action","1")
      .contentType("application/json").content(body)).andExpect(status().isOk())
      .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.state").value("WAITING"));
  when(actionClient.get(anyString(),anyMap())).thenReturn(reply(200,response.replace(operation,"00000000-0000-0000-0000-000000000004")));
  actionMvc.perform(get(path+"/"+operation).cookie(cookie)).andExpect(status().isServiceUnavailable());
 }
}
