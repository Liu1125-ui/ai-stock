package com.aistock.notification.email;

import com.aistock.common.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class EmailDeliveryServiceTest {
    @Test void duplicateAcceptanceReturnsSameRecordAndRejectsDifferentPayload() {
        var db = new EmailDatabaseFixture();
        MailTransport transport = new MailTransport() {
            public boolean available() { return true; }
            public void send(EmailCommand command) { }
        };
        var service = new EmailDeliveryService(db.jdbc, db.codec(), transport, db.clock);
        var command = new EmailCommand(UUID.randomUUID(), UUID.randomUUID(), "member@example.com", "123456", db.clock.instant().plusSeconds(600));
        assertEquals(service.accept(command), service.accept(command));
        assertEquals(1, db.jdbc.queryForObject("select count(*) from email_delivery where request_id=?", Integer.class, command.requestId().toString()));
        var different = new EmailCommand(command.requestId(), command.challengeId(), command.email(), "654321", command.expiresAt());
        assertEquals("REQUEST_CONFLICT", assertThrows(BusinessException.class, () -> service.accept(different)).code());
        assertFalse(command.toString().contains(command.code()));
        assertFalse(db.jdbc.queryForObject("select hex(payload_ciphertext) from email_delivery where request_id=?", String.class, command.requestId().toString()).contains("313233343536"));
    }
    @Test void missingSmtpRefusesAcceptanceAndDoesNotQueue() {
        var db = new EmailDatabaseFixture();
        MailTransport unavailable = new MailTransport() {
            public boolean available() { return false; }
            public void send(EmailCommand command) { fail("Unavailable SMTP cannot send"); }
        };
        var service = new EmailDeliveryService(db.jdbc, db.codec(), unavailable, db.clock);
        var command = new EmailCommand(UUID.randomUUID(), UUID.randomUUID(), "member@example.com", "123456", db.clock.instant().plusSeconds(600));
        assertEquals("EMAIL_SERVICE_UNAVAILABLE", assertThrows(BusinessException.class, () -> service.accept(command)).code());
        assertEquals(0, db.jdbc.queryForObject("select count(*) from email_delivery where request_id=?", Integer.class, command.requestId().toString()));
    }
}
