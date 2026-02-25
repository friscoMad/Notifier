# Notification Types (MVP)

These are the primary notification types that the router will support. They must be seeded into the `notification_types` and `filter_definitions` tables during the initialization phase (e.g., via Flyway/Liquibase or a data loader).

## Available Types & Filters

| Type Key | Description | Source | Available Filters (Keys) |
|----------|-------------|--------|--------------------------|
| `pr_created` | PR created by user | GitHub Webhook | `author`, `repo`, `base_branch` |
| `pr_review_requested` | PR assigned for review | GitHub Webhook | `author`, `repo`, `reviewer`, `base_branch` |
| `pr_merged_service` | PR merged affecting service | GitHub Webhook | `author`, `repo`, `base_branch`, `affected_services[]` |
| `pr_checks_passed` | PR checks passing | GitHub Webhook | `author`, `repo`, `check_name`, `base_branch` |
| `pr_checks_failed` | PR checks failing | GitHub Webhook | `author`, `repo`, `check_name`, `base_branch` |
| `deploy_started` | Deployment started | GH Actions / Buildkite | `service`, `environment`, `author`, `pipeline` |
| `deploy_completed` | Deployment completed | GH Actions / Buildkite | `service`, `environment`, `author`, `pipeline`, `status` |
| `flaky_test_detected` | Flaky test detected | Buildkite / Custom | `test_name`, `service`, `team` |
| `pr_merged_master_success` | PR merged to master without error | GitHub Webhook | `author`, `repo` |
| `pr_merged_master_error` | PR merged to master with error | GitHub Webhook | `author`, `repo` |

## Payload Normalization Example

When an external system sends a webhook, the Webhook Adapter layer creates an internal `Event` that resembles this:

```json
{
  "typeKey": "deploy_completed",
  "metadata": {
    "service": "api-gateway",
    "environment": "production",
    "author": "john.doe",
    "pipeline": "deploy-api-gateway",
    "status": "success"
  },
  "payload": {
    "title": "Deployment Completed",
    "description": "API Gateway successfully deployed to production.",
    "url": "https://buildkite.com/acme/deploy-api-gateway/builds/123",
    "started_at": "2023-10-27T10:00:00Z",
    "completed_at": "2023-10-27T10:05:00Z"
  }
}
```

This payload is evaluated against the `filters` JSON in the database to route it to the corresponding subscribers.