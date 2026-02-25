# Notification Router

A centralized notification management system with self-service subscription via Slack.

## Project Overview

This project provides a unified notification router that allows developers to receive notifications from various sources (GitHub, CI/CD systems) through multiple channels (Slack, Email, Web Inbox) with flexible filtering and subscription capabilities.

### Key Features

- **Unified API**: Single entry point for all notification sources
- **Self-Service**: Users configure their subscriptions via Slack bot
- **Flexible Filtering**: Filter notifications by type, repository, author, service, etc.
- **Digest Support**: Group notifications over 24h periods
- **Multi-Channel**: Slack DM, Slack Channels, Web Inbox
- **Open Source**: Built on Novu for notification delivery

## Architecture

```
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

## Tech Stack

| Component | Technology |
|-----------|------------|
| API Service | Kotlin + Spring Boot |
| Slack Bot | Kotlin + Slack SDK |
| Database | PostgreSQL |
| Notification Engine | Novu (Helm) |
| Container | k3s / Kubernetes |

## Project Structure

```
notification-router/
├── CLAUDE.md                 # This file
├── docs/                     # Documentation
│   ├── architecture.md
│   ├── api-specification.md
│   ├── database-schema.md
│   ├── notification-types.md
│   └── slack-commands.md
├── k8s/                      # Kubernetes manifests
│   ├── namespace.yaml
│   ├── postgres.yaml
│   ├── api-deployment.yaml
│   ├── bot-deployment.yaml
│   └── secrets.yaml
├── api/                      # Kotlin Spring Boot API
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/main/kotlin/
├── bot/                      # Kotlin Slack Bot
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/main/kotlin/
└── agents/                   # Agent task definitions
    ├── phase1-infra.yaml
    ├── phase2-core-api.yaml
    ├── phase3-webhooks.yaml
    ├── phase4-slack-bot.yaml
    ├── phase5-novu-integration.yaml
    └── phase6-testing.yaml
```

## How Agents Should Work

### General Guidelines

1. **Always read CLAUDE.md first** when starting work on this project
2. **Follow the phase order** defined in `agents/` directory
3. **Run tests before committing** - see testing guidelines below
4. **Use Kotlin** for all new code (API and Bot)
5. **Document all APIs** in OpenAPI/Swagger format

### Phase Workflow

Agents should work through phases sequentially:

1. **Phase 1 - Infrastructure**: Setup k3s, PostgreSQL, Novu Helm
2. **Phase 2 - Core API**: User management, subscriptions, preferences
3. **Phase 3 - Webhooks**: GitHub, GitHub Actions, Buildkite adapters
4. **Phase 4 - Slack Bot**: Commands, interactive modals
5. **Phase 5 - Novu Integration**: Workflows, digest, providers
6. **Phase 6 - Testing**: Unit, integration, load tests

### Code Standards

- Use Kotlin idiomatic patterns
- Follow Spring Boot best practices for API
- Use Slack Kotlin SDK for bot development
- Write unit tests for all services (minimum 80% coverage)
- Use Spring Data JPA for database access

### Testing Requirements

Before completing any phase:
- Run `./gradlew test` for unit tests
- Verify API with `./gradlew bootJar && java -jar build/libs/*.jar`
- Test Docker image builds successfully

### Kubernetes Development

For local development:
```bash
# Build API
cd api && ./gradlew bootJar && cd ..
# Build Bot
cd bot && ./gradlew bootJar && cd ..

# Build Docker images
docker build -t notification-router/api:dev ./api
docker build -t notification-router/bot:dev ./bot

# Deploy to k3s
kubectl set image deployment/notification-router-api api=notification-router/api:dev
kubectl set image deployment/notification-router-bot bot=notification-router/bot:dev
```

## Notification Types

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

## Slack Commands

| Command | Description |
|---------|-------------|
| `/notifyme subscribe <type>` | Subscribe to notification type |
| `/notifyme subscribe <type> repo=api` | Subscribe with filter |
| `/notifyme unsubscribe <type>` | Unsubscribe |
| `/notifyme list` | List subscriptions |
| `/notifyme help` | Show help |

## Environment Variables

### API Service
```
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/notification_router
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
NOVU_API_URL=http://novu-api:3000
NOVU_API_KEY=<novu-api-key>
```

### Slack Bot
```
SLACK_BOT_TOKEN=xoxb-<bot-token>
SLACK_SIGNING_SECRET=<signing-secret>
API_URL=http://notification-router-api:8080
```

## Getting Started

1. Install k3s and kubectl
2. Run `make setup` to install PostgreSQL and Novu
3. Run `make db-migrate` to create database schema
4. Run `make run-api` to start API locally
5. Run `make run-bot` to start Slack bot locally

See `docs/architecture.md` for detailed setup instructions.
