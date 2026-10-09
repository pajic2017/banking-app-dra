# Phase 1 design and mentoring

## Responsibilities and boundaries

The root Gradle build centralizes supported dependency versions and Java 17; each application still has its own build file, configuration, main class and tests and can produce an independent jar. `common-contracts` is an empty Java library reserved for explicitly versioned API/event contracts. It contains no persistence classes.

Eureka owns service registrations, not business state. `@EnableEurekaServer` activates the registry. Clients register using `spring.application.name`; the gateway uses logical `lb://` destinations instead of hard-coded backend addresses. Registrations are eventually visible, so the live smoke test polls until routing and registry contents agree.

Config Server uses `@EnableConfigServer` and the native filesystem backend. Files in `infrastructure/config` are versioned in the application repository, avoiding a second Git repository or credentials during the foundation phase. A mounted read-only directory is used in Compose. Application configuration imports are required: client startup fails if Config Server is unavailable rather than silently accepting an unconfigured environment. Local defaults can be overridden with CONFIG_SERVER_URL, EUREKA_URL, CONFIG_REPO and PORT. Config Server and Eureka are independent of config-client imports to avoid a bootstrapping cycle. Configuration refresh is not automatic: restart clients after editing central configuration in this phase.

The gateway uses the current Spring Cloud Gateway WebFlux starter and `spring.cloud.gateway.server.webflux` configuration namespace. It is reactive and has no servlet starter. Explicit routes prevent accidental exposure of every discovered service. Health/info are the only globally exposed actuator endpoint categories; admin, refresh, environment and shutdown endpoints are not exposed.

`@SpringBootApplication` combines auto-configuration, component scanning and Spring configuration. `@RestController` serializes probe maps to JSON. `@GetMapping` maps one infrastructure endpoint; later phases replace this scaffold with validated DTOs and business APIs. `@WebMvcTest` loads the MVC slice and `MockMvc` checks actual HTTP mapping/serialization. `@SpringBootTest` starts the infrastructure applications on random ports and verifies real HTTP health responses. Configuration/discovery client networking is disabled within isolated tests, not in the running application. The independent live test exercises those integrations.

## Database plan

There are no schemas or migrations in Phase 1: generating unused financial tables now would hide decisions that belong to later phases. Compose provisions empty, isolated Customer/Account MySQL and Transaction/Auth PostgreSQL databases and Notification MongoDB. Flyway migrations start in Phase 2. Relational foreign keys stay within a service's database; customer references in Account Service are verified through APIs and have no cross-database foreign key.

Account Service will own the authoritative balances and double-entry ledger. For same-bank, same-currency transfers both account updates and balanced ledger entries will occur in one Account database transaction with deterministic lock ordering and a unique operation ID. Transaction Service will own durable request/orchestration state and retry the same operation ID after ambiguous network failures. Durable idempotency records and an outbox must be committed alongside financial changes. This design deliberately keeps the money invariant inside a single database transaction; a REST response is not a distributed atomic commit. Transaction projections and notifications can lag and require reconciliation. Cross-bank or multi-ledger movement requires a separately designed reservation/compensation saga; it is not implemented or claimed here.

## Files

* Root build/settings, Gradle wrapper: reproducible versioned build.
* Per-service `src/main` and `src/test`: independent application and foundational tests.
* `infrastructure/config`: common properties and gateway route table.
* `infrastructure/docker/Dockerfile`: Java 17, non-root runtime using built jars.
* `docker-compose.yml`: applications, isolated persistent databases, Kafka KRaft, imported Keycloak realm and optional monitoring profile.
* `scripts`: secure local credential creation, Java startup/shutdown and live smoke checks.
* `.github/workflows/build.yml`: Java 17 build/tests plus Compose configuration validation. It does not claim a deployed environment or container integration tests.

## Verification and next phase

Use the exact commands in README. A successful jar build alone does not establish discovery or routing; run the live smoke test. Compose `--wait` separately checks infrastructure readiness. Docker data lives in named volumes, while processes must restart after environment restoration.

Phase 2 will add Customer entity/DTO/repository/service/controller, Flyway constraints/indexes, validation, pagination, logical deactivation, exception mapping, OpenAPI and Testcontainers tests. No financial transaction, Kafka delivery guarantee or customer authentication is demonstrated by Phase 1.
