package com.aistock.prediction.modelcatalog;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
final class ModelDatabaseFixture {
    final JdbcTemplate jdbc;
    ModelDatabaseFixture(){
        var url=System.getenv("PREDICTION_TEST_DB_URL");var password=System.getenv("PREDICTION_TEST_DB_PASSWORD");
        if(!"jdbc:mysql://127.0.0.1:13316/stock_prediction_test?serverTimezone=UTC".equals(url)||password==null||password.isBlank())throw new IllegalStateException("Only isolated prediction test DB allowed");
        var ds=new DriverManagerDataSource(url,"root",password);ds.setDriverClassName("com.mysql.cj.jdbc.Driver");jdbc=new JdbcTemplate(ds);
        if(!"stock_prediction_test".equals(jdbc.queryForObject("select database()",String.class)))throw new IllegalStateException("Wrong test DB");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc.update("delete from training_submission_item");
        jdbc.update("delete from training_submission");
        jdbc.update("delete from comparison_ai_operation");jdbc.update("delete from comparison_ai_result");
        jdbc.update("delete from comparison_market_use");jdbc.update("delete from stock_comparison_operation");jdbc.update("delete from stock_comparison_result");
        jdbc.update("delete from prediction_market_use");jdbc.update("update prediction_explanation_slot set operation_id=null,lease_until=null,operation_kind=null where slot_id=1");jdbc.update("delete from stock_question_operation");jdbc.update("delete from stock_question_result");jdbc.update("delete from stock_analysis_market_use");jdbc.update("delete from stock_analysis_operation");jdbc.update("delete from stock_analysis_result");jdbc.update("delete from prediction_explanation_operation");jdbc.update("delete from prediction_explanation");jdbc.update("delete from forecast_verification_operation");jdbc.update("delete from forecast_verification_head");jdbc.update("delete from forecast_verification_version");jdbc.update("delete from forecast_request");jdbc.update("delete from forecast_user_guard");
        jdbc.update("delete from model_release_event");jdbc.update("delete from model_version");jdbc.update("update model_release_head set current_model_id=null,revision=0");
    }
}
