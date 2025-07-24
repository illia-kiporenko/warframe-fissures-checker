# Use an OpenJDK 24 image as base (replace with actual tag if available)
FROM openjdk:17-oracle

WORKDIR /app

COPY target/long-polling-fissures.jar app.jar

EXPOSE 5050

ENTRYPOINT ["java", "-jar", "app.jar"]
