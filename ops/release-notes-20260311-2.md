# Release Notes - 2026-03-11 (#2)

## 📋 Summary
Resolved circular dependency between `CartService` and `TradeBuilder` by introducing `CartPersistenceService`. Verified stability on Azure with full transactional smoke tests.

## 🔍 Evidence Solidification (Azure)
- **Deployment Log**: `~/lilishop-deployment/verify-pipeline.2026-03-11_045221.log`
- **Transactional Smoke Evidence**: `~/lilishop-deployment/evidence/write-smoke-20260311-045506`
- **Nginx Config Status**: Verified GA-Boot3 state in `/etc/nginx/sites-available/maollar`.
- **Nginx Config Backup**: `/etc/nginx/sites-available/maollar.bak.20260311-2`
- **Git Tag**: `prod-backend-boot3-20260311-2`

## 🛠️ Environment Audit (Current Keys Only)
The following environment variables are currently active in the running `buyer-api` process:
- `SPRING_MAIN_ALLOW_CIRCULAR_REFERENCES`
- `SPRING_MVC_PATHMATCH_MATCHING_STRATEGY`
- `HOME`, `PATH`, `USER`, `LANG`, etc.

**Note**: All database/middleware credentials are currently relying on `application-prod.yml` defaults or are not yet injected via env vars. This must be addressed before entering the "fail-fast default password" phase.

## ⏳ Next Step: Fail-Fast Readiness
- [ ] Supplement `.env` with `SPRING_DATASOURCE_PASSWORD`, `SPRING_REDIS_PASSWORD`, etc.
- [ ] Remove hardcoded default passwords from `application-prod.yml`.
