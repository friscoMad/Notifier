# Notification Use Cases

To ensure the router and Novu platform handle all engineering constraints, we've designed the system around these core scenarios.

## 1. The "Firehose" (Global Subscription)
**Scenario**: A tech lead or QA engineer wants to know every time a Pull Request is created across the entire organization, immediately.
- **Router Setting**: `subscribe pr_created` (No filters).
- **Novu Setting**: Immediate delivery, Channels: Slack & Email.

## 2. The "Focused Developer" (Granular Filtering)
**Scenario**: A backend developer only cares about PRs in the `api` repository where they are specifically requested as a reviewer. They don't want noise from the `frontend` repo.
- **Router Setting**: `subscribe pr_review_requested repo=api reviewer=johndoe`.
- **Novu Setting**: Immediate delivery, Channel: Slack.

## 3. The "Busy Reviewer" (Custom Digest Grouping)
**Scenario**: A senior engineer receives too many `pr_review_requested` pings. They want them grouped together and delivered twice a day, rather than interrupting their flow.
- **Router Setting**: `subscribe pr_review_requested repo=api`.
- **Novu Setting**: Digest enabled. Custom Digest Variable: `12h`. At 9 AM and 9 PM, Novu sends a single Slack message with a list of the 8 PRs needing their review.

## 4. The "Audit Log" (Email Only)
**Scenario**: A compliance officer needs a record of all `deploy_completed` events in `production`, but they don't want their Slack pinging constantly.
- **Router Setting**: `subscribe deploy_completed environment=production`.
- **Novu Setting**: Deliver via Email only. Turn off Slack. Digest: Off (immediate).

## 5. The "Team Channel" (Routing to Public/Private Channels)
**Scenario**: The backend team wants a passive log of all failed CI checks in their shared `#backend-alerts` Slack channel so anyone can jump on it.
- **Router Setting**: Channel Subscription configured for `#backend-alerts` listening to `pr_checks_failed` where `team=backend`.
- **Novu Setting**: Since it's a channel, it uses a generic integration token rather than a personal subscriber profile. Digest: Optional (e.g., end-of-day rollup of all flaky tests).

---

# Slack Bot UI & Architecture Design

To support these use cases without making the interface overwhelming, we follow a philosophy of "Progressive Disclosure". The majority of engineers will just use the graphical UI (Slack Modals).

## Slash Command (`/notifyme`)
The entry point. Keeps CLI arguments simple for power users.

- `/notifyme`
  - *No arguments*. Pops open the Interactive Modal.
- `/notifyme list`
  - Returns an ephemeral message showing active subscriptions, filters, and digest settings.
- `/notifyme unsubscribe [id]`
  - Quickly drop a subscription.

*(We drop the complex CLI `subscribe type repo=x --digest=12h` syntax for the MVP, as the interactive Modal handles this far more cleanly and prevents syntax errors).*

## The Interactive Modal UI
When an engineer runs `/notifyme`, they get a clean visual form.

**Section 1: What do you want to hear about?**
- `Dropdown`: Notification Type (e.g., *Pull Request Created*, *Deployment Completed*).
- *Action*: Selecting a type dynamically loads **Section 2**.

**Section 2: Filter the noise (Dynamic)**
- If "PR Created" is selected, fields appear for `Repository` (optional) and `Author` (optional).
- If "Deployment" is selected, fields appear for `Service` (optional) and `Environment` (optional).

**Section 3: How should we deliver it?**
- `Checkboxes`: [x] Slack Message  [ ] Email  [ ] Web Inbox
- `Dropdown`: Delivery Speed
  - Immediate (As soon as it happens)
  - Daily Digest (Batch for 24 hours)
  - Half-Day Digest (Batch for 12 hours)

### Under the Hood
When the user clicks "Submit" on the Modal:
1. The Bot saves the filter criteria (Section 1 & 2) in the PostgreSQL `subscriptions` table.
2. The Bot determines the user's Novu `subscriberId` (creating one if it doesn't exist).
3. The Bot calls the Novu API to seamlessly update the user's Preferences (Section 3: toggling active channels and setting the `custom_digest_interval` variable).
