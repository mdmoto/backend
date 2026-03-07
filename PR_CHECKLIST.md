# Maollar Pull Request & Change Admission Checklist (P4 Phase)

This checklist defines the "Definition of Done" (DoD) for all pull requests and deployment changes to ensure system stability and P4 maintenance standards.

## 🛠️ Mandatory Technical Verification
*Before submitting a PR, ensure the following tools return success (Exit 0).*

- [ ] **✅ Build & Test Pipeline**: Run `bash verify-pipeline.sh` (Local/Azure).
    - [ ] No compilation errors (Java 17).
    - [ ] No flattened test class duplicates.
    - [ ] Maven unit tests pass.
- [ ] **🔬 System Health**: Run `bash ops/scheduled-inspection.sh`.
    - [ ] Actuator health is UP (8888-8892).
    - [ ] Essential ports are reachable.
- [ ] **⚡ Transactional Integrity**: Run `RUN_WRITE_SMOKE=1 bash verify-pipeline.sh`.
    - [ ] Order creation & cancellation flows are SUCCESSFUL.
    - [ ] Smoke DB cleanup is verified.

## 📄 Documentation Requirements
- [ ] **Impact Assessment**: Note any changes to `application.yml` or database DDL.
- [ ] **Known Deviations**: If adding a new circular reference or configuration bypass, it **MUST** be recorded in `ops/p3-known-deviations.md` with a removal plan.
- [ ] **SLI Update**: If introducing a new core business endpoint, update `ops/sli-metrics.md`.

## 🛡️ Forbidden Actions
- [x] **No Hardcoded Secrets**: Use environment variables or `.env` files.
- [x] **No Circular References**: Only allowed if absolutely unavoidable and tagged with `@Lazy`.
- [x] **No Breaking MVC Paths**: Paths must be compatible with `PathPatternParser`.

## 🚀 Deployment Gate
> [!IMPORTANT]
> The final `ops/deploy-gatekeeper.sh` script on the Azure production cluster MUST be executed and return SUCCESS before the "Merge" button is pressed.

---
*Created by Maollar Engineering Governance - 2026-03-07*
