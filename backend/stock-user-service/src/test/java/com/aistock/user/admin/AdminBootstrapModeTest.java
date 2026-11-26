package com.aistock.user.admin;
import com.aistock.user.support.AuthDatabaseFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
@Tag("mysql") class AdminBootstrapModeTest {
    @Test void bootstrapContextStartsWithoutHttpControllersOrAutomaticAccountCreation(){
        var db=new AuthDatabaseFixture();
        var app=new SpringApplication(AdminBootstrapApplication.class);app.setWebApplicationType(WebApplicationType.NONE);app.setAdditionalProfiles("admin-bootstrap");
        app.setDefaultProperties(Map.of("spring.datasource.url",System.getenv("AUTH_TEST_DB_URL"),"spring.datasource.username","root",
                "spring.datasource.password",System.getenv("AUTH_TEST_DB_PASSWORD"),"spring.cloud.nacos.discovery.enabled","false","logging.level.root","ERROR","spring.main.banner-mode","off"));
        try(var context=app.run()){
            assertEquals(0,context.getBeansOfType(com.aistock.user.account.ProfileController.class).size());
            assertEquals(0,context.getBeansOfType(AdminUserController.class).size());
            assertFalse(context instanceof org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext);
            assertEquals(0,db.jdbc().queryForObject("select count(*) from user_account",Integer.class));
        }
    }
}
