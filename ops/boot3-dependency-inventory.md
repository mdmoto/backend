# Spring Boot 3.x Dependency Compatibility Inventory

This document categorizes all tasks required for the Spring Boot 3 migration into three priority levels (A, B, C).

## 🟢 Category A: Hard Blockers (Must fix first)
| Task ID | Item | Reason | Impact |
| :--- | :--- | :--- | :--- |
| **A-1** | `javax.*` -> `jakarta.*` | Spring Boot 3 / Cloud 2023 requires Jakarta EE 9/10. | Entire codebase (Compilation) |
| **A-2** | Security Config Rewrite | `WebSecurityConfigurerAdapter` is removed; FilterChain Beans required. | Authentication/Authorization |
| **A-3** | Springdoc 2.x Upgrade | Springdoc 1.x is incompatible with Jakarta namespace. | API Documentation |
| **A-4** | Actuator Compatibility | Management paths and health indicators require verification. | Monitoring & Deployment Gates |
| **A-5** | Hibernate 6+ / MP 3.5.3+ | JPA/ORM layers must support Jakarta. | Database Layer |

## 🟡 Category B: Parallel Upgrades (Tools & SDKs)
| Task ID | Item | Reason | Impact |
| :--- | :--- | :--- | :--- |
| **B-1** | Redisson / Redis Client | Version update for Jakarta/SB3 compatibility. | Caching |
| **B-2** | RocketMQ Client | Version sync for Spring Boot 3 support. | Message Queue |
| **B-3** | Utils (Hutool / FastJSON) | Ensure no hidden `javax` dependencies in transitive libs. | General Utilities |
| **B-4** | MapStruct / Lombok | Requires version harmony for Annotation Processing. | DTO Mapping |

## 🔵 Category C: Deferred Items (Non-critical)
| Task ID | Item | Reason | Impact |
| :--- | :--- | :--- | :--- |
| **C-1** | Virtual Threads (Java 21) | Not strictly required for Boot 3 but recommended later. | Performance |
| **C-2** | Observability (Micrometer) | New tracing/observation APIs available. | Advanced Monitoring |
| **C-3** | Build Optimizations | Native Image (GraalVM) readiness. | Startup Speed |

---
*Status: Initial Inventory Created - 2026-03-07*
