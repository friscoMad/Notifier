#!/bin/bash

# Update application.yml with testing profile
# This script updates the application.yml to use the k3s testing profile

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Backup original file
cp api/src/main/resources/application.yml api/src/main/resources/application.yml.backup

echo -e "${GREEN}Updating application.yml for k3s testing...${NC}"

# Add testing profile to application.yml
cat > api/src/main/resources/application.yml << 'EOF'
spring:
  application:
    name: notification-router-api
  profiles:
    active: local,k3s-testing
  
---

spring:
  config:
    activate:
      on-profile: local
  datasource:
    url: jdbc:postgresql://localhost:5432/notification_router
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate.ddl-auto: none
    show-sql: false
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect

flyway:
  url: jdbc:postgresql://localhost:5432/notification_router
  user: postgres
  password: postgres
  locations: classpath:db/migration

novu:
  api:
    url: http://localhost:3000
    key: test-api-key

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

server:
  port: 8080

logging:
  level:
    com.notifier.router: DEBUG

---

spring:
  config:
    activate:
      on-profile: k3s-testing
  datasource:
    url: jdbc:postgresql://postgres:5432/notification_router
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate.ddl-auto: none
    show-sql: false
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect

flyway:
  url: jdbc:postgresql://postgres:5432/notification_router
  user: postgres
  password: postgres
  locations: classpath:db/migration

novu:
  api:
    url: http://novu-api.novu.svc.cluster.local:3000
    key: ${NOVU_API_KEY:test-api-key}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

server:
  port: 8432

logging:
  level:
    com.notifier.router: DEBUG
EOF

echo -e "${GREEN}✅ application.yml updated successfully!${NC}"
echo -e "${YELLOW}Backup created at: api/src/main/resources/application.yml.backup${NC}"
echo -e "${YELLOW}Testing profile 'k3s-testing' is now active${NC}"