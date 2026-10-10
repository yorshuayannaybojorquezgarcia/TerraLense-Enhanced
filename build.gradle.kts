// TerraLense - a server-side minimap for Paper.
//
// Compiles against paper-api only. No paperweight/NMS, so no remapping is needed
// and the jar stays portable across Paper builds.

plugins {
    java
    `java-library`
}

group = "dev.terralense"
version = "2.0.0-dev"

val paperApiVersion = project.property("paperApiVersion") as String
val minecraftApiVersion = project.property("apiVersion") as String
val sqliteVersion = project.property("sqliteVersion") as String

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    // SQLite is declared in plugin.yml's `libraries` block, so Paper puts it on the
    // plugin classpath at load time. compileOnly here, not shaded.
    compileOnly("org.xerial:sqlite-jdbc:$sqliteVersion")
    testImplementation("org.xerial:sqlite-jdbc:$sqliteVersion")

    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-serial,-processing"))
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addStringOption("Xdoclint:none", "-quiet")
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.processResources {
    val tokens = mapOf(
        "version" to project.version.toString(),
        "apiVersion" to minecraftApiVersion,
        "sqliteVersion" to sqliteVersion,
    )
    inputs.properties(tokens)
    filesMatching("plugin.yml") {
        expand(tokens)
    }
}

// Prints the resolved paper-api jar, handy when checking the API surface locally.
tasks.register("printPaperJar") {
    val jars = configurations.compileClasspath.map { cp -> cp.files.map { it.absolutePath } }
    doLast {
        jars.get().filter { it.contains("paper-api") }.forEach { println("PAPER_JAR=$it") }
    }
}

// Local test deploy. Not wired into `build`; run it manually.
val testServerPlugins = rootProject.layout.projectDirectory.dir("../../test-sevrer/paper/plugins")

tasks.register<Copy>("deployToTestServer") {
    group = "distribution"
    description = "Copies the built jar into the local test server's plugins directory."
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    into(testServerPlugins)

    doLast {
        println("Deployed ${tasks.jar.get().archiveFileName.get()} to ${testServerPlugins.asFile}")
    }
}
