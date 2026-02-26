# Notification Customization & Templating Architecture

This document answers a key architectural question: **"If the router filters events and sends them to Novu, how do individual engineers customize the format of the message they receive, given they might want different information?"**

## The Short Answer
In our architecture, **Novu Templates are global per Notification Type**, but **Delivery Preferences are per User**. 

Engineers do *not* write their own individual JSON templates for the same event. Instead, the centralized Novu Workflow defines a high-quality, comprehensive template that includes all relevant information for everyone. Engineers then use their **Subscriber Preferences** (via the Slack bot `/notifyme` or a web portal) to choose *if* and *where* they want to receive that standardized message (e.g., Slack DM, Email, or a daily Digest).

---

## Detailed Breakdown

### 1. The Global Workflow Template (`pr_created`)
When an event like `pr_created` enters the Router:
1. The Router evaluates the custom **filters** (e.g., Engineer A only wants PRs from the `api` repo).
2. The Router tells Novu: "Trigger the `pr-created` workflow for Engineer A".

Inside Novu, the `pr-created` workflow has a single, global template defined (like the one in `novu/workflows/pr-created.json`):
```text
A new Pull Request was created by {{payload.pull_request.user.login}} in {{payload.repository.name}}
```
**This template belongs to the system, not the user.** The platform team (or repo maintainers) designs this template to be universally useful.

### 2. User-Level Customization (What engineers CAN change)

While engineers cannot rewrite the syntax of the UI template itself, they have extensive control over **Delivery and Filtering**:

#### A. Granular Filtering (The Router Layer)
Engineers customize *what* triggers a notification.
- "Only alert me if `repo == 'frontend'` AND `author == 'jane'`"
- This logic lives in the Spring Boot `api` database (`subscriptions` table).

#### B. Channel Preferences (The Novu Layer)
Engineers customize *where* the notification goes. 
Novu maintains a "Subscriber Profile" for every engineer. Through the Slack Bot (e.g., `/notifyme preferences`), users can update their Novu profile to say:
- "For `pr_created` events, send me a Slack DM immediately."
- "For `deploy_completed` events, turn off Slack, but send me an Email."

#### C. Digesting (The Novu Layer)
Engineers can opt into Digests. If the global `pr-created` workflow has a "Digest Node" configured (e.g., Batch for 24 hours), Novu natively handles aggregating all 15 PRs that happened that day into a single message for that specific user, rather than pinging them 15 times.

### 3. What if an engineer absolutely needs a wildly different format?

If a specific team needs the `deploy_completed` data formatted as a rigid JSON block for an automated tool, while humans need a friendly Markdown message:

1. **Multiple Channels in One Workflow:** The global `pr-created` workflow in Novu can have *both* an "In-App" step (friendly markdown) AND a "Webhook/Custom" step (raw JSON). The user subscribes to the channel they prefer.
2. **Multiple Workflows:** If the use case is fundamentally different, you create a new Notification Type in the router (e.g., `deploy_completed_audit_log`) mapping to a separate Novu Workflow with its own distinct template.

### Summary
1. **The Router** handles complex logic: "Should John receive this event at all based on the payload metadata?"
2. **Novu (The Engine)** handles presentation and delivery: "John passed the filter. John prefers Slack over Email. I will render the global Markdown template and send it to John's Slack."
