plugins {
    java
    application
    id("com.diffplug.spotless")
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

val picocliVersion = "4.7.6"
val jacksonVersion = "2.19.0"
val junitVersion = "5.11.4"
val mockitoVersion = "5.14.2"
val assertjVersion = "3.27.3"

dependencies {
    // --- CLI framework ---
    implementation("info.picocli:picocli:${picocliVersion}")
    annotationProcessor("info.picocli:picocli-codegen:${picocliVersion}")

    // --- JSON ---
    implementation("com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${jacksonVersion}")

    // --- Testing ---
    testImplementation("org.junit.jupiter:junit-jupiter:${junitVersion}")
    testImplementation("org.mockito:mockito-core:${mockitoVersion}")
    testImplementation("org.mockito:mockito-junit-jupiter:${mockitoVersion}")
    testImplementation("org.assertj:assertj-core:${assertjVersion}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.studydeck.cli.StudyDeckCli")
}

// Generate shell completion script during compile
tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(
        listOf(
            "-Aproject=${project.group}/${project.name}"
        )
    )
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.studydeck.cli.StudyDeckCli"
    }
    // Fat jar — bundle all dependencies
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/LICENSE*", "META-INF/NOTICE*")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

spotless {
    java {
        googleJavaFormat("1.27.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
}
