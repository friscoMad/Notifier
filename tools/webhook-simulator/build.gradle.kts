plugins {
    id("application")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.spring.boot.starter.core)
    implementation(libs.spring.shell.starter)
    implementation("org.springframework:spring-web")
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson2.module.kotlin)
    detektPlugins(libs.detekt.formatting)
}

application {
    mainClass.set("com.notifier.router.tools.WebhookSimulatorKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaExec> {
    standardInput = System.`in`
}
