# Maollar Release Notes - 2026-03-07
## Version: prod-p3-boot27-java17-20260307

### 1. 核心升级 (Core Upgrade)
- **Framework**: Upgraded from Spring Boot 2.4.10 to **2.7.18**.
- **Java**: Upgraded from Java 1.8 to **Java 17**.
- **Dependencies**: Lombok (1.18.30), Maven Compiler Plugin (3.11.0), Springdoc-OpenAPI (1.7.0).

### 2. 关键变更与风险项 (Key Changes & Deviations)
- **Circular References**: Enabled via `spring.main.allow-circular-references: true` to support legacy code.
- **Path Matching**: Switched to `ant_path_matcher` for Springdoc and legacy API compatibility.
- **Swagger Migration**: Successfully migrated from Springfox (Swagger 2) to **Springdoc (OpenAPI 3)**.
  - UI Path: `/swagger-ui.html`
  - Disabled in `prod` profile by default.

### 3. 发布与回滚机制 (Deployment & Rollback)
- **Atomic Deployment**: Using symlink-based release system in `dist/`.
  - Current version: `dist/current -> dist/releases/20260307-*`
- **Rollback Instruction**:
  ```bash
  cd ~/lilishop-deployment/ops
  bash rollback-p3.sh
  ```
  This command points the `current` symlink back to the previous timestamp directory and restarts services.

### 4. 自动化门禁证据 (Gate Evidence)
- **Build & Verification Log**: `~/lilishop-deployment/verify-pipeline.2026-03-07_092832.log`
- **Smoke Test Evidence**: `~/lilishop-deployment/evidence/write-smoke-20260307-093033`
- **Status**: All steps **PASS** (7/7).

---
*Verified by Antigravity AI @ 2026-03-07*
