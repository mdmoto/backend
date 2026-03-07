# Maollar Service Level Indicators (SLI) & Alerting
**Target Version**: P3-Boot27 (Stable)

## 1. 核心业务指标 (Core Business SLIs)

| 指标 (Metric) | 定义 (Definition) | 目标 (Target SLA) | 告警阈值 (Critical Threshold) |
|:---|:---|:---:|:---:|
| **下单成功率** | 200 OK for `/buyer/order/create` / Total Requests | > 99.5% | < 98% (5 min window) |
| **支付回调成功率** | 200 OK from `/buyer/payment/notify` | 100% | Any 5xx or < 99% |
| **5xx 错误率** | Total 5xx / Total Requests | < 0.1% | > 1% (1 min window) |
| **P95 响应延迟** | 95th percentile latency | < 500ms | > 2000ms |

## 2. 简易告警逻辑 (Simple Alerting Logic)
由于目前未部署 Prometheus/Alertmanager，我们将使用 `ops/check-sli.sh` 每小时分析一次日志并发送报告。

### 检测方式:
- **状态码分析**: 通过 `nginx` 或 `buyer-api` 日志统计 5xx。
- **业务行为分析**: 搜索 `Order created` (成功) 与 `Order creation failed` (失败) 的比例。
- **性能分析**: 使用 `curl -w "%{time_total}"` 针对关键热点接口（如首页、商品详情）进行采样。

## 3. 告警路由 (Alert Routing)
- **Level 1 (Warning)**: 写入 `~/lilishop-deployment/evidence/alerts/warning.log`，供每周巡检查看。
- **Level 2 (Critical)**: 写入基线证据目录，触发 `verify-pipeline.sh` 熔断。

---
*Created by Antigravity AI @ 2026-03-07*
