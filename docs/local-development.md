# Local Development Setup

This guide walks you through setting up the local Notification Router system from scratch. Our local environment includes PostgreSQL, MongoDB, Redis, and the entire Novu Core stack. We also automatically seed the necessary API keys, Test Users, and Novu JSON workflows.

## Prerequisites
- Docker Compose v2+
- Java 21 (Pre-configured in system environment)
- GitHub CLI (Pre-configured in system environment)
- Node/npm (Optional—our setup scripts use a docker container if Node isn't present)
- **Slack Workspace**: For testing, we recommend the [Slack Developer Program](https://api.slack.com/developer-program) which provides a free Enterprise Grid sandbox. Alternatively, a standard [Slack Free Plan](https://slack.com/get-started) workspace works for basic bot testing.

## 1. Start the Containers
Navigate to the root directory and start the core infrastructure:

```bash
docker compose up -d
```

Wait for all outputs to show "Healthy", especially the database and `novu_api` containers.

## 2. Initialize Novu & Seeding Database

We provide automated setup scripts that connect to the local containers to pre-seed the MongoDB collections. They create the `admin@notifier.local` user, default organization, and define the persistent `NOVU_API_KEY` (stored in `.env`).

**On Windows (Powershell):**
```powershell
.\scripts\setup-novu.ps1
```

**On Mac/Linux:**
```bash
./scripts/setup-novu.sh
```

These scripts also automatically trigger `novu sync` to load the JSON layouts and configurations located in the `novu/workflows/` directory.

## 3. Seed PostgreSQL

Once the PostgreSQL container `router_postgres` is running, seed the initial domain objects:

**On Windows (Powershell):**
```powershell
cmd.exe /c "docker exec -i router_postgres psql -U postgres -d notification_router < seed.sql"
```

**On Mac/Linux:**
```bash
docker exec -i router_postgres psql -U postgres -d notification_router < seed.sql
```

## 4. Run the Spring Boot API

Your `application.yml` is already set to fetch the `NOVU_API_KEY` natively from the environment variable (or falls back to the exact pre-seeded key generated above).

Run the core router application with Gradle:
```bash
./gradlew :api:bootRun
```

OR on Windows:
```powershell
.\gradlew.bat :api:bootRun --no-daemon
```

## 5. Slack Bot Local Development (Socket Mode)

To speed up local development and avoid needing a public URL (like ngrok), you can use Slack **Socket Mode**.

1. **Enable Socket Mode** in your [Slack App Settings](https://api.slack.com/apps):
   - Go to **Settings > Basic Information > App-Level Tokens**.
   - Create a token with `connections:write` scope (usually named `xapp-...`).
   - Go to **Settings > Socket Mode** and enable it.
   - Go to **Features > Event Subscriptions** and ensure events are enabled (Socket Mode handles them automatically).

2. **Configure your environment**:
   Add these to your `.env` file or export them:
   ```env
   SLACK_SOCKET_MODE_ENABLED=true
   SLACK_APP_TOKEN=xapp-your-app-token
   SLACK_BOT_TOKEN=xoxb-your-bot-token
   ```

3. **Run the Bot**:
   ```bash
   ./gradlew :bot:bootRun
   ```
   The bot will connect via WebSockets and start responding to commands and events immediately.

## 6. Connect a Real GitHub Webhook (optional)

To receive real GitHub events instead of using the webhook emulator, expose the Router API publicly and register a webhook in GitHub.

### 6.1 Expose the API locally

Use any tunnel to make port `8082` reachable from the internet:

```bash
# Example with ngrok
ngrok http 8082
# → Forwarding: https://abc123.ngrok.io → localhost:8082
```

### 6.2 Generate a webhook secret

```bash
openssl rand -hex 32
# → e.g. a3f9c2d1b4e7...
```

Add it to your `.env`:

```env
GITHUB_WEBHOOK_SECRET=<the generated value>
```

### 6.3 Register the webhook in GitHub

1. Go to your GitHub repository → **Settings** → **Webhooks** → **Add webhook**
2. Fill in:
   - **Payload URL**: `https://<tunnel-url>/api/v1/webhooks/github`
   - **Content type**: `application/json`
   - **Secret**: the value from step 6.2
   - **Events**: select **"Let me select individual events"** and check:
     - Pull requests
     - Check runs
     - Check suites
     - Deployments
     - Deployment statuses
     - Workflow runs
3. Click **Add webhook** — GitHub will send a `ping` event; the Router responds `202 Accepted`

A green checkmark next to the webhook confirms it is working.

### 6.4 Rotate the webhook secret (if compromised)

Only needed if the secret is ever leaked and needs to be replaced:

1. Generate a new secret: `openssl rand -hex 32`
2. Update `GITHUB_WEBHOOK_SECRET` in your environment and restart the API
3. In GitHub webhook settings, update the **Secret** field to the same new value

### Handled GitHub event types

| GitHub event | Router action |
|---|---|
| `pull_request` `opened` | `pr_created` |
| `pull_request` `review_requested` | `pr_review_requested` |
| `pull_request` `synchronize` | `pr_updated` |
| `pull_request` `closed` | `pr_merged_master_success` / `pr_merged_master_error` |
| `check_run` `completed` | `pr_checks_passed` / `pr_checks_failed` |
| `check_suite` `rerequested` | `pr_checks_failed` |
| `deployment` / `deployment_status` | `deploy_started` / `deploy_completed` |

## 7. Send a Test Payload
You can trigger a test message through the Webhook controller. Assuming the app runs on `8080`:

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/v1/webhooks/github `
   -Method POST `
   -Headers @{ "X-GitHub-Event" = "pull_request"; "Content-Type" = "application/json" } `
   -Body (Get-Content payload.json -Raw)
```

You can then log into Novu on `http://localhost:4000` (User: `admin@notifier.local` Password: `admin123`) and verify the payload triggers your `pr-created` pipeline!

 
 
 
 
 
 
