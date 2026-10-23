# The JAR is built earlier in the pipeline and downloaded into target/ before this build runs
FROM eclipse-temurin:17-jre-alpine

# Run as a non-root user
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY target/library-api.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
