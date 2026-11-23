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

# Note: local compose mounts host JWT PEMs. Host private.pem is mode 600,
# so a non-root container user cannot read it without chmod/ownership changes.
# Non-root hardening can be added later with proper secret mounts.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
