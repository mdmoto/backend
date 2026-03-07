# P3 Upgrade Known Deviations & Toggles

This document tracks temporary or specific configuration changes introduced during the Spring Boot 2.7.18 and Java 17 upgrade (P3 Phase).

## 1. Circular References
- **Configuration**: `spring.main.allow-circular-references: true`
- **Reason**: Spring Boot 2.6+ disables circular references by default. Lilishop's legacy architecture (especially in `framework` and `buyer-api`) heavily relies on circular bean injection (e.g., Service A -> Service B -> Service A).
- **Impact**: Slight startup overhead; potential architectural debt.
- **Removal Condition**: Systematic refactoring of service layers to use Constructor Injection or extracting shared logic into a third "BaseService" or "Manager" layer to break the cycles.

## 2. MVC Path Matching Strategy
- **Configuration**: `spring.mvc.pathmatch.matching-strategy: ant_path_matcher`
- **Reason**: Spring Boot 2.6+ switched the default from `AntPathMatcher` to `PathPatternParser`. Springfox (Swagger 2) is incompatible with `PathPatternParser` and fails to start.
- **Impact**: Uses the legacy path matching algorithm.
- **Removal Condition**: Successful migration to `springdoc-openapi` (OpenAPI 3), which supports `PathPatternParser`.

## 3. Swagger 2 (Springfox) Disabled
- **Status**: Currently disabled via commenting out `@EnableSwagger2WebMvc`.
- **Reason**: Springfox 3.0.0 is effectively abandoned and has severe compatibility issues with Spring Boot 2.7's `DocumentationPluginsBootstrapper`.
- **Target**: Migrate to `springdoc-openapi-ui`.

---
*Last Updated: 2026-03-07 by P3 Upgrade Pipeline*
