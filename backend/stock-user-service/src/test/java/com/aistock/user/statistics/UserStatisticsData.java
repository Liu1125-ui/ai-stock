package com.aistock.user.statistics;
import com.aistock.user.support.AuthDatabaseFixture;import com.aistock.user.account.*;import com.aistock.user.session.*;import java.time.*;import java.sql.Timestamp;import java.util.*;
final class UserStatisticsData {
 static final String PASSWORD="Statistics-test-password-1";
 static UUID account(AuthDatabaseFixture db,String email,String role,String status,Instant time){var id=UUID.randomUUID();new UserRepository(db.jdbc()).insertVerifiedOrdinaryUser(id,email,new PasswordHasher().encode(PASSWORD),"test",time);db.jdbc().update("update user_account set role_code=?,status=? where user_id=?",role,status,id.toString());return id;}
 static void session(AuthDatabaseFixture db,UUID user,String start,boolean revoked){var time=Instant.parse(start);new SessionRepository(db.jdbc()).create(UUID.randomUUID(),user,UUID.randomUUID().toString(),1,time,time.plusSeconds(60));if(revoked)db.jdbc().update("update login_session set revoked_at=? where user_id=?",Timestamp.from(time.plusSeconds(30)),user.toString());}
}
