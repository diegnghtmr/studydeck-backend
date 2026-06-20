# Multi-stage build: compile with JDK 26, run on JRE 21 slim.
# We build with JDK 26 (--release 25) to produce Java 25 bytecode.
# Source now lives in :app subproject (multi-module layout).

# ---- BUILD STAGE ----
FROM eclipse-temurin:26-jdk-noble AS build

WORKDIR /workspace

# Pre-download the ONNX embedding model (the build stage has network) so the RUNTIME
# image boots fully OFFLINE — no HuggingFace dependency at startup. Cached as its own layer.
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /workspace/models \
    && curl -fSL --retry 3 --retry-delay 2 -o /workspace/models/model.onnx \
       https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx \
    && curl -fSL --retry 3 --retry-delay 2 -o /workspace/models/tokenizer.json \
       https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json

# Copy Gradle wrapper files first (layer caching)
COPY gradle gradle
COPY gradlew .
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY app/build.gradle.kts app/
COPY cli/build.gradle.kts cli/

# Bootstrap the Gradle distribution first, with retries (the wrapper download can
# hit transient redirect/network flakes inside the build sandbox).
RUN ./gradlew --version --no-daemon \
    || ./gradlew --version --no-daemon \
    || ./gradlew --version --no-daemon

# Download dependencies (cached layer)
RUN ./gradlew :app:dependencies --no-daemon --quiet 2>/dev/null || true

# Copy app source and build
COPY app/src app/src
COPY openapi.yaml .

RUN ./gradlew :app:bootJar --no-daemon -x test -x spotlessCheck && \
    java -Djarmode=tools -jar app/build/libs/*.jar extract --layers --launcher --destination app/build/extracted

# ---- RUN STAGE ----
FROM eclipse-temurin:26-jre-noble AS runtime

# curl is required by the container HEALTHCHECK (jre-noble does not ship it).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Non-root user for security
RUN groupadd --system studydeck && useradd --system --gid studydeck studydeck

WORKDIR /app

# Copy extracted layered jar (Spring Boot layered jars improve Docker layer caching)
COPY --from=build --chown=studydeck:studydeck /workspace/app/build/extracted/dependencies/ ./
COPY --from=build --chown=studydeck:studydeck /workspace/app/build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=studydeck:studydeck /workspace/app/build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=studydeck:studydeck /workspace/app/build/extracted/application/ ./

# Baked ONNX embedding model — app reads these local files instead of downloading at boot.
COPY --from=build --chown=studydeck:studydeck /workspace/models /app/models
ENV SPRING_AI_EMBEDDING_TRANSFORMER_ONNX_MODEL_URI=file:/app/models/model.onnx \
    SPRING_AI_EMBEDDING_TRANSFORMER_TOKENIZER_URI=file:/app/models/tokenizer.json

USER studydeck

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "org.springframework.boot.loader.launch.JarLauncher"]
