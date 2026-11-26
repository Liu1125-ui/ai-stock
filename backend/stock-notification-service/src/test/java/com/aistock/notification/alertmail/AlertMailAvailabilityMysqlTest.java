package com.aistock.notification.alertmail;
import com.aistock.notification.alert.*;import java.util.concurrent.atomic.AtomicReference;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
@Tag("mysql") class AlertMailAvailabilityMysqlTest extends AlertMailDatabaseFixture {
 @Test void personalPreferenceAndServiceReadinessAreIndependent(){var availability=new AtomicReference<>("NOT_CONFIGURED");var service=new NotificationPreferenceService(new AlertRuleRepository(jdbc),clock,availability::get);assertFalse(service.get(owner).emailEnabled());assertEquals("NOT_CONFIGURED",service.get(owner).deliveryAvailability());var p=service.patch(owner,0L,true,true);assertTrue(p.emailEnabled());assertEquals("NOT_CONFIGURED",p.deliveryAvailability());availability.set("ENABLED");assertEquals("ENABLED",service.get(owner).deliveryAvailability());availability.set("NOT_ENABLED");assertEquals("NOT_ENABLED",service.get(owner).deliveryAvailability());}
}
