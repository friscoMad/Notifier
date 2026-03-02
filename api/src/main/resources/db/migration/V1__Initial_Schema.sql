-- V1: Initial Schema

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    slack_id VARCHAR(50) NOT NULL,
    slack_team_id VARCHAR(50),
    email VARCHAR(255),
    name VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uc_user_slack_id UNIQUE (slack_id)
);

CREATE INDEX idx_user_slack_id ON users(slack_id);

CREATE TABLE IF NOT EXISTS notification_types (
    id UUID PRIMARY KEY,
    type_key VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    default_channels JSONB NOT NULL DEFAULT '["inbox"]',
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uc_notification_type_key UNIQUE (type_key)
);

CREATE INDEX idx_notification_type_key ON notification_types(type_key);

CREATE TABLE IF NOT EXISTS filter_definitions (
    id UUID PRIMARY KEY,
    notification_type_id UUID NOT NULL REFERENCES notification_types(id),
    field VARCHAR(100) NOT NULL,
    field_type VARCHAR(50) NOT NULL,
    operators JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_filter_def_type_id ON filter_definitions(notification_type_id);

CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_type_id UUID NOT NULL REFERENCES notification_types(id),
    channels JSONB NOT NULL,
    channel_config JSONB NOT NULL DEFAULT '{}',
    filters JSONB NOT NULL DEFAULT '[]',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uc_subscription_user_type UNIQUE (user_id, notification_type_id)
);

CREATE INDEX idx_subscription_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscription_type_id ON subscriptions(notification_type_id);

CREATE TABLE IF NOT EXISTS channel_subscriptions (
    id UUID PRIMARY KEY,
    slack_channel_id VARCHAR(50) NOT NULL,
    slack_channel_name VARCHAR(255) NOT NULL,
    notification_type_id UUID NOT NULL REFERENCES notification_types(id),
    filters JSONB NOT NULL DEFAULT '[]',
    digest_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    digest_interval VARCHAR(20) NOT NULL DEFAULT '24h',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uc_channel_sub_channel_type UNIQUE (slack_channel_id, notification_type_id)
);

CREATE INDEX idx_channel_sub_channel_id ON channel_subscriptions(slack_channel_id);
CREATE INDEX idx_channel_sub_type_id ON channel_subscriptions(notification_type_id);
