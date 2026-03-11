# Release Notes - 2026-03-11 (#3) - Fail-Fast Migration

## 📋 Summary
Migrated production configuration to a "Fail-Fast" strategy. Sensitve passwords and LBS keys no longer have hardcoded defaults in the JAR and MUST be provided by the environment.

## 🛠️ Changes
- **`config/application-prod.yml`**:
    - Removed default values for `${LILI_DB_PASSWORD}`, `${LILI_REDIS_PASSWORD}`, `${LILI_DRUID_PASSWORD}`, `${LILI_JASYPT_PASSWORD}`, and `${LILI_MAIL_PASSWORD}`.
    - Migrated `LILI_LBS_KEY` and `LILI_LBS_SK` from hardcoded strings to environment placeholders with no defaults.
- **`buyer-api/src/test/resources/application.yml`**:
    - Fixed typo where MySQL password was using `${LILI_REDIS_PASSWORD:lilishop}` instead of `${LILI_DB_PASSWORD:lilishop}`.

## ⚙️ Environment Configuration (Azure)
- **Environment File**: `~/lilishop-deployment/backend/.env` (Permissions: 600)
- **Active Keys**: `LILI_DB_PASSWORD`, `LILI_REDIS_PASSWORD`, `LILI_DRUID_PASSWORD`, `LILI_JASYPT_PASSWORD`, `LILI_LBS_KEY`, `LILI_LBS_SK`.
- **Systemd Integration**: Verified `EnvironmentFile=/home/azureuser/lilishop-deployment/backend/.env` is active in all `lili-*` services.

## ✅ Verification
- Ran full pipeline with `RUN_WRITE_SMOKE=1`.
- All services (`buyer-api`, `consumer`, etc.) successfully connected to DB/Redis using the `.env` provided credentials.
- Transactional smoke test PASSED.
