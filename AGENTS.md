# Notification Router - Agent Instructions

Welcome to the Notification Router project! This document outlines the project's setup, architectural guidelines, and tooling to help AI agents seamlessly navigate and modify the codebase.

## Project Structure

This is a multi-module Kotlin Spring Boot application built with Gradle.
- `api/`: The core module handling the main web server, business logic, endpoints and REST functionality.
- `bot/`: The module dedicated to integrating with Slack SDK and handling incoming/outgoing bot operations.
- Uses **PostgreSQL** as the primary database in production and relies on Spring Data JPA for data access.

## Best Practices & Tooling

To maintain clean architecture and code quality, the project relies on the following tools configured directly in `build.gradle.kts`:

### 1. Kotlin Linting & Style (Ktlint)
We use `ktlint` (via `org.jlleitschuh.gradle.ktlint` plugin) to enforce standard Kotlin style guidelines. 
- **Check formatting:** `./gradlew ktlintCheck`
- **Auto-format code:** `./gradlew ktlintFormat`
Always run `./gradlew ktlintFormat` after creating or significantly modifying a `.kt` file to ensure styling compliance. Check for build failures as ktlint strictness can break the build for improper syntax styling.

### 2. Testing
Testing leverages standard JUnit 5, Spring Boot Test, and Mockito-Kotlin.
- **Run all tests:** `./gradlew test`
- All unit tests should utilize `org.mockito.kotlin.*` whenever using mocks. Never use Java's `any(Class::class.java)` as it lacks null-safety compatibility with Kotlin. Do test executions via Gradle to verify implementations work correctly.

## Agent Workflows
1. **Evaluate Tasks:** Review file structures using `list_dir` and read logic using `view_file` or `view_code_item`.
2. **Execute Tests:** Utilize Gradle tasks to verify fixes (`test`) and styling (`ktlintCheck`).
3. **Format Changes:** Actively run `./gradlew ktlintFormat` across the codebase after writing Kotlin files so the user enters a clean IDE state. 
4. **Spring Ecosystem:** Try utilizing constructor injection, `@Service`, `@RestController` and standard JPA repository functionalities where possible. Wait for the compilation step to catch any missing definitions or ambiguous mappings between Data Transfer Objects (DTO) and Entities.
