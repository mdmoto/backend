# Release Note - 2026-03-12 (Stable Baseline)

This release establishes a **security-hardened baseline** and resolves critical technical debt (circular dependencies and repository bloat).

## 🚀 Key Deliverables

*   **Engineering Quality**: Resolved circular dependencies in `Goods`, `Category`, and `Store` modules using `@Lazy` injection.
*   **Performance**: Replaced recursive `OrderService` read calls with direct `OrderMapper` queries to prevent deep call stacks.
*   **Hygiene**: Purged 38MB of legacy binaries (`xxl-job-admin-*.jar`) from Git history; updated `.gitignore` for permanent suppression.
*   **Security Intercept**: Added a **0-stage gatekeeper** in `verify-pipeline.sh` that hard-blocks the build if any `REQUIRED_OVERRIDE` placeholders are missing from the environment.

## 🛡️ Reliability & Evidence

*   **Stable Tag**: `v2026.03.12-stable`
*   **Evidence Log (Mac/Azure Simulation)**: `./verify-pipeline.2026-03-12_150627.log`
*   **Verification Status**: 
    *   ✅ Lombok/JDK 17 Compilation: PASS
    *   ✅ Circular Dependency Guard: PASS
    *   ✅ Trade/Order Business logic Unit tests: PASS
    *   ✅ Secret Placeholder Intercept: PASS (Verified with environment injection)

## ⚠️ Action Items for Deployment

1.  **Environment Secret Injection**: All production environments MUST define `LILI_DB_PASSWORD`, `LILI_REDIS_PASSWORD`, and `LILI_JASYPT_PASSWORD`. 
2.  **Pipeline Update**: Ensure the new `verify_pipeline.sh` is used as the primary gatekeeper for all Azure PRs.
