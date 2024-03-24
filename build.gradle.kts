buildscript {
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.1.0")
    }

    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
    }
}