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
    compileOnly("io.papermc.paper:paper-api:26.2.build.130-stable")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
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
        rootProject.layout.buildDirectory.file("MrCrayfishVehiclePlugin-resource-pack.zip").get().asFile.absolutePath
    )
}
