FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw --batch-mode verify

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 medagentguard
WORKDIR /app
COPY --from=build /workspace/target/medagent-guard-0.1.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
