FROM eclipse-temurin:17-jre

WORKDIR /app

# Produced by './mvnw clean verify' (repackaged executable jar)
COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"] 
