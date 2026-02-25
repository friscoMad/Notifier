---
description: Update project dependencies and tools
---

This workflow guides an agent on how to update the Notifier project dependencies safely.

## 1. Discover Current Versions
Review the following files to determine what versions are currently in use:
- `gradle/libs.versions.toml`: Check all versions under `[versions]`.
- `gradle/wrapper/gradle-wrapper.properties`: Check the Gradle wrapper version.

## 2. Identify Latest Versions
Run web searches or check Maven Central to find the latest stable versions of:
- Spring Boot (`org.springframework.boot`)
- Ktlint Plugin (`org.jlleitschuh.gradle.ktlint`) and Ktlint Core (`com.pinterest.ktlint:ktlint-cli`)
- Mockito Kotlin (`org.mockito.kotlin:mockito-kotlin`)
- Slack Bolt (`com.slack.api:bolt` and `bolt-jetty`)
- Gradle (`gradle-wrapper`)

## 3. Apply Updates
Update the versions in the `[versions]` block of `gradle/libs.versions.toml` and (if applicable) `gradle-wrapper.properties` identified in Step 1.

## 4. Verify Project
// turbo-all
Run the following commands to ensure formatting, compilation and tests pass successfully:
1. Run `./gradlew ktlintFormat`
2. Run `./gradlew clean build`
3. Run `make run-tests`

## 5. Report Results
Review the logs. If there are failures, fix compile or test issues using the new SDK version documentation. Finally, notify the user that the update is complete.
