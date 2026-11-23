# Stage 1: build the Spring Boot JAR
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN chmod +x mvnw \
	&& ./mvnw -B -DskipTests package

# Stage 2: slim runtime image
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar

RUN mkdir -p /app/tmp/uploads /app/keys

# JWT public PEM is mounted at runtime (compose). Non-root hardening can follow
# once key mount ownership is settled (same pattern as AuthBackend).
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
