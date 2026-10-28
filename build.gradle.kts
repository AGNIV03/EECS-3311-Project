plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "libraryagent"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

javafx {
    version = "21.0.13"
    modules = listOf("javafx.controls")
}

dependencies {
    implementation("com.anthropic:anthropic-java:3.2.0")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.4")
    // The SDK logs through SLF4J; a no-op binding keeps its startup warnings out of the CLI.
    runtimeOnly("org.slf4j:slf4j-nop:2.0.20")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    // GUI entry point; the CLI has its own task below.
    mainClass = "libraryagent.ui.gui.LibraryApp"
}

tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "Runs the command-line interface."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "libraryagent.ui.cli.CliApp"
    standardInput = System.`in`
    workingDir = rootDir
}

tasks.register<JavaExec>("runWeb") {
    group = "application"
    description = "Runs the web version (JSON API + browser interface)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "libraryagent.ui.web.WebServer"
    workingDir = rootDir
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

tasks.test {
    // Tests tagged "live" call the real Anthropic API (and cost money); run them with `gradlew liveTest`.
    useJUnitPlatform {
        excludeTags("live")
    }
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:unchecked")
}

tasks.register<Test>("liveTest") {
    group = "verification"
    description = "Runs tests that call the real Anthropic API (needs ANTHROPIC_API_KEY in .env)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = rootDir
    useJUnitPlatform {
        includeTags("live")
    }
    testLogging {
        events("passed", "failed")
        showStandardStreams = true
    }
}
