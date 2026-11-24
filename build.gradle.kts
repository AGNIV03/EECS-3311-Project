import java.util.zip.ZipFile

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

// One runnable file for the CLI: java -jar build/libs/library-agent-cli.jar --input task.txt
// The JavaFX libraries are left out, because the CLI does not use them.
val cliLibraries = configurations.runtimeClasspath.map { classpath ->
    classpath.filter { it.name.endsWith(".jar") && !it.name.startsWith("javafx") }
}

// Libraries register their plug-ins in META-INF/services files of the same name, so those files
// are joined here; copying only the first one would drop the others' entries.
val mergeCliServices = tasks.register("mergeCliServices") {
    val target = layout.buildDirectory.dir("cli-services")
    inputs.files(cliLibraries)
    outputs.dir(target)
    doLast {
        val merged = sortedMapOf<String, MutableSet<String>>()
        cliLibraries.get().forEach { library ->
            ZipFile(library).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("META-INF/services/") }
                    .forEach { entry ->
                        val lines = zip.getInputStream(entry).bufferedReader().readLines()
                            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                        merged.getOrPut(entry.name.removePrefix("META-INF/services/")) { linkedSetOf() }.addAll(lines)
                    }
            }
        }
        val folder = target.get().asFile
        folder.deleteRecursively()
        folder.mkdirs()
        merged.forEach { (name, lines) -> File(folder, name).writeText(lines.joinToString("\n") + "\n") }
    }
}

tasks.register<Jar>("cliJar") {
    group = "application"
    description = "Builds the standalone CLI as one runnable jar."
    archiveFileName = "library-agent-cli.jar"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to "libraryagent.ui.cli.CliApp", "Multi-Release" to "true")
    }
    from(sourceSets["main"].output)
    from(mergeCliServices) { into("META-INF/services") }
    from(cliLibraries.map { libraries -> libraries.map { zipTree(it) } }) {
        exclude("META-INF/services/**", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA",
            "META-INF/MANIFEST.MF", "module-info.class", "META-INF/versions/*/module-info.class")
    }
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

// Drives the real GUI through every feature for the demonstration video (calls the live model).
// Usage: gradlew demoGui --args="<path to ffmpeg.exe, or - for no recording> <output folder>"
tasks.register<JavaExec>("demoGui") {
    group = "application"
    description = "Runs the scripted GUI walkthrough used to record the demonstration video."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "libraryagent.ui.gui.DemoWalkthrough"
    workingDir = rootDir
    environment("DB_PATH", "data/demo-gui.db")
}
