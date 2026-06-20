# Multi-stage build: compile with JDK 26, run on JRE 21 slim.
# We build with JDK 26 (--release 25) to produce Java 25 bytecode.

# ---- BUILD STAGE ----
FROM eclipse-temurin:26-jdk-noble AS build

WORKDIR /workspace

# Copy Gradle wrapper files first (layer caching)
COPY gradle gradle
COPY gradlew .
COPY settings.gradle.kts .
COPY build.gradle.kts .

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon --quiet 2>/dev/null || true

# Copy source and build
COPY src src
COPY openapi.yaml .

RUN ./gradlew bootJar --no-daemon -x test -x spotlessCheck && \
    java -Djarmode=tools -jar build/libs/*.jar extract --layers --launcher --destination build/extracted

# ---- RUN STAGE ----
FROM eclipse-temurin:26-jre-noble AS runtime

# Non-root user for security
RUN groupadd --system studydeck && useradd --system --gid studydeck studydeck

WORKDIR /app

# Copy extracted layered jar (Spring Boot layered jars improve Docker layer caching)
COPY --from=build --chown=studydeck:studydeck /workspace/build/extracted/dependencies/ ./
COPY --from=build --chown=studydeck:studydeck /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=studydeck:studydeck /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=studydeck:studydeck /workspace/build/extracted/application/ ./

USER studydeck

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "org.springframework.boot.loader.launch.JarLauncher"]
