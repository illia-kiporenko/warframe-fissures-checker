# Use an OpenJDK 24 image as base (replace with actual tag if available)
FROM openjdk:24-jdk-slim

WORKDIR /app

COPY target/long-polling-fissures.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
