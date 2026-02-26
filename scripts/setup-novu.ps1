<#
.SYNOPSIS
Initializes the local Novu instance by seeding a default Admin User, Organization, and API Key into MongoDB, then runs the Novu CLI via Docker to sync the local JSON workflows.
#>

$ErrorActionPreference = "Stop"

Write-Host "Waiting for MongoDB to be ready..."
Start-Sleep -Seconds 5

Write-Host "Seeding default Novu Admin User, Organization, and API Key..."

# Load shared variables from .env
$envFile = Join-Path $PSScriptRoot "..\.env"
Get-Content $envFile | Where-Object { $_ -match '^\s*(?!#)([^=]+)=(.*)$' } | ForEach-Object {
    Set-Variable -Name $Matches[1] -Value $Matches[2].Trim()
}

$USER_ID = $NOVU_TEST_USER_ID
$ORG_ID = $NOVU_TEST_ORG_ID
$ENV_DEV_ID = $NOVU_TEST_ENV_ID
$API_KEY = $NOVU_API_KEY
$HASH = $NOVU_API_KEY_HASH
$PASSWORD_HASH = $NOVU_ADMIN_PASSWORD_HASH

$MongoCommand = @"
db.users.updateOne(
  { email: 'admin@notifier.local' },
  { `$setOnInsert: {
      _id: ObjectId('$USER_ID'),
      firstName: 'Admin',
      lastName: 'User',
      password: '$PASSWORD_HASH',
      tokens: [],
      createdAt: new Date(),
      updatedAt: new Date(),
      __v: 0
    }
  },
  { upsert: true }
);

db.organizations.updateOne(
  { name: 'NotifierLocal' },
  { `$setOnInsert: {
      _id: ObjectId('$ORG_ID'),
      name: 'NotifierLocal',
      logo: 'https://novu.co/images/novu-logo.png',
      createdAt: new Date(),
      updatedAt: new Date(),
      __v: 0
    }
  },
  { upsert: true }
);

db.members.updateOne(
  { _userId: ObjectId('$USER_ID'), _organizationId: ObjectId('$ORG_ID') },
  { `$setOnInsert: {
      roles: ['admin'],
      invite: null,
      memberStatus: 'active',
      createdAt: new Date(),
      updatedAt: new Date(),
      __v: 0
    }
  },
  { upsert: true }
);

db.environments.updateOne(
  { _parentId: null, _organizationId: ObjectId('$ORG_ID'), name: 'Development' },
  { `$setOnInsert: {
      _id: ObjectId('$ENV_DEV_ID'),
      name: 'Development',
      identifier: 'development',
      apiKeys: [{
        key: '$API_KEY',
        hash: '$HASH'
      }],
      apiRateLimits: {
        trigger: { burstAllowance: 0, windowDuration: 0, maximumLimit: 0 },
        configuration: { burstAllowance: 0, windowDuration: 0, maximumLimit: 0 },
        global: { burstAllowance: 0, windowDuration: 0, maximumLimit: 0 },
        default: { burstAllowance: 0, windowDuration: 0, maximumLimit: 0 }
      },
      echo: { url: '' },
      createdAt: new Date(),
      updatedAt: new Date(),
      __v: 0
    }
  },
  { upsert: true }
);
"@

Write-Host "Executing MongoDB payload..."
docker exec -i novu_mongodb mongosh --username root --password secret --authenticationDatabase admin novu-db --quiet --eval $MongoCommand

Write-Host "Novu User Setup Complete!"
Write-Host "Admin Email: admin@notifier.local"
Write-Host "Admin Password: admin123"
Write-Host "API Key: $API_KEY"
Write-Host ""
Write-Host "Running Novu Sync to deploy local workflows..."

# We use a docker container because Windows machines typically won't have Node installed by default
# to run `npx novu sync` natively.
docker run --rm -v "$pwd\novu:/app/novu" -e NOVU_API_KEY="$API_KEY" -w /app node:18-alpine npx -y novu@latest sync --api-url http://host.docker.internal:3000

Write-Host "Local Environment sync completed successfully!"
