package com.aistock.prediction.modelcatalog;
/** Reuses existing guarded DB migration/cleanup without exposing production credentials. */
public final class StatisticsPredictionFixture {public static org.springframework.jdbc.core.JdbcTemplate fresh(){return new ModelDatabaseFixture().jdbc;}}
