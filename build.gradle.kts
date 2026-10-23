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
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.4")

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

tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:unchecked")
}
