package com.aistock.prediction.explanation;
import com.aistock.common.BusinessException;
import com.aistock.prediction.comparisonai.*;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class ComparisonMaasClientTest {
 final ObjectMapper m=new ObjectMapper();
 Map<String,String> facts(){return ComparisonAiFacts.build(ComparisonAiSamples.saved(),"model",m).values();}
 @Test void dedicatedPromptAndStrictInnerAndOuterJson()throws Exception {
  for(String mode:List.of("ok","inner-duplicate","inner-trailing","outer-duplicate","outer-trailing","empty","markdown")){
   var captured=new java.util.concurrent.atomic.AtomicReference<JsonNode>();
   var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   server.createContext("/chat",e->{
    captured.set(m.readTree(e.getRequestBody()));
    String content=switch(mode){case "inner-duplicate"->"{\"paragraphs\":[],\"paragraphs\":[]}";case "inner-trailing"->"{\"paragraphs\":[]} {}";case "empty"->"";case "markdown"->"```json\n{\"paragraphs\":[]}\n```";default->"{\"paragraphs\":[]}";};
    var envelope=m.createObjectNode();envelope.putArray("choices").addObject().putObject("message").put("content",content);
    String body=mode.equals("outer-duplicate")?"{\"choices\":[],\"choices\":[]}":m.writeValueAsString(envelope)+(mode.equals("outer-trailing")?" {}":"");
    byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();
   });server.start();
   try {
    var c=new HttpMaasClient("http://127.0.0.1:"+server.getAddress().getPort()+"/chat","model","private",true,m,Duration.ofSeconds(2),true);
    if(mode.equals("ok")){assertTrue(c.generateComparison(facts()).has("paragraphs"));assertTrue(captured.get().path("messages").get(0).path("content").asText().contains("RETURN_RELATIONS"));assertEquals(1200,captured.get().path("max_tokens").asInt());assertFalse(captured.get().path("stream").asBoolean(true));}
    else {var ex=assertThrows(BusinessException.class,()->c.generateComparison(facts()));assertEquals("AI_OUTPUT_INVALID",ex.code(),mode);}
   }finally{server.stop(0);}
  }
 }
 @Test void categorizesProviderFailuresWithoutLeakingKey()throws Exception {
  for(int status:new int[]{401,403,429,500}){
   var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/chat",e->{e.getRequestBody().readAllBytes();byte[] bytes="{}".getBytes();e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();
   try{var c=new HttpMaasClient("http://127.0.0.1:"+server.getAddress().getPort()+"/chat","model","private",true,m,Duration.ofSeconds(1),true);var error=assertThrows(BusinessException.class,()->c.generateComparison(facts()));assertEquals(status==429?"AI_RATE_LIMITED":status==500?"AI_UNAVAILABLE":"AI_AUTH_DENIED",error.code());assertFalse(error.getMessage().contains("private"));}finally{server.stop(0);}
  }
 }
 @Test void emptyProviderContentHasBoundedDiagnostic(org.springframework.boot.test.system.CapturedOutput output)throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/chat",e->{e.getRequestBody().readAllBytes();var envelope=m.createObjectNode();var choice=envelope.putArray("choices").addObject().put("finish_reason","length");choice.putObject("message").put("content","");byte[] bytes=m.writeValueAsBytes(envelope);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();
  try{var c=new HttpMaasClient("http://127.0.0.1:"+server.getAddress().getPort()+"/chat","model","private-diagnostic-key",true,m,Duration.ofSeconds(2),true);assertEquals("AI_OUTPUT_INVALID",assertThrows(BusinessException.class,()->c.generateComparison(facts())).code());assertTrue(output.getAll().contains("rule=INNER_OBJECT_REQUIRED finishReason=length"));assertFalse(output.getAll().contains("private-diagnostic-key"));}finally{server.stop(0);}
 }
}
