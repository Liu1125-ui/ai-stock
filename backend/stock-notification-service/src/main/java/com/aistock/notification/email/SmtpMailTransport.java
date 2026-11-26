package com.aistock.notification.email;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public final class SmtpMailTransport implements MailTransport {
    private final JavaMailSender sender;
    private final String from;
    private final boolean configured;
    public SmtpMailTransport(JavaMailSender sender, String from, boolean configured) { this.sender = sender; this.from = from; this.configured = configured; }
    @Override public boolean available() { return configured; }
    @Override public void send(EmailCommand command) {
        if (!configured) throw new IllegalStateException("SMTP not configured");
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(command.email()); message.setSubject("股票时序分析系统：邮箱验证");
        message.setText("你好！你的邮箱验证码是：" + command.code() + "\n请在10分钟内完成验证。若不是你本人操作，请忽略本邮件。\n本邮件由毕业设计系统自动发送。");
        sender.send(message);
    }
}
