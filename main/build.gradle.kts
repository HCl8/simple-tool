import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import proguard.gradle.ProGuardTask

plugins {
    id("org.jetbrains.compose") version "1.6.1"
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.serialization") version "1.9.20"
}

group = "com.hcl"
version = "1.0-SNAPSHOT"

repositories {

    maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/google")

    maven("https://repo.gradle.org/gradle/libs-releases")

    mavenLocal()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    implementation("org.json:json:20220320")

    implementation("org.slf4j:slf4j-api:1.7.35")
    implementation("org.slf4j:slf4j-simple:1.7.35")

    implementation("org.dom4j:dom4j:2.1.4")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.4.0.202211300538-r")

    implementation("de.siegmar:fastcsv:2.2.1")

    implementation(compose.desktop.currentOs)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(listOf("-Dfile.encoding=utf-8"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

configure<SourceSetContainer> {
    named("main") {
        java.srcDir("src/main/kotlin")
    }
}

tasks.register<Jar>("uberJar") {
    archiveClassifier.set("uber")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    setProperty("zip64", true)

    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")

    manifest {
        attributes("Main-Class" to "com.hcl.ProxyServerKt")
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
