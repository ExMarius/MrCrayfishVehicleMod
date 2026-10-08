plugins {
    java
}

group = "com.mrcrayfish"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val properties = mapOf("version" to project.version)
    inputs.properties(properties)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(properties)
    }
    from(rootProject.projectDir.parentFile.resolve("MOD-LICENSE.txt")) {
        rename { "LICENSE.txt" }
    }
}

tasks.jar {
    archiveBaseName.set("MrCrayfishVehiclePlugin")
}

tasks.register<Exec>("resourcePack") {
    group = "build"
    description = "Builds the required vanilla-client resource pack."
    workingDir(rootProject.projectDir.parentFile)
    commandLine(
        "python3",
        "tools/build_resource_pack.py",
        "--output",
        rootProject.layout.buildDirectory.file("MrCrayfishVehiclePlugin-resource-pack-1.21.4-r25.zip").get().asFile.absolutePath
    )
}
