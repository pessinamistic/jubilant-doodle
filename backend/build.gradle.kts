plugins {
    java
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.dbdeployer"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // H2 embedded database (system config store — zero-download, auto-provisions itself)
    runtimeOnly("com.h2database:h2")

    // PostgreSQL driver kept at runtime-only for the one-shot legacy migration
    // (connects to the old dbdeployer-system-db container on first boot if present)
    runtimeOnly("org.postgresql:postgresql:42.7.4")

    // Docker Java SDK (zerodep transport has native Unix socket support on macOS/Linux/Windows)
    implementation("com.github.docker-java:docker-java-core:3.4.1")
    implementation("com.github.docker-java:docker-java-transport-zerodep:3.4.1")

    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Utilities
    implementation("org.apache.commons:commons-lang3:3.17.0")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
