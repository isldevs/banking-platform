FROM gradle:8.10.2-jdk21 AS builder
WORKDIR /src
COPY . .
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=builder /src/build/libs/*.jar /app/app.jar
USER appuser
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
