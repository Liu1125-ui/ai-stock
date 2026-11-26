package com.aistock.user.registration;
import com.aistock.user.account.PasswordHasher;
import com.aistock.user.support.AuthDatabaseFixture;
import com.aistock.common.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@Tag("mysql") class RegistrationSubmissionGuardTest {
 @Test void missingChallengeNeverHashesPasswordAndSubmissionsHaveSourceQuota(){
  var db=new AuthDatabaseFixture();var passwords=spy(new PasswordHasher());var key=new byte[32];
  var service=new RegistrationService(db.jdbc(),key,key,db.clock(),passwords);
  var input=new RegisterCommand(UUID.randomUUID(),UUID.randomUUID(),"member@example.com","123456","Long-test-password-1","测试用户");
  for(int i=0;i<30;i++)assertEquals("CHALLENGE_INVALID",assertThrows(BusinessException.class,()->service.register(input,"client-one")).code());
  assertEquals("RATE_LIMITED",assertThrows(BusinessException.class,()->service.register(input,"client-one")).code());
  assertEquals("CHALLENGE_INVALID",assertThrows(BusinessException.class,()->service.register(input,"client-two")).code());
  verify(passwords,never()).encode(anyString());
 }
}
