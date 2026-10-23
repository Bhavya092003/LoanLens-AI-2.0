FROM maven:3.9.13-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG SKIP_TESTS=true
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests=${SKIP_TESTS} package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 --create-home appuser \
    && mkdir -p /var/lib/lending/policies /var/lib/lending/applications \
    && chown -R appuser:appuser /var/lib/lending
COPY --from=build --chown=appuser:appuser /workspace/target/digital-lending-platform-0.1.0-SNAPSHOT.jar /app/app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
