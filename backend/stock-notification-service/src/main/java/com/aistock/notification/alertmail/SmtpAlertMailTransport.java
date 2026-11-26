package com.aistock.notification.alertmail;
import org.springframework.mail.*;import org.springframework.mail.javamail.JavaMailSender;
import org.eclipse.angus.mail.smtp.*;import java.util.concurrent.*;
/** Bounded single SMTP attempt. Ambiguous submission is never retried as a definite rejection. */
public final class SmtpAlertMailTransport implements AlertMailTransport,AutoCloseable {
 private final JavaMailSender sender;private final String from;private final boolean ready;
 private final ExecutorService executor=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{var t=new Thread(r,"alert-smtp");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 public SmtpAlertMailTransport(JavaMailSender sender,String from,boolean ready){this.sender=sender;this.from=from;this.ready=ready&&com.aistock.notification.alert.AlertMailAccountClient.validAddress(from);}
 public boolean ready(){return ready;}
 public Result send(Command command){if(!ready)return Result.DEFINITE_NOT_ACCEPTED;Future<Result> attempt;
  try{attempt=executor.submit(()->{
   var m=new SimpleMailMessage();m.setFrom(from);m.setTo(command.recipient());m.setSubject(command.subject());m.setText(command.body());
   try{sender.send(m);return Result.ACCEPTED;}catch(MailAuthenticationException denied){return Result.DEFINITE_NOT_ACCEPTED;}catch(MailSendException error){return definitelyRejected(error)?Result.DEFINITE_NOT_ACCEPTED:Result.UNKNOWN;}catch(RuntimeException unknown){return Result.UNKNOWN;}
  });}catch(RejectedExecutionException notStarted){return Result.DEFINITE_NOT_ACCEPTED;}
  try{return attempt.get(30,TimeUnit.SECONDS);}catch(InterruptedException unknown){Thread.currentThread().interrupt();attempt.cancel(true);return Result.UNKNOWN;}catch(Exception unknown){attempt.cancel(true);return Result.UNKNOWN;}
 }
 private static boolean definitelyRejected(MailSendException error){
  var failures=error.getMessageExceptions();if(failures.length!=1)return false;
  // MailConnectException is emitted only by the SMTP connect phase, before submission.
  // Generic socket errors/timeouts after DATA remain UNKNOWN.
  Throwable e=failures[0];while(e!=null){
   if(e instanceof org.eclipse.angus.mail.util.MailConnectException)return true;
   if(e instanceof SMTPSendFailedException failed){if(failed.getValidSentAddresses()!=null&&failed.getValidSentAddresses().length>0)return false;String cmd=failed.getCommand();if(cmd!=null&&failed.getReturnCode()>=400&&failed.getReturnCode()<=599&&(cmd.startsWith("RCPT")||cmd.startsWith("MAIL")||cmd.equals("DATA")||cmd.equals(".")))return true;}
   if(e instanceof SMTPAddressFailedException failed&&failed.getReturnCode()>=400&&failed.getReturnCode()<=599&&failed.getCommand().startsWith("RCPT"))return true;
   e=e instanceof jakarta.mail.MessagingException me?me.getNextException():e.getCause();
  }return false;
 }
 public void close(){executor.shutdownNow();}
}
