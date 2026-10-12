# Phase 1 verification — 2026-10-09

Executed in `/workspace/banking-app-dra`, using Java 17.0.20.1, Gradle 8.14.3, Spring Boot 3.5.16 and Spring Cloud 2025.0.3.

| Check | Observed outcome |
|---|---|
| Gradle build | Successful; all executable service jars produced |
| Existing generated foundation tests | Eight executed, zero failures/errors/skips; five MVC probe tests and three live infrastructure application health tests |
| Repeated installation script | Successful; 58 tasks up to date |
| Compose configuration | Valid |
| Container image builds | All eight application images built; non-root runtime and dependency-free Java readiness probe |
| Java-only startup | Successful; repeated startup reused owned processes and passed smoke |
| Java-only shutdown | Completed before switching to Compose |
| Compose startup | All fifteen core containers healthy |
| Live Java and Compose smoke | Eight healthy applications, shared config value returned, seven Eureka registrations and five correct gateway probe responses |
| Keycloak | Imported banking realm; OIDC discovery returned expected issuer and authorization endpoint |
| Kafka | Broker topic-list command completed successfully (no business topics yet) |
| MySQL/PostgreSQL/MongoDB | Compose health checks passed; MySQL executes SELECT 1, PostgreSQL readiness accepts connections, MongoDB ping succeeds |

Initial scaffold/test issues and environment failures were corrected before these results: disabled Config Client test imports, Eureka server test prerequisites, Java proxy/CA configuration, container artifact permissions, and Docker VFS disk exhaustion. Artifacts were built with checksum/TLS verification enabled. Build cache and obsolete setup-created images were removed; source and database volumes were preserved.

Optional monitoring was not started. Business endpoints, migrations, money invariants, authentication enforcement, Kafka delivery/recovery, Testcontainers suites and Kubernetes deployment are not implemented or validated in Phase 1. Approximately 589 MB disk headroom remained with the full stack running. A saved draft is not a published environment or proof of restoration in a new task.
