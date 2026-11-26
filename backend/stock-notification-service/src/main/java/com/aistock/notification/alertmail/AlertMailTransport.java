package com.aistock.notification.alertmail;
import com.aistock.notification.alert.AlertMailAccountClient;
public interface AlertMailTransport {
 enum Result {ACCEPTED,DEFINITE_NOT_ACCEPTED,UNKNOWN}
 record Command(String recipient,String subject,String body){
  public Command {if(!AlertMailAccountClient.validAddress(recipient)||subject==null||subject.isBlank()||subject.length()>200||subject.chars().anyMatch(Character::isISOControl)||body==null||body.isBlank()||body.length()>16000)throw new IllegalArgumentException("Invalid alert mail command");}
  @Override public String toString(){return "AlertMailCommand[redacted]";}
 }
 boolean ready();Result send(Command command);
}
