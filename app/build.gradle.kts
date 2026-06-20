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
val jsonSchemaValidatorVersion = "1.5.4"

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

    // --- Observability ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")

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
        rule {
            limit {
                // Baseline threshold for P0 — grows per phase
                minimum = "0.10".toBigDecimal()
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    // Allow Testcontainers to reach Docker socket
    jvmArgs("-Djava.security.egd=file:/dev/./urandom")
}

tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}
