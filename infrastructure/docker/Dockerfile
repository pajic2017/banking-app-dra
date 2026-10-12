FROM eclipse-temurin:17-jre-alpine@sha256:3c472129dc75a8d1d7a3f2df5b2093a8077e4493deff046754d4764d0371de63
WORKDIR /app
RUN mkdir -p /var/log/bank && chown 10001:10001 /var/log/bank
ARG SERVICE
COPY --chmod=0444 ${SERVICE}/build/libs/app.jar build/docker-probe/HealthCheck.class /app/
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
