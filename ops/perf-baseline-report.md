# Maollar Performance Baseline & Capacity Report (P4 Phase)

This document establishes the capacity baseline for peak-load scenarios and defines expansion thresholds and degradation (Fall-back) strategies.

## 📊 Peak-load Capacity Baseline (Estimated)

*Based on Azure B2s (2 vCPU, 4GB RAM) environment with current P3/P4 optimizations.*

| Essential Scenarios | Target Endpoint | QPS (Baseline) | P95 Latency | Error Rate | Capacity Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Order Placement** | `/buyer/trade/create` | ~150 | < 800ms | < 0.1% | 🆗 Stable |
| **Payment Callback** | `/buyer/payment/notify` | ~500 | < 200ms | < 0.01% | ✅ High |
| **Search/Query (Read)** | `/buyer/goods/get/page` | ~800 | < 150ms | < 0.05% | ✅ Strong |
| **User Login** | `/buyer/passport/login` | ~120 | < 1200ms | < 0.2% | ⚠️ Auth Bottleneck |

## 🚀 Expansion Thresholds (Scaling Out)

| Threshold Logic | Condition | Required Action |
| :--- | :--- | :--- |
| **CPU High Load** | CPU Utilization > 75% for 3 mins | Add +1 Azure VM Instance |
| **P95 Latency Spike** | Order P95 > 1.5s for 5 mins | Add +1 Instance (or Scale Up DB) |
| **Memory Exhaustion** | JVM Heap > 85% Usage | Scale VM Size (to 8GB RAM) |
| **Error Rate Warning** | HTTP 5xx Errors > 1% | Circuit Breaker investigation |

## 🛡️ Circuit Breaking & Degradation (Fallback Strategy)

| Component | Symptom | Fallback / Degradation Action |
| :--- | :--- | :--- |
| **Recommendation Engine** | Slow response | Disable AI logic; Return cached/static default goods |
| **Point/Point Calculation** | High latency | Log to MQ; Process as background task (Eventual consistency) |
| **Coupons/Promotion** | DB Timeout | Skip coupon calculation; Return base price (Manual refund later) |
| **Payment Gateway** | Timeout | Switch to 'Offline/Manual' payment mode if critical |

## 📈 Benchmarking Methodology
- **Pre-requisite**: No other heavy processes running on Azure VM.
- **Tools**: `ops/latency-profiler.sh` (sequential) or `ab -c 10 -n 1000` (concurrency).
- **Frequency**: Run after every major P4 refactor.

---
*Created by P4 Performance Suite - 2026-03-07*
