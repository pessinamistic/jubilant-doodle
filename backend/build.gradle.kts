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

// ── jpackage — native installer (DMG on macOS, EXE on Windows) ───────────────
// Usage:
//   macOS:   ./gradlew jpackageInstaller -Pjpackage.type=dmg
//   Windows: ./gradlew jpackageInstaller -Pjpackage.type=exe
//
// Uses ProcessBuilder directly — Project.exec{} was removed in Gradle 9.
// Output lands in build/dist/.

val jpackageType: String = findProperty("jpackage.type")?.toString() ?: "dmg"

tasks.register("jpackageInstaller") {
    dependsOn(tasks.named("bootJar"))
    doLast {
        val distDir = layout.buildDirectory.dir("dist").get().asFile
        distDir.mkdirs()

        val jarsDir = layout.buildDirectory.dir("libs").get().asFile
        val mainJar = jarsDir.listFiles()
            ?.firstOrNull { it.name.endsWith(".jar") && !it.name.endsWith("-plain.jar") }
            ?: error("bootJar output not found in ${jarsDir.absolutePath}")

        // Prefer JAVA_HOME env (set by actions/setup-java) over the JRE that
        // Gradle bootstrapped itself with — that one may not have jpackage.
        val javaHome    = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
        val isWindows   = System.getProperty("os.name", "").lowercase().contains("win")
        val jpackageBin = "$javaHome/bin/jpackage${if (isWindows) ".exe" else ""}"
        val appVersion  = project.version.toString().replace("-SNAPSHOT", "")

        val cmd = mutableListOf(
            jpackageBin,
            "--type",         jpackageType,
            "--name",         "Port Wrangler",
            "--vendor",       "dbdeployer",
            "--app-version",  appVersion,
            "--description",  "Manage and deploy local database instances",
            "--input",        jarsDir.absolutePath,
            "--main-jar",     mainJar.name,
            // --main-class omitted: jpackage reads Main-Class from MANIFEST.MF
            "--dest",         distDir.absolutePath,
            "--java-options", "-Xmx256m",
        )

        when (jpackageType) {
            "dmg" -> cmd += listOf("--mac-package-identifier", "com.dbdeployer.portwrangler")
            "exe" -> cmd += listOf("--win-dir-chooser", "--win-menu", "--win-shortcut")
        }

        logger.lifecycle("jpackageInstaller: {}", cmd.joinToString(" "))

        val process = ProcessBuilder(cmd)
            .inheritIO()
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) error("jpackage failed with exit code $exitCode")
    }
}
