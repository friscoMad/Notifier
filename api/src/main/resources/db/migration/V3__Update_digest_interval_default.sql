-- Update digest_interval default from '24h' to '1w' to match the new interval key scheme
-- (1d = 1 minute Novu window, 1w = 2 minute Novu window).
ALTER TABLE channel_subscriptions ALTER COLUMN digest_interval SET DEFAULT '1w';
