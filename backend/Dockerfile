FROM eclipse-temurin:17-jre@sha256:34d6aaf0fa4ef553c470234ec23c32c7d5c0f370c2bd4b0bf8a16838052d85f0
ARG MODULE
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-Dcsp.sentinel.log.dir=/tmp/sentinel"
COPY ${MODULE}/target/*-SNAPSHOT.jar /app/app.jar
USER 10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
