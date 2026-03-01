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

// 1. Cleanup extra environments to avoid duplicate key errors on identifier and apiKeys.key
db.environments.deleteMany({ 
  name: 'Development', 
  _id: { `$ne: ObjectId('69a0605ad18b1aabbc412d10') } 
});

// 2. Update the main Development environment with our local API key and link to user
db.environments.updateOne(
  { name: 'Development' },
  { `$set: {
      apiKeys: [{
        key: '$API_KEY',
        hash: '$HASH',
        _userId: ObjectId('69a0605ad18b1aabbc412d06')
      }],
      identifier: 'development'
    }
  }
);
"@

Write-Host "Executing MongoDB payload..."
docker exec -i novu_mongodb mongosh --username root --password secret --authenticationDatabase admin novu-db --quiet --eval "$MONGO_PAYLOAD"

Write-Host "Novu User Setup Complete!"
Write-Host "Admin Email: admin@notifier.local"
Write-Host "Admin Password: admin123"
Write-Host "API Key: $API_KEY"
Write-Host ""
Write-Host "Running Novu Sync to deploy local workflows..."

# We use a docker container because Windows machines typically won't have Node installed by default
# to run `npx novu sync` natively.
# Legacy sync command for Novu v0.x JSON workflows
# If this fails, you can manually create the workflows in the local dashboard at http://localhost:4000
docker run --rm -v "$(Get-Location)/novu:/app/novu" -e NOVU_API_KEY="$API_KEY" -w /app node:18-alpine npx -y novu@0.19.0 sync --api-url http://host.docker.internal:3000 || Write-Host "Warning: Novu Sync failed. You may need to manually create workflows in the dashboard."

Write-Host "Local Environment setup completed successfully!"
