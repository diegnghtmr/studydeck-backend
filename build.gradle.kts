plugins {
    java
    id("org.springframework.boot") version "4.0.7"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.4"
    jacoco
}

group = "com.studydeck"
version = "0.1.0-SNAPSHOT"

// Use installed JDK 26 to compile with --release 25.
// No toolchain download triggered — we reference the running JVM directly.
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

dependencies {
    // --- Web + REST ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // --- Persistence ---
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // --- Migrations ---
    // Flyway 10+ split database support into separate modules.
    // flyway-database-postgresql is required for PostgreSQL in Flyway 11.x.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // --- Observability ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- OpenAPI / Swagger UI (dev only - conditional on profile) ---
    // springdoc-openapi-starter-webmvc-ui is not yet released for Boot 4.
    // In dev profile, we serve the static openapi.yaml directly via Spring MVC.
    // Swagger UI can be served later once springdoc releases a Boot 4 compatible version.

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
