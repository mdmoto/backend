# Spring Boot 3.x Migration Research (P4 Phase)

This document provides a comprehensive "Migration Checklist + Effort Estimation" for upgrading Maollar from Spring Boot 2.7.18 to 3.2.x.

## 🔍 Core Changes Summary

| Feature | Boot 2.7 (Current) | Boot 3.2 (Target) | Impact Level |
| :--- | :--- | :--- | :--- |
| **Java Baseline** | Java 17 (Migrated in P3) | Java 17 or 21 (LTS) | ✅ DONE |
| **Namespace** | `javax.*` | `jakarta.*` | 🔥 HIGH (Requires re-importing all models/validation) |
| **Spring Security** | Security 5.7 (Adapter-based) | Security 6.2 (Composition-based) | 🔥 HIGH (WebSecurityConfigurerAdapter is REMOVED) |
| **OpenAPI** | Springdoc 1.x (v1.6.x) | Springdoc 2.x (v2.x) | ⚠️ MEDIUM (Already migrated to Springdoc in P3) |
| **Query Engine** | MyBatis Plus 3.5.x | MyBatis Plus 3.5.3.x (Jakarta support) | ⚠️ MEDIUM |
| **Maven Plugin** | compiler-plugin:3.x | requires configuration for -parameters | ⚠️ LOW |

## 🛠️ Detailed Migration Checklist

### 1. Namespace Migration (`javax` -> `jakarta`)
- **Action**: All `javax.validation`, `javax.servlet` and `javax.annotation` must be replaced.
- **Tools**: OpenRewrite or manual `sed` commands.
- **Estimated Effort**: 3-4 days for full codebase sweep and verification.

### 2. Spring Security 6.x Rewrite
- **Affected Files**: All `*SecurityConfig.java` in `buyer-api`, `seller-api`, `manager-api`, etc.
- **Action**: 
    - Convert `public class XXSecurityConfig extends WebSecurityConfigurerAdapter` to `public class XXSecurityConfig`.
    - Replace `configure(HttpSecurity http)` with `@Bean public SecurityFilterChain filterChain(HttpSecurity http)`.
    - Update `antMatchers` to `requestMatchers` (matching logic has changed).
- **Estimated Effort**: 2-3 days per API module.

### 3. Springdoc / OpenAPI 3.2+
- **Action**: 
    - Change dependency to `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0+`.
    - Note that `@Parameter(hidden = true)` and other OpenAPI 3 annotations already in place.
- **Estimated Effort**: 1 day.

### 4. Middleware / Drivers
- **Redis (Jedis/Lettuce)**: Needs Jakarta-compatible drivers.
- **RocketMQ**: Must be compatible with Spring Boot 3.
- **MyBatis Plus**: Must ensure the latest 3.5.3+ version is used for Jakarta namespace support.

## ⌛工期评估 (Effort Estimation)

| 阶段 | 任务描述 | 预计工期 (Man-Days) | 风险等级 |
| :--- | :--- | :--- | :--- |
| **P4-R1** | 自动化命名空间替换 (`javax` -> `jakarta`) | 2 | 低 |
| **P4-R2** | Spring Security 6 适配与鉴权逻辑重构 | 5 | 高 |
| **P4-R3** | 依赖地狱解决 (3rd Party JARs) | 3 | 中 |
| **P4-R4** | 集成测试与全量回归 (Dev/Staging) | 4 | 中 |
| **Total** | | **14 人天** | |

## 💡 Recommendation
DO NOT directly upgrade. Use a parallel "Boot3 Integration Branch" and perform a module-by-module migration beginning with `framework`, then `common-api`, and lastly the business APIs.

---
*Created by P4 Research Suite - 2026-03-07*
