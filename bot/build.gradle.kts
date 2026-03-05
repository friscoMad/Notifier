import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.ktlint)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    // Slack SDK
    implementation(libs.slack.bolt.jakarta.servlet)
    implementation(libs.slack.bolt.socket.mode)
    implementation(libs.slack.api.model.kotlin.extension)
    implementation(libs.slack.api.client.kotlin.extension)
    implementation(libs.tyrus.standalone.client)
    implementation(libs.javax.websocket.api)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.kotlin)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    systemProperty("spring.profiles.active", "local")
}
