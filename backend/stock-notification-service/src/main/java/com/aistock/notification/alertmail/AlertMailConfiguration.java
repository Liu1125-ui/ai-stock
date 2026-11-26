package com.aistock.notification.alertmail;
import com.aistock.notification.alert.AlertMailAccountClient;import java.time.*;import java.util.HexFormat;import java.util.concurrent.*;
import org.springframework.context.annotation.*;import org.springframework.context.SmartLifecycle;import org.springframework.core.env.Environment;import org.springframework.mail.javamail.JavaMailSenderImpl;
@Configuration public class AlertMailConfiguration {
 public record Policy(boolean enabled,int maxAttempts,Duration lease,Duration ttl,long pollMs,long retryInitial,long retryLater){}
 @Bean public Policy policy(Environment env){boolean enabled=env.getProperty("aistock.alert.mail-worker-enabled",Boolean.class,false);int max=env.getProperty("aistock.alert.mail-max-attempts",Integer.class,3);long lease=env.getProperty("aistock.alert.mail-lease-seconds",Long.class,120L),ttl=env.getProperty("aistock.alert.mail-ttl-seconds",Long.class,86400L),poll=env.getProperty("aistock.alert.mail-poll-ms",Long.class,2000L);long initial=env.getProperty("aistock.alert.mail-retry-initial-seconds",Long.class,60L),later=env.getProperty("aistock.alert.mail-retry-later-seconds",Long.class,300L);if(initial<1||later<initial||later>86400||max<1||max>10||lease<60||lease>300||ttl<60||ttl>604800||poll<500||poll>3600000)throw new IllegalArgumentException("Invalid alert mail policy");return new Policy(enabled,max,Duration.ofSeconds(lease),Duration.ofSeconds(ttl),poll,initial,later);}
 @Bean public AlertMailCodec alertMailCodec(Environment env){return new AlertMailCodec(HexFormat.of().parseHex(env.getRequiredProperty("aistock.mail.payload-key")),HexFormat.of().parseHex(env.getRequiredProperty("aistock.mail.digest-key")));}
 @Bean(destroyMethod="close") public SmtpAlertMailTransport alertMailTransport(Environment env){
  var sender=new JavaMailSenderImpl();String host=env.getProperty("aistock.smtp.host",""),username=env.getProperty("aistock.smtp.username",""),password=env.getProperty("aistock.smtp.password",""),from=env.getProperty("aistock.smtp.from","");boolean ssl=env.getProperty("aistock.smtp.ssl-enabled",Boolean.class,true),tls=env.getProperty("aistock.smtp.starttls-required",Boolean.class,false);
  sender.setHost(host);sender.setPort(env.getProperty("aistock.smtp.port",Integer.class,465));sender.setUsername(username);sender.setPassword(password);sender.setDefaultEncoding("UTF-8");var props=sender.getJavaMailProperties();props.setProperty("mail.smtp.auth","true");props.setProperty("mail.smtp.ssl.enable",Boolean.toString(ssl));props.setProperty("mail.smtp.ssl.checkserveridentity","true");props.setProperty("mail.smtp.starttls.enable",Boolean.toString(tls));props.setProperty("mail.smtp.starttls.required",Boolean.toString(tls));for(String k:new String[]{"connectiontimeout","timeout","writetimeout"})props.setProperty("mail.smtp."+k,"5000");
  return new SmtpAlertMailTransport(sender,from,!host.isBlank()&&!username.isBlank()&&!password.isBlank()&&(ssl||tls));
 }
 @Bean public AlertMailWorker alertMailWorker(AlertMailRepository repo,AlertMailAccountClient accounts,AlertMailCodec codec,SmtpAlertMailTransport smtp,Clock clock,Policy policy){return new AlertMailWorker(repo,accounts,codec,smtp,clock,policy.enabled(),policy.maxAttempts(),policy.lease(),policy.retryInitial(),policy.retryLater());}
 @Bean public AlertMailViewService alertMailViewService(AlertMailRepository repo){return new AlertMailViewService(repo);}
 @Bean public Ticker alertMailTicker(AlertMailWorker worker,Clock clock,Policy policy){return new Ticker(worker,clock,policy);}
 /** Dedicated lifecycle executor: slow alert SMTP cannot block the registration scheduler. */
 public static final class Ticker implements SmartLifecycle {
  private final AlertMailWorker worker;private final Clock clock;private final Policy policy;private volatile ScheduledExecutorService executor;private volatile boolean running;
  Ticker(AlertMailWorker worker,Clock clock,Policy policy){this.worker=worker;this.clock=clock;this.policy=policy;}
  public synchronized void start(){if(running)return;running=true;executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"alert-mail-scan");t.setDaemon(true);return t;});executor.scheduleWithFixedDelay(()->{try{worker.runOnce(clock.instant());}catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger(Ticker.class).warn("Alert mail scan temporarily unavailable");}},policy.pollMs(),policy.pollMs(),TimeUnit.MILLISECONDS);}
  public synchronized void stop(){running=false;if(executor!=null)executor.shutdownNow();}public boolean isRunning(){return running;}
 }
}
