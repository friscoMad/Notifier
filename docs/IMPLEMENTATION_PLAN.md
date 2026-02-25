# Implementation Plan

Based on the architectural documents, API specifications, and the existing codebase cleanup, the project is structured correctly into a multi-module Kotlin Spring Boot architecture. Phase 1 established the DB models (PostgreSQL) and the core API structure (`api/` module). Below is the comprehensive execution plan required to finalize the Notification Router system.

## 1. Engine & Routing Implementation (`api` module)

Currently, `EventController`, `WebhookController`, `BuildkiteWebhookAdapter`, `GitHubWebhookAdapter`, and `GitHubActionsWebhookAdapter` all capture payload data correctly. However, the logic stops at `// In a real implementation, you would: 1. Get all subscriptions...`. 

**Tasks Needed:**
- **Create an `EventService`:** This component will act as the orchestrator. It should:
  1. Retrieve the parsed `Event` from the controllers.
  2. Look up the `NotificationType` corresponding to the event's `typeKey`.
  3. Query `SubscriptionRepository` and `ChannelSubscriptionRepository` for active listeners corresponding to that specific `NotificationTypeId`.
- **Integrate `FilterEvaluator`:** Use the `FilterEvaluator` inside `EventService` to iterate through users/channels and determine if the event's exact metadata triggers their threshold alerts (i.e. if `filter.operator` and `filter.field` pass).
- **Consolidate Novu Dispatching:** Build a `NovuService` leveraging the `co.novu:novu-java:1.6.0` dependency. Once filtered, batch users up and execute Novu workflow triggers (`TriggerEventRequest`) so notifications deploy to Slack or web-inboxes.

## 2. Slack Bot Application (`bot` module)

The bot module requires full integration with `slack-api-client` and `bolt` packages natively found in `bot.build.gradle.kts`.

**Tasks Needed:**
- **Create a Configuration Bean for Bolt:** Initialize a `App` instance passing the `SLACK_BOT_TOKEN` and `SLACK_SIGNING_SECRET`. Provide it as a Bean so it listens dynamically.
- **Implement Slash Commands (`/notifyme`):** 
  - Subscribing (`subscribe`, `unsubscribe`, `help`, `list`).
  - Configure the bot to make API calls to the local `api` module (or directly to the DB) when configuring preferences.
- **Implement Interactive Modals (View Submissions):** Provide visual feedback for managing complex logic across Webhooks + notification-types. Expose these to Slack.
- **Support Channel Routing:** Handle `/notifyme channel <type> #channel --digest` logic appropriately inside the `command` functions.

## 3. Webhook Security Verification

Validating incoming hashes via a specific `Interceptor` or `Filter` inside `api` module.

**Tasks Needed:**
- Add `HmacUtils` validations or standard Spring Interceptors to confirm `X-Hub-Signature-256` payload hashes from GitHub.
- Add validations around Buildkite authorization parameters to prevent unauthorized payloads triggering downstream Novu deployments.

## 4. Polishing and Automated Testing Integration

Ensure End-To-End verification logic:
- Expand Controller unit-tests in `api/src/test` using Mockito-Kotlin to actively assess the logic implemented in `EventService`.
- Create a test `NovuProvider` mock or switch the test to hit a wiremock server mimicking Novu to trace trigger dispatches correctly.
