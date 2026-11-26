package com.aistock.user.account;
import com.aistock.user.admin.AdminUserService;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
@Configuration public class AccountConfiguration {
    @Bean ProfileService profiles(JdbcTemplate jdbc,Clock authClock){return new ProfileService(jdbc,authClock);}
    @Bean AdminUserService admins(JdbcTemplate jdbc,Clock authClock){return new AdminUserService(jdbc,authClock);}
}
