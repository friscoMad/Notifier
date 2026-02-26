# Notification Router - Antigravity Agent Instructions

A centralized notification management system with self-service subscription via Slack, built as a multi-module Kotlin Spring Boot application.

## 1. Project Overview
This project provides a unified notification router that allows developers to receive notifications from various sources (GitHub, CI/CD systems) through multiple channels (Slack, Email, Web Inbox) with flexible filtering and subscription capabilities.

### Key Features
- **Unified API**: Single entry point for all notification sources.
- **Self-Service**: Users configure their subscriptions via a Slack bot.
- **Flexible Filtering**: Filter notifications by type, repository, author, service, etc.
- **Digest Support**: Group notifications over 24h periods.
- **Multi-Channel**: Slack DM, Slack Channels, Web Inbox.
- **Open Source Core**: Built on Novu for notification delivery.

## 2. Architecture
```text
┌─────────────────────────────────────────────────────────────────┐
│                         INPUT                                   │
├─────────────────┬─────────────────┬─────────────────────────────┤
│ GitHub Webhook  │ GitHub Actions  │ Buildkite Webhook           │
│ (All events)    │ / Deploy        │ / Pipeline events           │
└────────┬────────┴────────┬────────┴─────────────┬───────────────┘
         │                 │                      │
         ▼                 ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│              TRANSFORMATION LAYER (Kotlin/Spring Boot)          │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐     │
│  │ Webhook     │  │ Normalize   │  │ Preference Engine    │     │
│  │ Adapters    │──│ Event       │──│ (Evaluate filters)   │     │
│  └─────────────┘  └─────────────┘  └──────────────────────┘     │
│         │                                    │                  │
│         ▼                                    ▼                  │
│  ┌─────────────────────────────────────────────────────┐        │
│  │            PostgreSQL                               │        │
│  │  users, subscriptions, filters, channel_subs        │        │
│  └─────────────────────────────────────────────────────┘        │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      NOVU (Core)                                │
│  ┌─────────────┐  ┌─────────────┐  ┌───────────────────────┐    │
│  │ Workflows   │  │ Digest      │  │ Providers             │    │
│  │ (per type)  │  │ (24h opt)   │  │ Slack, Inbox          │    │
│  └─────────────┘  └─────────────┘  └───────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## 3. Tech Stack & Project Structure

| Component | Technology |
|-----------|------------|
| API Service | Kotlin + Spring Boot |
| Slack Bot | Kotlin + Slack SDK |
| Database | PostgreSQL |
| Notification Engine | Novu (Helm) |
| Container | k3s / Kubernetes |

### Directory Layout
- `api/`: The core module handling the main web server, business logic, endpoints and REST functionality.
- `bot/`: The module dedicated to integrating with Slack SDK and handling incoming/outgoing bot operations.
- `docs/`: System documentation
  - [`architecture.md`](docs/architecture.md)
  - [`api-specification.md`](docs/api-specification.md)
  - [`database-schema.md`](docs/database-schema.md)
  - [`notification-types.md`](docs/notification-types.md)
  - [`phase1-infrastructure-setup.md`](docs/phase1-infrastructure-setup.md)
  - [`slack-commands.md`](docs/slack-commands.md)
  - [`testing-k3s.md`](docs/testing-k3s.md)
  - [`troubleshooting.md`](docs/troubleshooting.md)
  - [`IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md)
- `k8s/`: Kubernetes manifests.

## 4. Current Implementation Status

**Implemented:**
- **Infrastructure:** K3s, PostgreSQL, Novu Helm.
- **Core Models:** User management, subscriptions, preferences in DB. All domain classes have JPA annotations.
- **API Structure:** Foundational Spring Boot setup across `api` and `bot` modules using Gradle/Ktlint.
- **Webhook Adapters:** Controllers for GitHub, GitHub Actions, Buildkite are actively capturing payloads.
- **Engine & Routing (`api` module):** `EventService` + `FilterEvaluator` connect incoming payloads to active subscriptions. `NovuService` dispatches via `co.novu:novu-java:1.6.0`.
- **Security:** HMAC signature verification for GitHub webhooks.
- **Testing:** E2E integration tests (MockBean payload verification + WireMock HTTP-level tests via SDK reflection).

