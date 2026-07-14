# Build stage
# Plain JDK image on purpose: the Maven Wrapper (.mvn/wrapper) pins and
# downloads its own Maven (3.9.9), keeping a single source of truth for the
# Maven version instead of duplicating it in an image tag.
FROM eclipse-temurin:25-jdk-noble AS build

WORKDIR /app

# Copy pom.xml and download dependencies (cached layer while pom.xml is unchanged)
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:25-jre-noble

# curl is required by the container healthcheck (docker-compose.yml)
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Safe default for standalone `docker run` (overridable): the docker profile
# requires real DB credentials instead of silently using dev defaults.
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75", \
  "-XX:+UseG1GC", \
  "-jar", "app.jar"]
