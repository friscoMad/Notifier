# Slack Commands

The Slack Bot is built in Kotlin using the Slack Bolt SDK. It provides commands for users to subscribe to notifications or manage channel subscriptions.

## Global Slash Command: `/notifyme`

### Usage

| Command | Description | Example |
|---------|-------------|---------|
| `/notifyme subscribe <type>` | Subscribe to a notification type with no filters (you get everything for that type). | `/notifyme subscribe pr_created` |
| `/notifyme subscribe <type> <filters...>` | Subscribe to a notification type with specific filters. | `/notifyme subscribe pr_created repo=api,web` |
| `/notifyme subscribe <type> <filter>=<value>` | Subscribe to a deployment event for a specific service. | `/notifyme subscribe deploy_completed service=payment` |
| `/notifyme unsubscribe <type>` | Unsubscribe from a notification type completely. | `/notifyme unsubscribe pr_created` |
| `/notifyme list` | Show a list of all your active subscriptions in a DM. | `/notifyme list` |
| `/notifyme help` | Show help message and examples. | `/notifyme help` |

## Interactive UI (Modals)

The bot will also support an interactive UI (Modals) for easier configuration. If a user runs `/notifyme` without arguments, it opens a modal.

### Subscription Modal
1. **Select Type**: Dropdown of available notification types.
2. **Filters**: Dynamic input fields based on the selected type's available filters (e.g., if `deploy_completed` is selected, fields for `service`, `environment`, `status` appear).
3. **Channels**: Checkboxes for where to receive notifications (e.g., `Slack DM`, `Inbox`).

### Channel Configuration
When a user adds the bot to a public/private channel, they can configure channel-level routing:
- `/notifyme channel <type> #channel`
- `/notifyme channel <type> #channel --digest`

The bot will then handle routing those events to that specific channel (with optional digest batching).