**To Be Implemented:**
- **Slack Bot (`bot` module):** Connect Slack Bolt SDK for Slash Commands (`/notifyme`) and Interactive Modals (View Submissions).
- **Additional Webhook Adapters:** Buildkite signature verification.
- **Novu Workflows:** Create Novu workflows matching notification type keys.

## 5. Antigravity Workflows & Guidelines

1. **Always read `GEMINI.md` first** when starting work on this project.
2. **Evaluate Tasks:** Review file structures using `list_dir` and read logic using `view_file` or `view_code_item`. Consult `docs/IMPLEMENTATION_PLAN.md` for specific in-progress items.
3. **Use Kotlin:** Write idiomatic Kotlin. Follow Spring Boot best practices. Try utilizing constructor injection, `@Service`, `@RestController` and standard JPA repository functionalities where possible.
4. **Testing Setup:** 
   - We leverage standard JUnit 5, Spring Boot Test, Mockito-Kotlin, and WireMock Standalone.
   - Run tests before committing: `./gradlew test`
   - *Crucial:* All unit tests should utilize `org.mockito.kotlin.*` whenever using mocks. Never use Java's `any(Class::class.java)` or `ArgumentCaptor.capture()` as they return `null` and crash Kotlin's non-nullable parameters. Use `org.mockito.kotlin.check {}` for argument verification instead.
   - **WireMock:** Always use the `WireMockServer` instance for `stubFor()`, `resetAll()`, and create a `WireMock(port)` client for `verifyThat()`. Never use static `WireMock.verify()` — it defaults to port 8080.
5. **Linting & Code Standards (Ktlint):**
   - Check formatting: `./gradlew ktlintCheck`
   - Auto-format code: `./gradlew ktlintFormat`
   - Always run `./gradlew ktlintFormat` after writing or modifying Kotlin files so the IDE state is clean.
6. **Git on Windows:**
   - Git commit hangs due to GPG signing. Always use `cmd /c` with `-c commit.gpgSign=false`: `cmd /c "git add . && git -c commit.gpgSign=false commit -m ""message"""`
   - Never chain `git add` and `git commit` in PowerShell directly — it may hang.
7. **Gradle on Windows:**
   - The Gradle daemon can get stuck between test runs. Use `--no-daemon` for reliability, or `.\gradlew.bat --stop` before retrying.
   - If a batch script prompt appears (`¿Desea terminar el trabajo por lotes?`), answer `S` (Sí) to terminate it.

## 6. Notification Types & Slack Commands

### Notification Types
| Type Key | Source | Filters |
|----------|--------|---------|
| `pr_created` | GitHub | author, repo, base_branch |
| `pr_review_requested` | GitHub | author, repo, reviewer |
| `pr_merged_service` | GitHub | author, repo, affected_services[] |
| `pr_checks_passed` | GitHub | author, repo, check_name |
| `pr_checks_failed` | GitHub | author, repo, check_name |
| `deploy_started` | GH Actions/Buildkite | service, environment |
| `deploy_completed` | GH Actions/Buildkite | service, environment, status |
| `flaky_test_detected` | Custom | test_name, service, team |
| `pr_merged_master_success` | GitHub | author, repo |
| `pr_merged_master_error` | GitHub | author, repo |

### Slack Commands
| Command | Description |
|---------|-------------|
| `/notifyme subscribe <type>` | Subscribe to notification type |
| `/notifyme subscribe <type> repo=api` | Subscribe with filter |
| `/notifyme unsubscribe <type>` | Unsubscribe |
| `/notifyme list` | List subscriptions |
| `/notifyme help` | Show help |

## 7. Local Kubernetes Development
```bash
# Build API and Bot
cd api && ./gradlew bootJar && cd ..
cd bot && ./gradlew bootJar && cd ..

# Build Docker images
docker build -t notification-router/api:dev ./api
docker build -t notification-router/bot:dev ./bot

# Deploy to local k3s
kubectl set image deployment/notification-router-api api=notification-router/api:dev
kubectl set image deployment/notification-router-bot bot=notification-router/bot:dev
```
