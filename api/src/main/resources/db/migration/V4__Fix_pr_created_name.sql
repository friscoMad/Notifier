UPDATE notification_types
SET name        = 'PR Created',
    description = 'Triggered when a new PR is opened on GitHub'
WHERE type_key = 'pr_created';
