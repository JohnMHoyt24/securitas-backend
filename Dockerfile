# Build stage — compiles the jar. Run on the target architecture (ARM on the Oracle
# Cloud VM) to avoid cross-compilation; eclipse-temurin images are multi-arch so this
# Dockerfile is portable regardless.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Runtime stage — slim JRE, no build tooling.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/securitas-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
