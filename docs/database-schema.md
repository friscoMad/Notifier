# Database Schema

The core application database is PostgreSQL. It stores identities, notification metadata, and rules for user/channel subscriptions.

## `users`
Tracks individual user identities, mapped to Slack.
- `id`: UUID (Primary Key)
- `slack_id`: VARCHAR(50) (Unique, Not Null)
- `slack_team_id`: VARCHAR(50)
- `email`: VARCHAR(255)
- `name`: VARCHAR(255)
- `created_at`: TIMESTAMP
- `updated_at`: TIMESTAMP

## `notification_types`
Pre-seeded static definitions of events that can be routed.
- `id`: UUID (Primary Key)
- `type_key`: VARCHAR(100) (Unique, Not Null, e.g., `pr_created`)
- `name`: VARCHAR(255) (Not Null)
- `description`: TEXT
- `default_channels`: VARCHAR(50)[] (Default `['inbox']`)
- `created_at`: TIMESTAMP

## `filter_definitions`
What fields can be filtered on for a given notification type.
- `id`: UUID (Primary Key)
- `notification_type_id`: UUID (References `notification_types(id)`)
- `field`: VARCHAR(100) (Not Null, e.g., `repo`)
- `field_type`: VARCHAR(50) (Not Null, e.g., `STRING`, `ARRAY`, `BOOLEAN`)
- `operators`: VARCHAR(50)[] (Not Null, e.g., `EQ`, `IN`, `CONTAINS`)
- `created_at`: TIMESTAMP

## `subscriptions`
Maps a user to a notification type with specific filters.
- `id`: UUID (Primary Key)
- `user_id`: UUID (References `users(id)`, ON DELETE CASCADE)
- `notification_type_id`: UUID (References `notification_types(id)`)
- `channels`: VARCHAR(50)[] (Not Null, e.g., `['slack_dm', 'inbox']`)
- `channel_config`: JSONB (Default `{}`)
- `filters`: JSONB (Default `[]`)
- `enabled`: BOOLEAN (Default TRUE)
- `created_at`: TIMESTAMP
- `updated_at`: TIMESTAMP
- *Constraint*: `UNIQUE(user_id, notification_type_id)`

## `channel_subscriptions`
Maps a Slack channel (instead of a user) to a notification type.
- `id`: UUID (Primary Key)
- `slack_channel_id`: VARCHAR(50) (Not Null)
- `slack_channel_name`: VARCHAR(255) (Not Null)
- `notification_type_id`: UUID (References `notification_types(id)`)
- `filters`: JSONB (Default `[]`)
- `digest_enabled`: BOOLEAN (Default FALSE)
- `digest_interval`: VARCHAR(20) (Default `24h`)
- `enabled`: BOOLEAN (Default TRUE)
- `created_at`: TIMESTAMP
- *Constraint*: `UNIQUE(slack_channel_id, notification_type_id)`