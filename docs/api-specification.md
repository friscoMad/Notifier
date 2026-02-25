# API Specification

This document details the internal REST API provided by the `API Service` (Kotlin/Spring Boot).

## Base Path
`/api/v1`

---

## Users

### `POST /users`
Creates or updates a user from a Slack identity.
- **Request Body**:
  ```json
  {
    "slackId": "U12345",
    "slackTeamId": "T12345",
    "email": "user@example.com",
    "name": "Jane Doe"
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "id": "uuid",
    "slackId": "U12345",
    "email": "user@example.com",
    "name": "Jane Doe"
  }
  ```

### `GET /users/{slackId}`
Get a user by their Slack ID.

---

## Subscriptions (User)

### `POST /subscriptions`
Create a new user subscription.
- **Request Body**:
  ```json
  {
    "userId": "uuid",
    "notificationTypeId": "uuid",
    "channels": ["slack_dm", "inbox"],
    "channelConfig": {},
    "filters": [
      {"field": "repo", "operator": "IN", "value": ["api", "web"]}
    ]
  }
  ```

### `GET /users/{id}/subscriptions`
List all subscriptions for a given user.

### `PATCH /subscriptions/{id}`
Update an existing subscription.

### `DELETE /subscriptions/{id}`
Remove a subscription.

---

## Subscriptions (Channel)

### `POST /channel-subscriptions`
Subscribe a Slack channel to a notification type.
- **Request Body**:
  ```json
  {
    "slackChannelId": "C12345",
    "slackChannelName": "#dev-alerts",
    "notificationTypeId": "uuid",
    "filters": [],
    "digestEnabled": true,
    "digestInterval": "24h"
  }
  ```

### `GET /channels/{id}/subscriptions`
List subscriptions for a specific Slack channel.

### `DELETE /channel-subscriptions/{id}`
Remove a channel subscription.

---

## Notification Types & Filters

### `GET /notification-types`
List available notification types.
- **Response**:
  ```json
  [
    {
      "id": "uuid",
      "typeKey": "pr_created",
      "name": "PR Created",
      "description": "Triggered when a new Pull Request is opened."
    }
  ]
  ```

### `GET /notification-types/{key}/filters`
Get available filters for a specific notification type.
- **Response**:
  ```json
  [
    {
      "id": "uuid",
      "field": "repo",
      "fieldType": "STRING",
      "operators": ["EQ", "IN"]
    }
  ]
  ```

---

## Webhooks

### `POST /webhooks/github`
Endpoint to receive GitHub webhook events.
- **Headers**: `X-Hub-Signature-256`
- **Body**: GitHub JSON Payload

### `POST /webhooks/github-actions`
Endpoint for GitHub Actions (deployments, runs).

### `POST /webhooks/buildkite`
Endpoint for Buildkite webhooks.

### `POST /events`
Internal endpoint to trigger a normalized event directly.
- **Request Body**:
  ```json
  {
    "typeKey": "flaky_test_detected",
    "metadata": {
      "test_name": "LoginSuite",
      "service": "auth-api",
      "team": "backend"
    },
    "payload": {
      "link": "https://ci.example.com/job/123",
      "error": "Timeout exception..."
    }
  }
  ```