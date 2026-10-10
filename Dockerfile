FROM maven:3.9.11-eclipse-temurin-21 AS build

ARG MODULE
WORKDIR /workspace
COPY . .
RUN mvn -B -pl "${MODULE}" -am -DskipTests package \
    && cp "${MODULE}/target/${MODULE}-1.0.0.jar" /workspace/application.jar

FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10001 --create-home spring
WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/application.jar app.jar
USER spring

EXPOSE 8000 8001 8002
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
