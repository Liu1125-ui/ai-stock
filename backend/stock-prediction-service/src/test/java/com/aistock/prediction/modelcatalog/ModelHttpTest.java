package com.aistock.prediction.modelcatalog;
import com.aistock.common.*;
import com.aistock.prediction.security.*;
import com.aistock.prediction.training.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.mock.web.MockCookie;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Tag("mysql") @WebAppConfiguration @SpringJUnitConfig(ModelHttpTest.Config.class)
@org.springframework.test.context.TestPropertySource(properties="aistock.training.admission-enabled=true")
class ModelHttpTest {
    static final String SERVICE="private-model-test";
    @Configuration @EnableWebSecurity @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @Import({PredictionSecurityConfiguration.class,ModelControllers.class,ApiExceptionHandler.class,TrainingAdmissionController.class,TrainingAdmissionConfiguration.class})
    static class Config {
        @Bean ModelDatabaseFixture db(){return new ModelDatabaseFixture();}
        @Bean ModelRepository repo(ModelDatabaseFixture db,ObjectMapper mapper){return new ModelRepository(db.jdbc,mapper);}
        @Bean ArtifactInspector inspector(){return ModelCatalogMysqlTest::inspect;}
        @Bean ModelCatalogService catalog(ModelRepository r,ArtifactInspector i,ObjectMapper m){return new ModelCatalogService(r,i,ModelCatalogMysqlTest.NOW,m);}
        @Bean ModelReleaseService release(ModelRepository r,ArtifactInspector i,ObjectMapper m){return new ModelReleaseService(r,i,ModelCatalogMysqlTest.NOW,m);}
        @Bean TrainingProgressClients.Scheduler trainingScheduler(){return org.mockito.Mockito.mock(TrainingProgressClients.Scheduler.class);}
        @Bean TrainingProgressClients.Algorithm trainingAlgorithm(){return org.mockito.Mockito.mock(TrainingProgressClients.Algorithm.class);}
        @Bean java.time.Clock trainingClock(){return ModelCatalogMysqlTest.NOW;}
        @Bean org.springframework.jdbc.core.JdbcTemplate trainingJdbc(ModelDatabaseFixture db){return db.jdbc;}
        @Bean java.util.concurrent.atomic.AtomicBoolean experimentGranted(){return new java.util.concurrent.atomic.AtomicBoolean();}
        @Bean SessionIdentityClient identity(java.util.concurrent.atomic.AtomicBoolean experimentGranted){return (input,token)->ApiResponse.success(new SessionIdentityClient.Identity(UUID.nameUUIDFromBytes(input.token().getBytes()),input.token().equals("admin-session")?"ADMIN":"USER",input.token().equals("experiment-session")&&experimentGranted.get(),0),"test");}
        @Bean static org.springframework.context.support.PropertySourcesPlaceholderConfigurer properties(){var p=new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();var props=new java.util.Properties();props.setProperty("aistock.internal.token",SERVICE);
            props.setProperty("aistock.training.admission-enabled","true");
            props.setProperty("aistock.training.catalog","{\"five-day\":{\"configurationHash\":\""+ "a".repeat(64)+"\",\"datasetHash\":\""+ "b".repeat(64)+"\"}}");p.setProperties(props);return p;}
    }
    @Autowired WebApplicationContext context;@Autowired ModelCatalogService catalog;@Autowired ObjectMapper mapper;@Autowired java.util.concurrent.atomic.AtomicBoolean experimentGranted;
    @BeforeEach void cleanIsolatedModels(){new ModelDatabaseFixture();experimentGranted.set(false);}
    private MockMvc mvc(){return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    private MockCookie cookie(String token){return new MockCookie("AISTOCK_SESSION",token);}
    @Test void publicRequiresSessionAndForgedRoleCannotReadAdmin()throws Exception{
        mvc().perform(get("/api/v1/models").header("X-Service-Token",SERVICE).header("X-Role","ADMIN")).andExpect(status().isUnauthorized());
        mvc().perform(get("/api/v1/admin/models").cookie(cookie("user-session")).header("X-Role","ADMIN")).andExpect(status().isForbidden());
        mvc().perform(get("/api/v1/models").cookie(cookie("user-session"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray());
    }
    @Test void adminCanReadButOriginCsrfAndUnknownActorInputAreRejected()throws Exception{
        var v=catalog.register(ModelCatalogMysqlTest.candidate(42));var uri="/api/v1/admin/models/"+v.modelId()+"/publish";
        mvc().perform(get("/api/v1/admin/models").cookie(cookie("admin-session"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.head.currentModelId").isEmpty());
        mvc().perform(post(uri).cookie(cookie("admin-session")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc().perform(post(uri).cookie(cookie("admin-session")).header("Origin","http://127.0.0.1:15173").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        var csrf=new MockCookie("XSRF-TOKEN","known-csrf");
        mvc().perform(post(uri).cookie(cookie("admin-session"),csrf).header("Origin","http://127.0.0.1:15173").header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content("{\"actorId\":\"forged\"}")).andExpect(status().isBadRequest());
        var action=new ModelRecords.ActionInput(UUID.randomUUID(),v.revision(),catalog.head().revision(),"已查看真实误差与基线并确认回顾性实验局限",true);
        mvc().perform(post(uri).cookie(cookie("admin-session"),csrf).header("Origin","http://127.0.0.1:15173").header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content(mapper.writeValueAsString(action))).andExpect(status().isOk()).andExpect(jsonPath("$.data.model.state").value("PUBLISHED"));
    }
    @Test void publicationRequiresServiceIdentityAndRejectsPendingOrMissing()throws Exception{
        var v=catalog.register(ModelCatalogMysqlTest.candidate(42));var uri="/internal/v1/models/"+v.modelId()+"/publication";
        mvc().perform(get(uri).cookie(cookie("admin-session"))).andExpect(status().isUnauthorized());
        mvc().perform(get(uri+";x").header("X-Service-Token",SERVICE)).andExpect(status().is4xxClientError());
        mvc().perform(get(uri).header("X-Service-Token",SERVICE)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODEL_NOT_PUBLISHED"));
        mvc().perform(get("/internal/v1/models/"+UUID.randomUUID()+"/publication").header("X-Service-Token",SERVICE)).andExpect(status().isNotFound());
    }
    @Test void publicationDescriptorReflectsPublishedVersionAndRejectsOffline()throws Exception{
        var v=catalog.register(ModelCatalogMysqlTest.candidate(42));var repo=context.getBean(ModelRepository.class);var release=context.getBean(ModelReleaseService.class);
        var actor=new ModelRecords.Actor(UUID.randomUUID(),"ADMIN");
        var a=new ModelRecords.ActionInput(UUID.randomUUID(),v.revision(),repo.head().revision(),"已核对模型版本与回顾性局限用于隔离测试",true);
        var published=release.mutate(actor,v.modelId(),"publish",a);var uri="/internal/v1/models/"+v.modelId()+"/publication";
        mvc().perform(get(uri).header("X-Service-Token",SERVICE)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.modelId").value(v.modelId().toString())).andExpect(jsonPath("$.data.artifactId").value(v.artifactId()))
            .andExpect(jsonPath("$.data.manifestHash").value(v.manifestHash())).andExpect(jsonPath("$.data.checkpointHash").value(v.checkpointHash()))
            .andExpect(jsonPath("$.data.publicationRevision").value(1));
        release.mutate(actor,v.modelId(),"offline",new ModelRecords.ActionInput(UUID.randomUUID(),published.model().revision(),repo.head().revision(),"已结束本机隔离契约测试并下线该版本",false));
        mvc().perform(get(uri).header("X-Service-Token",SERVICE)).andExpect(status().isConflict()).andExpect(jsonPath("$.data").isEmpty());
    }
    @Test void internalRegistrationAuthenticatesBeforeBodyAndBoundsSize()throws Exception{
        mvc().perform(post("/internal/v1/models/candidates").contentType("application/json").content("{")).andExpect(status().isUnauthorized());
        mvc().perform(post("/internal/v1/models/candidates;x").contentType("application/json").content("{")).andExpect(status().is4xxClientError());
        mvc().perform(post("/internal/v1/models/candidates").header("X-Service-Token",SERVICE).contentType("application/json").content("x".repeat(65537))).andExpect(status().isPayloadTooLarge());
    }

    @Test void trainingUsesCurrentGrantAndReceiptsStayOwnerScoped() throws Exception {
        var mvc=mvc();
        String path="/api/v1/training-submissions", request=UUID.randomUUID().toString();
        var body=mapper.writeValueAsString(new com.aistock.common.training.TrainingSubmission(request,java.util.List.of("five-day")));
        mvc.perform(get(path+"/"+request)).andExpect(status().isUnauthorized());
        mvc.perform(trainingPost(path,"experiment-session",body).header("X-Role","ADMIN"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EXPERIMENT_ACCESS_DENIED"));
        experimentGranted.set(true);
        mvc.perform(trainingPost(path,"experiment-session",body).cookie(cookie("admin-session")))
            .andExpect(status().isUnauthorized());
        var first=mvc.perform(trainingPost(path,"experiment-session",body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].configurationId").value("five-day")).andReturn();
        var task=mapper.readTree(first.getResponse().getContentAsByteArray()).path("data").path("items").get(0).path("taskId").asText();
        mvc.perform(trainingPost(path,"experiment-session",body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].taskId").value(task));
        mvc.perform(get(path+"/options").cookie(cookie("experiment-session"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0]").value("five-day"));
        mvc.perform(get(path+"/"+request+"/status").cookie(cookie("experiment-session"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].state").value("AWAITING_DISPATCH"));
        mvc.perform(get(path+"/"+request+"/status").cookie(cookie("admin-session"))).andExpect(status().isNotFound());
        String metricsPath=path+"/"+request+"/tasks/"+task+"/metrics";
        mvc.perform(get(metricsPath).cookie(cookie("user-session"))).andExpect(status().isNotFound());
        mvc.perform(get(metricsPath).cookie(cookie("experiment-session"))).andExpect(status().isServiceUnavailable());
        var jdbc=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        jdbc.update("UPDATE training_submission_item SET dispatch_state='CONFIRMED' WHERE task_id=?",task);
        var scheduler=context.getBean(TrainingProgressClients.Scheduler.class);
        var algorithm=context.getBean(TrainingProgressClients.Algorithm.class);
        var model=UUID.randomUUID();
        var progress=new com.aistock.common.training.TrainingProgress(task,"SUCCEEDED",null,null,"c".repeat(64),model);
        org.mockito.Mockito.when(scheduler.status(task,SERVICE)).thenReturn(ApiResponse.success(progress,"test"));
        var report=mapper.createObjectNode();
        report.put("checkpointHash","c".repeat(64)).put("datasetHash","b".repeat(64))
            .put("seed",42).put("selectedEpoch",1).put("elapsedSeconds",2.5).put("trainingSamples",15);
        var evaluation=mapper.readTree("{\"mae\":0.0249,\"rmse\":0.035,\"baselineMae\":0.0242,\"direction\":0.5,\"samples\":5}");
        report.set("heldout2025",evaluation);
        report.set("heldout2026",evaluation);
        report.putArray("epochHistory").addObject().put("epoch",1).put("trainingMse",0.003).set("validation",evaluation);
        org.mockito.Mockito.when(algorithm.metrics(task,SERVICE)).thenReturn(ApiResponse.success(report,"test"));
        mvc.perform(get(metricsPath).cookie(cookie("experiment-session"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.heldout2026.mae").value(0.0249))
            .andExpect(jsonPath("$.data.checkpointHash").doesNotExist());
        var rolling=report.deepCopy();
        rolling.remove(java.util.List.of("heldout2025","heldout2026"));
        rolling.set("holdout",evaluation);
        rolling.putObject("trainingData").put("datasetHash",report.path("datasetHash").textValue())
            .put("seed",42).putObject("holdout").put("from","2025-07-01").put("through","2026-09-24");
        org.mockito.Mockito.when(algorithm.metrics(task,SERVICE)).thenReturn(ApiResponse.success(rolling,"test"));
        mvc.perform(get(metricsPath).cookie(cookie("experiment-session"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.holdout.mae").value(0.0249))
            .andExpect(jsonPath("$.data.holdout.from").value("2025-07-01"))
            .andExpect(jsonPath("$.data.heldout2026").doesNotExist());
        ((com.fasterxml.jackson.databind.node.ObjectNode)rolling.path("trainingData")).put("datasetHash","f".repeat(64));
        mvc.perform(get(metricsPath).cookie(cookie("experiment-session"))).andExpect(status().isServiceUnavailable());
        org.mockito.Mockito.when(algorithm.metrics(task,SERVICE)).thenReturn(ApiResponse.success(report,"test"));
        report.put("checkpointHash","d".repeat(64));
        mvc.perform(get(metricsPath).cookie(cookie("experiment-session"))).andExpect(status().isServiceUnavailable());
        experimentGranted.set(false);
        mvc.perform(get(path).cookie(cookie("experiment-session"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].requestId").value(request));
        mvc.perform(get(path).cookie(cookie("user-session"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(trainingPost(path,"experiment-session",body)).andExpect(status().isForbidden());
        mvc.perform(get(path+"/"+request).cookie(cookie("experiment-session"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].taskId").value(task));
        mvc.perform(get(path+"/"+request).cookie(cookie("user-session"))).andExpect(status().isNotFound());
        mvc.perform(get(path+"/"+request).cookie(cookie("admin-session"))).andExpect(status().isNotFound());
        mvc.perform(trainingPost(path,"admin-session",body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].taskId").value(org.hamcrest.Matchers.not(task)));
    }

    @Test void experimentGrantCannotBeCoercedFromStringOrNumber() throws Exception {
        String base="{\"userId\":\"00000000-0000-0000-0000-000000000001\",\"role\":\"USER\",\"credentialVersion\":0,\"experimentAllowed\":";
        for(String value:java.util.List.of("\"true\"","1")) {
            org.junit.jupiter.api.Assertions.assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
                ()->mapper.readValue(base+value+"}",SessionIdentityClient.Identity.class));
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder trainingPost(String path,String session,String body) {
        return post(path).cookie(cookie(session),new MockCookie("XSRF-TOKEN","training-csrf"))
            .header("Origin","http://127.0.0.1:15173").header("X-XSRF-TOKEN","training-csrf")
            .contentType("application/json").content(body);
    }
}
