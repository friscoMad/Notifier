-- Allow multiple subscriptions to the same event type with different filters.
-- Replace the old unique constraint (user + type) with one that includes a hash
-- of the filters, so duplicates are only blocked when the filters are identical.

ALTER TABLE subscriptions DROP CONSTRAINT uc_subscription_user_type;
CREATE UNIQUE INDEX uc_subscription_user_type_filters
    ON subscriptions (user_id, notification_type_id, md5(filters::text));

ALTER TABLE channel_subscriptions DROP CONSTRAINT uc_channel_sub_channel_type;
CREATE UNIQUE INDEX uc_channel_sub_channel_type_filters
    ON channel_subscriptions (slack_channel_id, notification_type_id, md5(filters::text));
