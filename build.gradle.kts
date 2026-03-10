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

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
    }

    plugins.withId("io.gitlab.arturbosch.detekt") {
        configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.files("detekt.yml"))
        }

        tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
            autoCorrect = true
            jvmTarget = "21"
            reports {
                html.required.set(true)
                xml.required.set(false)
                sarif.required.set(false)
                txt.required.set(false)
            }
        }
        configurations.getByName("detekt") {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin") {
                    useVersion("2.0.21") // Add the version of Kotlin that detekt needs
                }
            }
        }
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
