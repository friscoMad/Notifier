<#
.SYNOPSIS
Starts an ngrok HTTPS tunnel for the local Novu API (port 3000), updates API_ROOT_URL
in docker-compose.yml, and restarts the Novu containers so OAuth callbacks work.

.DESCRIPTION
Slack requires an HTTPS redirect URL for OAuth. This script:
  1. Installs ngrok via winget if not present
  2. Reads NGROK_AUTHTOKEN from .env and configures ngrok
  3. Starts ngrok targeting port 3000
  4. Patches API_ROOT_URL in docker-compose.yml with the public HTTPS URL
  5. Restarts novu-api and novu-worker
  6. Prints the Slack redirect URL to add to your Slack app

.NOTES
Requires: Docker Desktop running with the Novu stack already up (docker compose up -d)
          NGROK_AUTHTOKEN set in .env (free account at https://dashboard.ngrok.com/signup)
#>

$ErrorActionPreference = "Stop"

$ComposeFile  = Join-Path $PSScriptRoot "..\docker-compose.yml"
$EnvLocalFile = Join-Path $PSScriptRoot "..\.env.local"
$NgrokApiUrl  = "http://localhost:4040/api/tunnels"

# --- 1. Load .env.local -------------------------------------------------------

if (-not (Test-Path $EnvLocalFile)) {
    Write-Host ""
    Write-Host "ERROR: .env.local not found at $EnvLocalFile"
    Write-Host ""
    Write-Host "Create it with your secrets (it is gitignored):"
    Write-Host "  NGROK_AUTHTOKEN=<your-token>"
    Write-Host ""
    Write-Host "Get a free ngrok token at: https://dashboard.ngrok.com/get-started/your-authtoken"
    Write-Host ""
    exit 1
}

$envVars = @{}
Get-Content $EnvLocalFile | Where-Object { $_ -match '^\s*([^#][^=]*)=(.*)$' } | ForEach-Object {
    $envVars[$Matches[1].Trim()] = $Matches[2].Trim()
}

$ngrokAuthToken = $envVars["NGROK_AUTHTOKEN"]
if (-not $ngrokAuthToken) {
    Write-Host ""
    Write-Host "ERROR: NGROK_AUTHTOKEN is not set in .env.local"
    Write-Host ""
    Write-Host "To get a free authtoken:"
    Write-Host "  1. Sign up at https://dashboard.ngrok.com/signup"
    Write-Host "  2. Copy your token from https://dashboard.ngrok.com/get-started/your-authtoken"
    Write-Host "  3. Add to .env.local:  NGROK_AUTHTOKEN=<your-token>"
    Write-Host ""
    exit 1
}

# --- 2. Ensure ngrok is installed ---------------------------------------------

if (-not (Get-Command "ngrok" -ErrorAction SilentlyContinue)) {
    Write-Host "ngrok not found. Installing via winget (user scope, no admin required)..."
    winget install --id ngrok.ngrok --scope user --silent --accept-source-agreements --accept-package-agreements
    $env:PATH = [System.Environment]::GetEnvironmentVariable("PATH", "Machine") + ";" +
                [System.Environment]::GetEnvironmentVariable("PATH", "User")
}

if (-not (Get-Command "ngrok" -ErrorAction SilentlyContinue)) {
    Write-Host "winget did not put ngrok on PATH. Downloading directly..."
    $ngrokDir = "$env:LOCALAPPDATA\ngrok"
    $ngrokZip = "$ngrokDir\ngrok.zip"
    New-Item -ItemType Directory -Force -Path $ngrokDir | Out-Null
    Invoke-WebRequest -Uri "https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-windows-amd64.zip" `
        -OutFile $ngrokZip -UseBasicParsing
    Expand-Archive -Path $ngrokZip -DestinationPath $ngrokDir -Force
    Remove-Item $ngrokZip
    $env:PATH = "$ngrokDir;$env:PATH"
}

if (-not (Get-Command "ngrok" -ErrorAction SilentlyContinue)) {
    Write-Error "Could not install ngrok. Please install it manually from https://ngrok.com/download"
    exit 1
}

# --- 3. Resolve ngrok full path -----------------------------------------------

$ngrokExe = (Get-Command "ngrok").Source
Write-Host "Using ngrok at: $ngrokExe"

# --- 4. Update ngrok if needed ------------------------------------------------

Write-Host "Checking for ngrok updates..."
& $ngrokExe update 2>&1 | Where-Object { $_ -notmatch "^t=" } | Write-Host

# --- 5. Configure ngrok authtoken (after update so new binary is used) -------

Write-Host "Configuring ngrok authtoken..."
& $ngrokExe config add-authtoken $ngrokAuthToken
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to configure ngrok authtoken."
    exit 1
}

# --- 6. Start ngrok -----------------------------------------------------------

Write-Host "Starting ngrok tunnel on port 3000..."

# Kill any existing (possibly stale) ngrok process first
Get-Process -Name "ngrok" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1

$ngrokJob = Start-Process -FilePath $ngrokExe -ArgumentList "http 3000" -WindowStyle Hidden -PassThru
Write-Host "ngrok started (PID $($ngrokJob.Id)). Waiting for tunnel to be established..."
Start-Sleep -Seconds 3

# Verify ngrok is still running (it exits immediately if auth fails)
if ($ngrokJob.HasExited) {
    Write-Error "ngrok exited immediately (exit code $($ngrokJob.ExitCode)). Check your authtoken."
    exit 1
}

# --- 7. Poll ngrok API for the public HTTPS URL -------------------------------

$publicUrl = $null
$attempts = 0
$maxAttempts = 15

while (-not $publicUrl -and $attempts -lt $maxAttempts) {
    $attempts++
    try {
        $tunnels = (Invoke-RestMethod -Uri $NgrokApiUrl -ErrorAction Stop).tunnels
        $publicUrl = ($tunnels | Where-Object { $_.proto -eq "https" } | Select-Object -First 1).public_url
    } catch {
        # ngrok API not ready yet
    }

    if (-not $publicUrl) {
        Write-Host "  Waiting for ngrok tunnel... (attempt $attempts/$maxAttempts)"
        Start-Sleep -Seconds 2
    }
}

if (-not $publicUrl) {
    Write-Error "Could not get ngrok HTTPS URL after $maxAttempts attempts. Check ngrok logs."
    exit 1
}

Write-Host "ngrok tunnel active: $publicUrl"

# --- 8. Patch API_ROOT_URL in docker-compose.yml ------------------------------

Write-Host "Updating API_ROOT_URL in docker-compose.yml..."

$content = Get-Content $ComposeFile -Raw
$updated = $content -replace '(API_ROOT_URL:\s*)http[^\r\n]+', "`${1}$publicUrl"

if ($content -eq $updated) {
    Write-Host "docker-compose.yml already up to date (API_ROOT_URL = $publicUrl)."
} else {
    Set-Content -Path $ComposeFile -Value $updated -NoNewline
    Write-Host "docker-compose.yml updated (API_ROOT_URL = $publicUrl)."
}

# --- 9. Restart Novu containers -----------------------------------------------

Write-Host "Restarting novu-api and novu-worker..."

Push-Location (Join-Path $PSScriptRoot "..")
try {
    docker compose up -d --force-recreate api worker
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Done! Add the following redirect URL to your Slack app:"
Write-Host ""
Write-Host "  $publicUrl/v1/integrations/chat/oauth/callback"
Write-Host ""
Write-Host 'Slack app settings: https://api.slack.com/apps -> OAuth & Permissions -> Redirect URLs'
