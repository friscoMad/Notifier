# Notification Router Architecture

## System Design

The Notification Router acts as a unified hub for receiving events from various developer tools (GitHub, Buildkite) and intelligently routing them to end users based on their self-serve preferences.

```
┌─────────────────────────────────────────────────────────────────┐
│                         INPUT                                    │
├─────────────────┬─────────────────┬───────────────────────────────┤
│ GitHub Webhook │ GitHub Actions │ Buildkite Webhook            │
│ (All events)   │ / Deploy       │ / Pipeline events            │
└────────┬────────┴────────┬────────┴─────────────┬───────────────┘
         │                 │                      │
         ▼                 ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│              TRANSFORMATION LAYER (Kotlin/Spring Boot)         │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐    │
│  │ Webhook    │  │ Normalize   │  │ Preference Engine    │    │
│  │ Adapters   │──│ Event       │──│ (Evaluate filters)   │    │
│  └─────────────┘  └─────────────┘  └──────────────────────┘    │
│         │                                    │                   │
│         ▼                                    ▼                   │
│  ┌─────────────────────────────────────────────────────┐        │
│  │            PostgreSQL                                │        │
│  │  users, subscriptions, filters, channel_subs         │        │
│  └─────────────────────────────────────────────────────┘        │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      NOVU (Core)                                │
│  ┌─────────────┐  ┌─────────────┐  ┌───────────────────────┐    │
│  │ Workflows  │  │ Digest      │  │ Providers            │    │
│  │ (per type) │  │ (24h opt)  │  │ Slack, Inbox        │    │
│  └─────────────┘  └─────────────┘  └───────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## Component Details

### 1. Webhook Adapters (API)
Accepts incoming payloads from various sources. Each adapter is responsible for:
- Validating the incoming payload (e.g., verifying GitHub webhook signatures).
- Parsing the payload.
- Mapping the source-specific data to a normalized internal `Event` model.

### 2. Normalized Event Model
A standard representation of any event entering the system. Contains:
- `notification_type_id`: What happened (e.g., `pr_created`).
- `payload`: Data for template rendering.
- `metadata`: Key-value pairs used for filter evaluation (e.g., `repo: "api"`, `author: "dev1"`).

### 3. Preference Engine (API)
Evaluates rules to decide *who* gets notified and *where*.
- Queries the PostgreSQL database to find all subscriptions for the given `notification_type_id`.
- For each subscription, evaluates the subscription filters against the event's `metadata`.
- If filters pass, collects the configured delivery channels (e.g., `slack_dm`, `#dev-alerts`).

### 4. Novu Integration
The engine leverages Novu for the actual delivery and digest logic.
- The router sends a `Trigger` API call to Novu.
- Novu Workflows handle the aggregation (Digest step) and rendering of templates.
- Novu pushes the final message to Slack or the Web Inbox.

### 5. Slack Bot
Provides the self-serve interface for users.
- Built with Slack Kotlin SDK (Bolt).
- Uses interactive modals and slash commands (`/notifyme`).
- Connects to the Router API to fetch available types/filters and manage user subscriptions.

## Database Flow

When an event arrives, the system queries:
1. `subscriptions` (User DMs)
2. `channel_subscriptions` (Slack channels)

It filters these lists in-memory or via SQL based on the event metadata. Matches are batched into API calls to Novu.

## Infrastructure Setup (k3s Local)

Local development utilizes a single-node k3s cluster.
- **k3s**: Lightweight Kubernetes.
- **Novu**: Deployed via community Helm chart (`nova-edge-charts`).
- **PostgreSQL**: Deployed as a pod, provides 2 logical databases (one for App, one for Novu if needed, although the minimal Novu helm might use its own dependencies or external services like Redis).
- **API & Bot**: Custom Kotlin deployments communicating with Postgres and Novu.