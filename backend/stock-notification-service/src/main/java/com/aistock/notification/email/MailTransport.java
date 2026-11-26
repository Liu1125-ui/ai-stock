package com.aistock.notification.email;
public interface MailTransport {
    boolean available();
    void send(EmailCommand command);
}
