package com.aistock.user.position;
import org.springframework.context.annotation.Configuration;
import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Mapper;
@Configuration(proxyBeanMethods=false)
@MapperScan(basePackageClasses=PositionMapper.class,annotationClass=Mapper.class)
public class PositionPersistenceConfiguration {}
