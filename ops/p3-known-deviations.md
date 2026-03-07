# P3 Upgrade Known Deviations & Toggles

This document tracks temporary or specific configuration changes introduced during the Spring Boot 2.7.18 and Java 17 upgrade (P3 Phase).

## 1. Circular References
- **Status**: ✅ **RESOLVED (P4 Phase)**
- **Configuration**: `spring.main.allow-circular-references: false`
- **History**: Spring Boot 2.6+ disables circular references by default. Initial P3 upgrade used `true` as a temporary bypass.
- **Resolution**: Refactored critical circular pairs (e.g., `MemberService` <-> `StoreService`, `OrderService` <-> `TradeService`) using `@Lazy` annotation to support default Spring behavior.

## 2. MVC Path Matching Strategy
- **Status**: ✅ **RESOLVED (P4 Phase)**
- **Configuration**: `spring.mvc.pathmatch.matching-strategy: path_pattern_parser`
- **History**: Initial P3 upgrade kept `ant_path_matcher` due to Swagger compatibility and legacy patterns.
- **Resolution**: Successfully migrated to Springdoc (OpenAPI 3) and verified that all paths are compatible with the more efficient `PathPatternParser`.

## 3. Swagger 2 (Springfox) Migration
- **Status**: ✅ **RESOLVED (P3-Final)**
- **Action**: Removed Springfox dependencies; Implemented Springdoc (`springdoc-openapi-ui`).
- **Access**: Swagger UI is now available at `/swagger-ui/index.html` (internally).

---
*Last Updated: 2026-03-07 by P3/P4 Final Closure*
