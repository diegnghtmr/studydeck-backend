// Root build — shared plugin versions only.
// Per-module logic lives in app/build.gradle.kts and cli/build.gradle.kts.
plugins {
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.diffplug.spotless") version "7.0.4" apply false
}

group = "com.studydeck"
version = "0.1.0-SNAPSHOT"
