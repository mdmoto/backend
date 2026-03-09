# Pilot Rollout Release Notes (Spring Boot 3 Migration)

## 🕒 Release Metadata
- **Release Date**: 2026-03-08
- **Release Phase**: Pilot Rollout (Canary)
- **Stable Git Tag**: `prod-pilot-boot3-20260308`
- **Azure Deployment Host**: `4.242.76.35`

## 🧪 Verification Evidence
- **Status**: 🛡️ 24-Hour Escort (Phase 2 Active)
- **Tag**: `prod-pilot-boot3-20260308`
- **Verification Evidence**: `/home/azureuser/lilishop-deployment/evidence/write-smoke-20260308-105301` (PASS)
- **Escort Window**: 2026-03-08 20:40 -> 2026-03-09 20:40 JST
- **Automation**: `monitor-escort.sh` active (15min metrics / 2h smoke).
- **Protocol**: Manual/Automated metrics check every 10 minutes.

## 📊 Monitoring Snapshot (30% Window)
| Time | Cluster | 5xx Rate | P95 Latency | Exceptions | Timeouts | Smoke |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 17:05 | Pilot | 0.00% | 9ms | 0 | 0 | PASS |
| 17:15 | Pilot | 0.00% | 9ms | 0 | 0 | PASS |
| 17:25 | Pilot | | | | | |
| 17:35 | Pilot | | | | | |
| 17:45 | Pilot | | | | | |
| 17:55 | Pilot | | | | | |

## 📊 Monitoring Targets (Pilot: 10% Traffic)
| Metric | Baseline | Target (Pilot) | Threshold (Rollback) |
| :--- | :--- | :--- | :--- |
| Order Success Rate | 99.5% | >99.0% | <95.0% |
| Callback Success | 100% | 100% | <98.0% |
| 5xx Error Rate | <0.1% | <0.5% | >1.0% |
| P95 Latency | <200ms | <300ms | >500ms |

## 🛠️ Rollback Procedure
If any metric exceeds threshold, run:
```bash
bash ~/lilishop-deployment/ops/rollback-p3.sh
```

## 🚥 Deployment Roadmap
1. [x] **0%**: Build & Local Integration (PASS)
2. [x] **10%**: Pilot Rollout (Canary Active)
3. [x] **30%**: Incremental Scaling (Active since 16:50 JST)
   - Verified with: `RUN_WRITE_SMOKE=1` against Port 18888
   - Split verified via UA variation.
4. [x] **50%**: Semi-Full Load (Active since 18:52 JST)
5. [x] **100%**: Permanent Cutover (Active since 20:15 JST)
   - Verified with: `RUN_WRITE_SMOKE=1` (PASS)
   - Nginx logic cleaned of canary headers.
