# Spring Boot 3 Migration Burn-down Board (Blocker Tracking)

| Blocker ID | Module | Feature | Owner | Effort (h) | Validation Command | Risk | Rollback Point | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **A-1-F** | `framework` | Jakarta Migration | AI/Dev | 16 | `mvn clean compile -pl framework` | High (Side effects) | Git Revert | 🔴 PENDING |
| **A-2-B** | `buyer-api` | Security Config | AI/Dev | 8 | `curl -i http://localhost/actuator/health` | Med (Access) | Config Rollback | 🔴 PENDING |
| **A-2-M** | `manager-api` | Security Config | AI/Dev | 8 | `mvn test -Dtest=SecurityTest` | Med (Access) | Config Rollback | 🔴 PENDING |
| **A-3-ALL** | `common` | Springdoc 2.x | AI/Dev | 4 | Access `/swagger-ui/index.html` | Low | Git Revert | 🔴 PENDING |
| **B-2-MQ** | `consumer` | RocketMQ SB3 | Dev | 8 | Smoke test: Message consumption | Med | Version revert | 🔴 PENDING |

## 🚀 Go/No-Go Conditions
- **GO-1**: All Category A items marked as Done.
- **GO-2**: Total unit test coverage for `framework` >= 80%.
- **GO-3**: Pilot module (`common-api`) passes Gatekeeper on Boot 3 Branch.
- **GO-4**: Smoke test passes on Azure Staging (P3 baseline vs P4 Boot3).

---
*Created: 2026-03-07*
