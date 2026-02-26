import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.ktlint)
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.flyway.core)

    // PostgreSQL
    runtimeOnly(libs.postgresql)

    // Novu SDK
    implementation(libs.novu.java)

    // Test
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.wiremock.standalone)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.h2)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    // Optimize test execution by running tests in parallel forks
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1

    // JVM arguments to speed up test execution
    jvmArgs(
        "-XX:+TieredCompilation",
        "-XX:TieredStopAtLevel=1",
        "-Dspring.main.lazy-initialization=true",
    )
}

ktlint {
    version.set("1.8.0")
    verbose.set(true)
    outputToConsole.set(true)
    filter {
        exclude("**/build/**")
    }
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    systemProperty("spring.profiles.active", "local")
}
