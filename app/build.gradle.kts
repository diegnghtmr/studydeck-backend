plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
    jacoco
}

group = "com.studydeck"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile> {
    options.release.set(25)
    options.compilerArgs.add("-Xlint:all")
}

repositories {
    mavenCentral()
}

// Version catalogue — kept explicit for traceability (TRD matrix)
val archunitVersion = "1.4.1"
val testcontainersVersion = "1.21.0"
val jacocoVersion = "0.8.15"
val jsonSchemaValidatorVersion = "3.0.4"
val springAiVersion = "2.0.0"

dependencyManagement {
    imports {
        // Spring AI BOM for Boot 4.x — manages all spring-ai-* artifact versions.
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

dependencies {
    // --- Web + REST ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // --- Persistence ---
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // --- Migrations ---
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // --- Spring AI: local ONNX embeddings (no external service required) ---
    // TransformersEmbeddingModel uses all-MiniLM-L6-v2 (384 dims) by default.
    implementation("org.springframework.ai:spring-ai-starter-model-transformers")

    // --- Spring AI: pgvector VectorStore (semantic search) ---
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")

    // --- Spring AI: chat (optional — graceful no-provider behavior) ---
    // ChatModel is optional: if no api-key/base-url is configured the app still boots.
    // Ollama (local) and OpenAI are supported via env; others can be added later.
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // --- Observability ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Prometheus scrape endpoint at /actuator/prometheus (enabled via management exposure).
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // --- JSON Schema validation (Draft 2020-12 via networknt) ---
    implementation("com.networknt:json-schema-validator:${jsonSchemaValidatorVersion}")

    // --- Security (OAuth2 Resource Server + JWT) ---
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")

    // --- Testing ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Testcontainers BOM
    testImplementation(platform("org.testcontainers:testcontainers-bom:${testcontainersVersion}"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    // ArchUnit for hexagonal boundary enforcement
    testImplementation("com.tngtech.archunit:archunit-junit5:${archunitVersion}")
}

// Spotless — google-java-format 1.27.0 per TRD
spotless {
    java {
        googleJavaFormat("1.27.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// JaCoCo — version pinned to TRD matrix
jacoco {
    toolVersion = jacocoVersion
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        // P7 hardening: real coverage gates. Current actuals are ~67% instruction / ~54% branch;
        // gates are set below those with headroom so normal fluctuation does not break CI while
        // still preventing meaningful regressions.
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.45".toBigDecimal()
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    // Allow Testcontainers to reach Docker socket
    jvmArgs("-Djava.security.egd=file:/dev/./urandom")
    // Each @SpringBootTest caches a full Spring context (JPA + Spring AI + Flyway). With ~16
    // Testcontainers-backed test classes these accumulate in the context cache and can exhaust the
    // default heap (OutOfMemoryError during context init). Give the test JVM headroom and bound the
    // Spring context cache so memory stays predictable as more integration tests are added.
    maxHeapSize = "3g"
    systemProperty("spring.test.context.cache.maxSize", "12")
}

tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}
