rootProject.name = "simple-tool"

pluginManagement {
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
    }
}
include("main")