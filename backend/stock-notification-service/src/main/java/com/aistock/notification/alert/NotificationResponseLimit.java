package com.aistock.notification.alert;
import feign.Capability;
import feign.Client;
import java.io.IOException;
/** Limits internal responses before JSON decoding, including errors and unknown lengths. */
public final class NotificationResponseLimit implements Capability {
 @Override public Client enrich(Client client){return (request,options)->{var response=client.execute(request,options);if(response.body()==null)return response;try(var body=response.body()){byte[] bytes=body.asInputStream().readNBytes(65537);if(bytes.length>65536)throw new IOException("Internal response exceeds configured safety limit");return response.toBuilder().body(bytes).build();}};}
}
