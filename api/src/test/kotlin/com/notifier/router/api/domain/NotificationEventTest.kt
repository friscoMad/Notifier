package com.notifier.router.api.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NotificationEventTest {

    // ── GitHub PR Events ──────────────────────────────────────────────────────

    @Nested
    inner class PrCreatedEventTest {
        private val event = PrCreatedEvent(
            author = "alice",
            repo = "org/repo",
            baseBranch = "main",
            title = "Fix login bug",
            description = "Fixes #123",
            url = "https://github.com/org/repo/pull/1",
            createdAt = "2024-01-01T00:00:00Z",
        )

        @Test fun `typeKey is pr_created`() = assertEquals("pr_created", event.typeKey)

        @Test fun `content contains emoji and bold title`() {
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("🔔 *PR Opened*"), "content=$content")
        }

        @Test fun `content contains clickable link`() {
            val content = event.payload["content"] as String
            assertTrue(content.contains("<https://github.com/org/repo/pull/1|Fix login bug>"), "content=$content")
        }

        @Test fun `content contains repo, branch and author`() {
            val content = event.payload["content"] as String
            assertTrue(content.contains("org/repo"), "content=$content")
            assertTrue(content.contains("main"), "content=$content")
            assertTrue(content.contains("alice"), "content=$content")
        }

        @Test fun `metadata contains author, repo and base_branch`() {
            assertEquals(mapOf("author" to "alice", "repo" to "org/repo", "base_branch" to "main"), event.metadata)
        }
    }

    @Nested
    inner class PrReviewRequestedEventTest {
        private val event = PrReviewRequestedEvent(
            author = "alice",
            repo = "org/repo",
            reviewer = "bob",
            baseBranch = "main",
            title = "Fix login bug",
            url = "https://github.com/org/repo/pull/1",
            requestedAt = "2024-01-01T00:00:00Z",
        )

        @Test fun `typeKey is pr_review_requested`() = assertEquals("pr_review_requested", event.typeKey)

        @Test fun `content contains reviewer icon and reviewer name`() {
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("👀 *Review Requested*"), "content=$content")
            assertTrue(content.contains("bob"), "content=$content")
        }

        @Test fun `content contains clickable link`() {
            val content = event.payload["content"] as String
            assertTrue(content.contains("<https://github.com/org/repo/pull/1|Fix login bug>"), "content=$content")
        }
    }

    @Nested
    inner class PrUpdatedEventTest {
        private val event = PrUpdatedEvent(
            author = "alice",
            repo = "org/repo",
            baseBranch = "main",
            title = "Fix login bug",
            url = "https://github.com/org/repo/pull/1",
            updatedAt = "2024-01-01T00:00:00Z",
        )

        @Test fun `typeKey is pr_updated`() = assertEquals("pr_updated", event.typeKey)

        @Test fun `content contains update icon`() {
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("🔄 *PR Updated*"), "content=$content")
        }

        @Test fun `content contains clickable link`() {
            val content = event.payload["content"] as String
            assertTrue(content.contains("<https://github.com/org/repo/pull/1|Fix login bug>"), "content=$content")
        }
    }

    @Nested
    inner class PrClosedEventTest {
        @Test fun `merged PR shows success icon and typeKey pr_merged_master_success`() {
            val event = PrClosedEvent(
                merged = true,
                author = "alice",
                repo = "org/repo",
                baseBranch = "main",
                title = "Fix bug",
                url = "https://github.com/org/repo/pull/1",
                mergedAt = "2024-01-01T00:00:00Z",
                mergedBy = "bob",
            )
            assertEquals("pr_merged_master_success", event.typeKey)
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("✅ *PR Merged*"), "content=$content")
            assertTrue(content.contains("bob"), "content=$content")
        }

        @Test fun `unmerged PR shows failure icon and typeKey pr_merged_master_error`() {
            val event = PrClosedEvent(
                merged = false,
                author = "alice",
                repo = "org/repo",
                baseBranch = "main",
                title = "Fix bug",
                url = "https://github.com/org/repo/pull/1",
                mergedAt = "",
                mergedBy = "",
            )
            assertEquals("pr_merged_master_error", event.typeKey)
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("❌ *PR Closed (unmerged)*"), "content=$content")
            assertTrue(content.contains("alice"), "content=$content")
        }

        @Test fun `content contains clickable link`() {
            val event = PrClosedEvent(
                merged = true,
                author = "a",
                repo = "r",
                baseBranch = "main",
                title = "My PR",
                url = "https://github.com/r/pull/1",
                mergedAt = "",
                mergedBy = "b",
            )
            val content = event.payload["content"] as String
            assertTrue(content.contains("<https://github.com/r/pull/1|My PR>"), "content=$content")
        }
    }

    // ── GitHub Check Events ───────────────────────────────────────────────────

    @Nested
    inner class PrCheckReRequestedEventTest {
        private val event = PrCheckReRequestedEvent(
            author = "alice",
            repo = "org/repo",
            checkName = "ci/build",
            baseBranch = "main",
            checkSuiteUrl = "https://github.com/org/repo/actions/runs/1",
            reRequestedAt = "2024-01-01T00:00:00Z",
        )

        @Test fun `typeKey is pr_checks_failed`() = assertEquals("pr_checks_failed", event.typeKey)

        @Test fun `content contains re-request icon and check name`() {
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("🔁 *Check Re-requested:*"), "content=$content")
            assertTrue(content.contains("ci/build"), "content=$content")
        }

        @Test fun `content contains clickable link to suite`() {
            val content = event.payload["content"] as String
            assertTrue(content.contains("<https://github.com/org/repo/actions/runs/1|View suite>"), "content=$content")
        }
    }

    @Nested
    inner class PrCheckCompletedEventTest {
        @Test fun `success conclusion shows check-passed icon and typeKey`() {
            val event = PrCheckCompletedEvent(
                conclusion = "success",
                author = "alice",
                repo = "org/repo",
                checkName = "ci/build",
                baseBranch = "main",
                checkRunUrl = "https://github.com/org/repo/actions/runs/1",
                completedAt = "2024-01-01T00:00:00Z",
            )
            assertEquals("pr_checks_passed", event.typeKey)
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("✅ *Check Passed:*"), "content=$content")
            assertTrue(content.contains("ci/build"), "content=$content")
        }

        @Test fun `failure conclusion shows check-failed icon and typeKey`() {
            val event = PrCheckCompletedEvent(
                conclusion = "failure",
                author = "alice",
                repo = "org/repo",
                checkName = "ci/build",
                baseBranch = "main",
                checkRunUrl = "https://github.com/org/repo/actions/runs/1",
                completedAt = "2024-01-01T00:00:00Z",
            )
            assertEquals("pr_checks_failed", event.typeKey)
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("❌ *Check Failed:*"), "content=$content")
        }

        @Test fun `content contains clickable link to run`() {
            val event = PrCheckCompletedEvent(
                conclusion = "success",
                author = "a",
                repo = "r",
                checkName = "ci",
                baseBranch = "main",
                checkRunUrl = "https://github.com/r/runs/1",
                completedAt = "",
            )
            val content = event.payload["content"] as String
            assertTrue(content.contains("<https://github.com/r/runs/1|View run>"), "content=$content")
        }
    }

    // ── Deployment Events ─────────────────────────────────────────────────────

    @Nested
    inner class DeployStartedEventTest {
        private val event = DeployStartedEvent(
            service = "api",
            environment = "production",
            author = "alice",
            pipeline = "deploy-prod",
            deploymentUrl = "https://builds.example.com/1",
            status = "running",
            createdAt = "2024-01-01T00:00:00Z",
        )

        @Test fun `typeKey is deploy_started`() = assertEquals("deploy_started", event.typeKey)

        @Test fun `content contains rocket icon with service and environment`() {
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("🚀 *Deploy Started*"), "content=$content")
            assertTrue(content.contains("api"), "content=$content")
            assertTrue(content.contains("production"), "content=$content")
            assertTrue(content.contains("alice"), "content=$content")
        }
    }

    @Nested
    inner class DeployCompletedEventTest {
        @Test fun `success status shows completed icon`() {
            val event = DeployCompletedEvent(
                service = "api",
                environment = "production",
                author = "alice",
                pipeline = "deploy-prod",
                status = "success",
                deploymentUrl = "https://builds.example.com/1",
                statusUrl = "https://builds.example.com/1/status",
                completedAt = "2024-01-01T00:00:00Z",
            )
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("✅ *Deploy Completed*"), "content=$content")
            assertTrue(content.contains("<https://builds.example.com/1|api>"), "content=$content")
        }

        @Test fun `non-success status shows failure icon`() {
            val event = DeployCompletedEvent(
                service = "api",
                environment = "production",
                author = "alice",
                pipeline = "deploy-prod",
                status = "failed",
                deploymentUrl = "https://builds.example.com/1",
                statusUrl = "https://builds.example.com/1/status",
                completedAt = "2024-01-01T00:00:00Z",
            )
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("❌ *Deploy Failed*"), "content=$content")
            assertTrue(content.contains("failed"), "content=$content")
        }
    }

    // ── Buildkite Events ──────────────────────────────────────────────────────

    @Nested
    inner class JobFinishedEventTest {
        @Test fun `passed status shows success icon and typeKey pr_checks_passed`() {
            val event = JobFinishedEvent(
                service = "api",
                testName = "unit-tests",
                team = "backend",
                status = "passed",
                jobUrl = "https://buildkite.com/job/1",
                buildUrl = "https://buildkite.com/build/1",
                finishedAt = "2024-01-01T00:00:00Z",
            )
            assertEquals("pr_checks_passed", event.typeKey)
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("✅ *Build Passed:*"), "content=$content")
            assertTrue(content.contains("unit-tests"), "content=$content")
            assertTrue(content.contains("<https://buildkite.com/build/1|View build>"), "content=$content")
        }

        @Test fun `failed status shows failure icon and typeKey pr_checks_failed`() {
            val event = JobFinishedEvent(
                service = "api",
                testName = "unit-tests",
                team = "backend",
                status = "failed",
                jobUrl = "https://buildkite.com/job/1",
                buildUrl = "https://buildkite.com/build/1",
                finishedAt = "2024-01-01T00:00:00Z",
            )
            assertEquals("pr_checks_failed", event.typeKey)
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("❌ *Build Failed:*"), "content=$content")
        }
    }

    @Nested
    inner class PipelineUpdatedEventTest {
        private val event = PipelineUpdatedEvent(
            service = "api",
            team = "backend",
            pipelineUrl = "https://buildkite.com/pipeline/api",
            updatedAt = "2024-01-01T00:00:00Z",
        )

        @Test fun `typeKey is pipeline_updated`() = assertEquals("pipeline_updated", event.typeKey)

        @Test fun `content contains wrench icon with clickable link`() {
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("🔧 *Pipeline Updated*"), "content=$content")
            assertTrue(content.contains("<https://buildkite.com/pipeline/api|api>"), "content=$content")
            assertTrue(content.contains("backend"), "content=$content")
        }
    }

    @Nested
    inner class BuildFinishedEventTest {
        @Test fun `passed status shows success icon and typeKey deploy_completed`() {
            val event = BuildFinishedEvent(
                service = "api",
                environment = "staging",
                status = "passed",
                buildUrl = "https://buildkite.com/build/1",
                finishedAt = "2024-01-01T00:00:00Z",
            )
            assertEquals("deploy_completed", event.typeKey)
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("✅ *Build Completed*"), "content=$content")
            assertTrue(content.contains("<https://buildkite.com/build/1|api>"), "content=$content")
            assertTrue(content.contains("staging"), "content=$content")
        }

        @Test fun `failed status shows failure icon and typeKey deploy_failed`() {
            val event = BuildFinishedEvent(
                service = "api",
                environment = "staging",
                status = "failed",
                buildUrl = "https://buildkite.com/build/1",
                finishedAt = "2024-01-01T00:00:00Z",
            )
            assertEquals("deploy_failed", event.typeKey)
            val content = event.payload["content"] as String
            assertTrue(content.startsWith("❌ *Build Failed*"), "content=$content")
        }
    }

    // ── Generic / Fallback ────────────────────────────────────────────────────

    @Nested
    inner class GenericEventTest {
        @Test fun `content contains typeKey with bell emoji`() {
            val event = GenericEvent(typeKey = "custom_event")
            val content = event.payload["content"] as String
            assertTrue(content.contains("🔔"), "content=$content")
            assertTrue(content.contains("custom_event"), "content=$content")
        }

        @Test fun `rawPayload is merged into payload`() {
            val event = GenericEvent(typeKey = "custom_event", rawPayload = mapOf("extra" to "data"))
            assertEquals("data", event.payload["extra"])
        }
    }
}
