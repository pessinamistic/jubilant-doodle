import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.4.2"
    id("com.diffplug.spotless") version "8.0.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.liquibase.gradle") version "2.2.0"
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
    implementation("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ── Spring AI 1.0.3 GA (Phase 3+) ────────────────────────────────────────
    // BOM pins all Spring AI artifacts to the 1.0.x GA line ("GA only" constraint).
    implementation(platform("org.springframework.ai:spring-ai-bom:1.0.3"))
    // Ollama: chat + embeddings in one starter.
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    // JDBC-backed chat memory window (verbatim turns) → SPRING_AI_CHAT_MEMORY table.
    implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
    // pgvector vector store (extension on the existing Postgres 16) — Phase 4 RAG.
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    // Phase 5: expose InfrastructureTools as an MCP server (Cursor / Claude Desktop).
    implementation("org.springframework.ai:spring-ai-starter-mcp-server")

    // PostgreSQL driver — used by the auto-provisioned system DB container (SystemDbProvisioner)
    runtimeOnly("org.postgresql:postgresql")

    // Liquibase — manages schema migrations (replaces ddl-auto: update)
    implementation("org.liquibase:liquibase-core")

    // H2 — TEMPORARY: only needed to run the one-shot H2→Postgres data migrator.
    // Remove this line (and H2DataMigrator.java) once existing data has been migrated.
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
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    // Liquibase Gradle plugin runtime — used by ./gradlew liquibaseChangelogSync and friends.
    // Versions pinned explicitly because this config is outside the Spring Boot BOM.
    liquibaseRuntime("org.liquibase:liquibase-core:4.29.2")
    liquibaseRuntime("org.postgresql:postgresql:42.7.4")
    liquibaseRuntime("info.picocli:picocli:4.7.6")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Liquibase Gradle plugin — used for one-off admin commands against the running system DB.
// Usage (existing installs only, run ONCE before enabling Liquibase for the first time):
//   ./gradlew liquibaseChangelogSync
// Env vars override the defaults so CI and Docker Compose deployments work without editing.
liquibase {
    activities {
        register("main") {
            arguments =
                mapOf(
                    "changelogFile" to
                        "src/main/resources/db/changelog/db.changelog-master.yaml",
                    "url" to
                        (System.getenv("SPRING_DATASOURCE_URL")
                            ?: "jdbc:postgresql://localhost:5499/dbdeployer"),
                    "username" to
                        (System.getenv("SPRING_DATASOURCE_USERNAME") ?: "dbdeployer"),
                    "password" to
                        (System.getenv("SPRING_DATASOURCE_PASSWORD") ?: "dbdeployer_internal"),
                    "driver" to "org.postgresql.Driver",
                )
        }
    }
    runList = "main"
}

val osName = System.getProperty("os.name", "").lowercase()
val isWindows = osName.contains("win")
val isMac = osName.contains("mac")

val frontendDir = layout.projectDirectory.dir("../frontend")
val embeddedStaticDir = layout.projectDirectory.dir("src/main/resources/static")
val frontendStaticOutput = layout.buildDirectory.dir("generated/frontend-static")

//val syncFrontendAssets = if (frontendDir.asFile.exists()) {
//    val frontendPackageJson = frontendDir.file("package.json").asFile
//    val frontendLockFile = frontendDir.file("package-lock.json").asFile
//    val frontendNodeModules = frontendDir.dir("node_modules").asFile
//    val frontendDistDir = frontendDir.dir("dist").asFile
//
//    val npmExecutable = if (isWindows) "npm.cmd" else "npm"
//    val frontendInstallCommand = if (frontendLockFile.exists()) "ci" else "install"
//
//    val frontendInstall = tasks.register<Exec>("frontendInstall") {
//        group = "build"
//        description = "Installs frontend dependencies used for packaging."
//
//        workingDir = frontendDir.asFile
//        executable = npmExecutable
//        args(frontendInstallCommand)
//
//        inputs.file(frontendPackageJson)
//        if (frontendLockFile.exists()) {
//            inputs.file(frontendLockFile)
//        }
//        outputs.dir(frontendNodeModules)
//    }
//
//    val frontendBuild = tasks.register<Exec>("frontendBuild") {
//        group = "build"
//        description = "Builds frontend static assets for the Spring Boot jar."
//
//        dependsOn(frontendInstall)
//        workingDir = frontendDir.asFile
//        executable = npmExecutable
//        args("run", "build")
//
//        inputs.files(
//            fileTree(frontendDir.asFile) {
//                include("src/**")
//                include("public/**")
//                include("index.html")
//                include("vite.config.js")
//                include("package.json")
//                include("package-lock.json")
//            }
//        )
//        outputs.dir(frontendDistDir)
//    }
//
//    tasks.register<Sync>("syncFrontendAssets") {
//        group = "build"
//        description = "Copies frontend dist assets into the backend build directory."
//
//        dependsOn(frontendBuild)
//        from(frontendDistDir)
//        into(frontendStaticOutput)
//    }
//} else {
//    tasks.register<Sync>("syncFrontendAssets") {
//        group = "build"
//        description = "Copies prebuilt static assets when frontend source is not available."
//
//        from(embeddedStaticDir)
//        into(frontendStaticOutput)
//
//        doFirst {
//            if (!embeddedStaticDir.asFile.exists()) {
//                error(
//                    "Frontend source not found at ${frontendDir.asFile}. " +
//                        "Expected prebuilt assets at ${embeddedStaticDir.asFile}."
//                )
//            }
//            logger.lifecycle(
//                "Frontend source not found at {}. Using prebuilt static assets from {}",
//                frontendDir.asFile,
//                embeddedStaticDir.asFile
//            )
//        }
//    }
//}

//tasks.named<BootJar>("bootJar") {
//    dependsOn(syncFrontendAssets)
//    from(syncFrontendAssets) {
//        into("BOOT-INF/classes/static")
//    }
//}

// ── jpackage — native installer (DMG on macOS, EXE on Windows) ───────────────
// Usage:
//   macOS:   ./gradlew jpackageInstaller
//   Windows: .\\gradlew.bat jpackageInstaller
//   Override package type: -Pjpackage.type=dmg|exe|app-image
//
// Uses ProcessBuilder directly — Project.exec{} was removed in Gradle 9.
// Output lands in build/dist/.

val defaultJpackageType = when {
    isWindows -> "exe"
    isMac -> "dmg"
    else -> "app-image"
}
val jpackageType: String = findProperty("jpackage.type")?.toString() ?: defaultJpackageType
val appVersion = version.toString().replace("-SNAPSHOT", "")

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
        val jpackageBin = "$javaHome/bin/jpackage${if (isWindows) ".exe" else ""}"

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

spotless {
    java {
        //eclipse().configFile("spotless.eclipseformat.xml")
        googleJavaFormat("1.25.2")
        //palantirJavaFormat()
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        trimTrailingWhitespace()
        endWithNewline()
    }
    groovyGradle {
        target("*.gradle")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("*.md", ".gitignore", ".github/**/*.yml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
