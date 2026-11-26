package com.aistock.notification.alertmail;
import java.io.*;import java.net.*;import java.util.concurrent.*;import org.springframework.mail.javamail.JavaMailSenderImpl;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class SmtpAlertMailTransportTest {
 @Test void localSmtpAcceptRejectAndLostAcknowledgementAreDistinct()throws Exception{
  for(String mode:new String[]{"accept","reject","drop"})try(var server=new Fixture(mode)){
   var sender=new JavaMailSenderImpl();sender.setHost("127.0.0.1");sender.setPort(server.socket.getLocalPort());sender.getJavaMailProperties().setProperty("mail.smtp.timeout","1000");sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout","1000");sender.getJavaMailProperties().setProperty("mail.smtp.writetimeout","1000");
   try(var transport=new SmtpAlertMailTransport(sender,"sender@example.com",true)){
    var result=transport.send(new AlertMailTransport.Command("recipient@example.com","股票预警","固定证据"));
    assertEquals(mode.equals("accept")?AlertMailTransport.Result.ACCEPTED:mode.equals("reject")?AlertMailTransport.Result.DEFINITE_NOT_ACCEPTED:AlertMailTransport.Result.UNKNOWN,result);
   }
  }
 }
 @Test void refusedConnectionIsDefinitelyNotAccepted()throws Exception {
  int port;try(var reserved=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=reserved.getLocalPort();}
  var sender=new JavaMailSenderImpl();sender.setHost("127.0.0.1");sender.setPort(port);
  sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout","1000");
  try(var transport=new SmtpAlertMailTransport(sender,"sender@example.com",true)){
   assertEquals(AlertMailTransport.Result.DEFINITE_NOT_ACCEPTED,transport.send(new AlertMailTransport.Command("recipient@example.com","预警","固定依据")));
  }
 }
 @Test void notReadyAndInvalidHeaderNeverOpenSmtp(){var sender=new JavaMailSenderImpl();try(var t=new SmtpAlertMailTransport(sender,"sender@example.com",false)){assertFalse(t.ready());assertEquals(AlertMailTransport.Result.DEFINITE_NOT_ACCEPTED,t.send(new AlertMailTransport.Command("recipient@example.com","股票预警","body")));assertThrows(IllegalArgumentException.class,()->new AlertMailTransport.Command("recipient@example.com\r\nBcc:bad@example.com","股票预警","body"));}}
 static class Fixture implements AutoCloseable {
  final ServerSocket socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress());final ExecutorService executor=Executors.newSingleThreadExecutor();
  Fixture(String mode)throws IOException{executor.submit(()->{try(var s=socket.accept();var reader=new BufferedReader(new InputStreamReader(s.getInputStream(),java.nio.charset.StandardCharsets.UTF_8));var out=new PrintWriter(s.getOutputStream(),true)){out.print("220 fixture\r\n");out.flush();boolean data=false;String line;while((line=reader.readLine())!=null){if(data){if(!line.equals("."))continue;if(mode.equals("drop"))return;out.print("250 accepted\r\n");data=false;}else if(line.startsWith("EHLO")||line.startsWith("HELO"))out.print("250 fixture\r\n");else if(line.startsWith("MAIL"))out.print("250 ok\r\n");else if(line.startsWith("RCPT"))out.print(mode.equals("reject")?"550 rejected\r\n":"250 ok\r\n");else if(line.equals("DATA")){out.print("354 continue\r\n");data=true;}else if(line.equals("QUIT")){out.print("221 bye\r\n");out.flush();return;}else out.print("250 ok\r\n");out.flush();}}catch(IOException ignored){}});}
  public void close()throws IOException{socket.close();executor.shutdownNow();}
 }
}
