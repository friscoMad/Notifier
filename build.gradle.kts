plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.detekt) apply false
}

allprojects {
    group = "com.notifier.router"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
    }
}

// Custom tasks to manage the local E2E environment
tasks.register<Exec>("startLocalEnv") {
    group = "application"
    description = "Starts the local Novu and PostgreSQL infrastructure via Docker Compose."
    commandLine("docker-compose", "up", "-d")
}

tasks.register<Exec>("stopLocalEnv") {
    group = "application"
    description = "Stops the local Docker Compose infrastructure and removes volumes."
    commandLine("docker-compose", "down", "-v")
}
