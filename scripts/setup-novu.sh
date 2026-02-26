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
"

echo "Novu setup complete!"
echo "Admin Email: admin@notifier.local"
echo "Admin Password: admin123"
echo "API Key: $API_KEY"

echo "Running Novu Sync using local API key..."
export NOVU_API_KEY="$API_KEY"
cd api && npx novu@latest sync
