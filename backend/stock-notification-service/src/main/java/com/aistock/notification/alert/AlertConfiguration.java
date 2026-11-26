package com.aistock.notification.alert;
import java.time.Clock;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;
@Configuration public class AlertConfiguration {
 @Bean AlertRuleService alertRules(AlertRuleRepository repo,AlertBoundaryClient client,Clock clock,@Value("${aistock.internal.token:}")String token,@Value("${aistock.alert.max-rules:50}")int limit,@Value("${aistock.alert.evaluation-enabled:false}")boolean enabled){return new AlertRuleService(repo,instrument->{var r=client.get(instrument,token);if(r==null||!"OK".equals(r.code()))throw AlertRuleService.invalid();return r.data();},clock,limit,enabled);}
 @Bean NotificationPreferenceService notificationPreferences(AlertRuleRepository repo,Clock clock,com.aistock.notification.alertmail.AlertMailConfiguration.Policy policy,com.aistock.notification.alertmail.SmtpAlertMailTransport smtp){return new NotificationPreferenceService(repo,clock,()->!policy.enabled()?"NOT_ENABLED":!smtp.ready()?"NOT_CONFIGURED":"ENABLED");}
}
