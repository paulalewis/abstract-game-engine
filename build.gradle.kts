plugins {
    kotlin("jvm") version "2.4.21"
}

group = "com.castlefrog.agl"
version = "1.2.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    useJUnit()
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to "Abstract Game Engine",
                "Specification-Version" to archiveVersion,
                "Implementation-Title" to "com.castlefrog.agl",
                "Implementation-Version" to archiveVersion
            )
        )
    }
}

// Random-playout benchmark: its own source set, so it stays out of the library
// JAR and out of `./gradlew test`. Run it with `./gradlew benchmark`.
val benchmark: SourceSet = sourceSets.create("benchmark") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

tasks.register<JavaExec>("benchmark") {
    description = "Plays random games in every domain and reports throughput and the time split."
    group = "verification"
    classpath = benchmark.runtimeClasspath
    mainClass.set("com.castlefrog.agl.benchmark.PlayoutBenchmarkKt")
}

// Compile the benchmark in `build` so it keeps up with API changes; it is never run there.
tasks.check {
    dependsOn(benchmark.classesTaskName)
}
