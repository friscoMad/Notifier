#!/bin/bash
# scripts/setup-novu.sh

set -e

echo "Waiting for MongoDB to be ready..."
sleep 10

echo "Seeding default Novu Admin User, Organization, and API Key..."

# Load shared variables from .env
set -a
source "$(dirname "$0")/../.env"
set +a

USER_ID="$NOVU_TEST_USER_ID"
ORG_ID="$NOVU_TEST_ORG_ID"
ENV_DEV_ID="$NOVU_TEST_ENV_ID"
API_KEY="$NOVU_API_KEY"
HASH="$NOVU_API_KEY_HASH"
PASSWORD_HASH="$NOVU_ADMIN_PASSWORD_HASH"

MONGO_CMD="docker exec -i novu_mongodb mongosh --username root --password secret --authenticationDatabase admin novu-db --quiet --eval"

# Encrypt the API key using Novu's encryptSecret so the dashboard can decrypt it.
# Novu treats any value starting with "nvsk." as encrypted — storing the raw key
# causes "Invalid initialization vector" when the dashboard fetches /v1/environments.
ENCRYPTED_API_KEY=$(docker exec \
  -e NEW_RELIC_APP_NAME=skip \
  -e STORE_ENCRYPTION_KEY="${NOVU_ENCRYPTION_KEY:-12345678901234567890123456789012}" \
  novu_api node -e "
process.env.STORE_ENCRYPTION_KEY = process.env.STORE_ENCRYPTION_KEY;
const { encryptSecret } = require('@novu/application-generic');
process.stdout.write(encryptSecret('$API_KEY'));
" 2>/dev/null)

# 1. Create Default User
$MONGO_CMD "
db.users.updateOne(
  { email: 'admin@notifier.local' },
  { \$setOnInsert: {
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
"

# 2. Create Default Organization
$MONGO_CMD "
db.organizations.updateOne(
  { name: 'NotifierLocal' },
  { \$setOnInsert: {
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
"

# 2b. Remove workflow limit (self-hosted free tier caps at 20 workflows;
#     we need 30 = 10 notification types x 3 channels: in_app, chat, email)
$MONGO_CMD "
db.organizations.updateOne(
  { name: 'NotifierLocal' },
  { \$set: { apiServiceLevel: 'business' } }
);
"

# 3. Create Default Member mapping
$MONGO_CMD "
db.members.updateOne(
  { _userId: ObjectId('$USER_ID'), _organizationId: ObjectId('$ORG_ID') },
  { \$setOnInsert: {
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
"

# 4. Create Development Environment with our static API Key
$MONGO_CMD "
db.environments.updateOne(
  { _parentId: null, _organizationId: ObjectId('$ORG_ID'), name: 'Development' },
  { \$setOnInsert: {
      _id: ObjectId('$ENV_DEV_ID'),
      name: 'Development',
      identifier: 'development',
      apiKeys: [{
        key: '$ENCRYPTED_API_KEY',
        hash: '$HASH',
        _userId: ObjectId('$USER_ID')
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
"

# 5. Create Default Notification Group (required by NovuSeeder to attach workflows)
$MONGO_CMD "
db.notificationgroups.updateOne(
  { name: 'General', _organizationId: ObjectId('$ORG_ID') },
  { \$setOnInsert: {
      name: 'General',
      _organizationId: ObjectId('$ORG_ID'),
      _environmentId: ObjectId('$ENV_DEV_ID'),
      createdAt: new Date(),
      updatedAt: new Date(),
      __v: 0
    }
  },
  { upsert: true }
);
"

echo "Novu setup complete!"
echo "Admin Email: admin@notifier.local"
echo "Admin Password: admin123"
echo "API Key: $API_KEY"

echo "Workflows are seeded by NovuSeeder.kt on API startup — no novu sync needed."
