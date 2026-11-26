package com.aistock.notification.alert;
import feign.*;
import java.util.Map;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NotificationResponseLimitTest {
 private final Request request=Request.create(Request.HttpMethod.GET,"http://fixture.test",Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null);
 @Test void boundedSuccessAndErrorResponsesArePreserved()throws Exception{for(int status:new int[]{200,503}){Client original=(r,o)->Response.builder().request(r).status(status).reason("fixture").headers(Map.of()).body(new ByteArrayInputStream(new byte[]{1,2,3}),null).build();try(var response=new NotificationResponseLimit().enrich(original).execute(request,new Request.Options())){assertEquals(status,response.status());assertArrayEquals(new byte[]{1,2,3},response.body().asInputStream().readAllBytes());}}}
 @Test void oversizedUnknownLengthBodyIsRejectedAndClosed(){var closed=new boolean[1];Client original=(r,o)->Response.builder().request(r).status(200).reason("fixture").headers(Map.of()).body(new ByteArrayInputStream(new byte[65537]){public void close()throws IOException{closed[0]=true;super.close();}},null).build();assertThrows(IOException.class,()->new NotificationResponseLimit().enrich(original).execute(request,new Request.Options()));assertTrue(closed[0]);}
}
