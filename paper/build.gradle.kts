plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.0"
    id("maven-publish")
}

group = "re.imc"
version = "1.0.9"

publishing {
    publications {
        create<MavenPublication>("shadow") {
            groupId = project.group.toString()
            artifactId = "GeyserModelEngine"
            version = project.version.toString()

            artifact(tasks.shadowJar.get().archiveFile) {
                builtBy(tasks.shadowJar)
            }
        }
    }

    repositories {
        mavenLocal()
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://central.sonatype.com/repository/maven-snapshots/")

    maven("https://mvn.lumine.io/repository/maven-public/")

    maven("https://repo.opencollab.dev/main/")

    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    compileOnly("com.ticxo.modelengine:ModelEngine:R4.1.0")
    compileOnly("io.github.toxicity188:bettermodel-api:3.5.0")
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:3.5.0")
    
    compileOnly(files("libs/geyserutils-spigot-1.0-SNAPSHOT.jar"))
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")

    // The server's own packetevents plugin, never a copy of our own: a bundled one falls behind the
    // Minecraft version and stops the plugin from loading.
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")

    implementation("org.reflections:reflections:0.10.2")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.compileJava {
    options.encoding = "UTF-8"
}

tasks.shadowJar {
    archiveFileName.set("${rootProject.name}-${version}.jar")


    relocate("org.bstats", "re.imc.geysermodelengine.libs.bstats")

    relocate("org.reflections", "re.imc.geysermodelengine.libs.reflections")
}

tasks.build {
    dependsOn("shadowJar")
    finalizedBy("publishToMavenLocal")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
