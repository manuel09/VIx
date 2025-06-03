plugins {
    kotlin("jvm") version "1.8.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    // altre dipendenze
}

tasks {
    shadowJar {
        archiveBaseName.set("VixSrcExtension")
        archiveVersion.set("1.0")
        archiveClassifier.set("")
    }
}

