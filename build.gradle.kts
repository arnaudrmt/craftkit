plugins {
    id("java")
}

group = "fr.arnaud"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()

    maven { url = uri("https://repo.dmulloy2.net/repository/public/") }
}

dependencies {

    compileOnly("org.spigotmc:spigot:1.8.8-R0.1-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}


tasks.register("buildAndCopy") {
    dependsOn("build", "copyToServer")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar>() {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().resources)
}

tasks.register<Copy>("copyToServer") {

    dependsOn(tasks.jar)

    val serverPluginFolder = file("/Users/arnaud/Desktop/Documents/Developpment/Projects/Plugins/Test Servers/1.8.8/plugins");

    from(tasks.jar.get().archiveFile)
    into(serverPluginFolder)
}