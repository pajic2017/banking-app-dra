# Phase 2 verification — 2026-10-09

Validated in `/workspace/banking-app-dra` on branch `feat/phase-2-customer-service`, with Java 17 and real MySQL 8.4.

| Check | Result |
|---|---|
| Root Gradle build | Passed |
| Customer tests | 22 passed: 17 full HTTP/MySQL integration cases, four Mockito unit tests and one foundation MVC test; zero failures/errors/skips |
| Other Java tests | Seven unchanged foundation tests retain passing Gradle results, for 29 total |
| Affected checks after Location fix | All Customer tests rerun and passed |
| Saved installation steps repeated | Root build/config validation passed; 58 tasks up to date |
| Host-Java startup | Eight health endpoints, seven Eureka registrations, five foundation routes and shared configuration checked |
| Host-Java Customer smoke | Gateway create/get/update/paginate/deactivate, uniqueness/validation and correctly resolved relative Location passed |
| Postman/Newman | Ten requests, thirteen assertions, zero failures/pending |
| Customer Docker image | Built successfully using non-root Java 17 runtime |
| Phase 2 Compose target | Five healthy containers: Eureka, Config Server, Gateway, Customer Service, Customer MySQL |
| Compose Customer smoke | Full development lifecycle through Gateway passed |
| Durable restart check | Previously inactive customer and timestamps identical before/after MySQL plus application restart |
| Flyway restart state | Exactly one successful V1 history record; schema accepted by Hibernate validation |
| API docs | OpenAPI paths and Swagger UI HTML retrieved successfully |
| Repository checks | Diff whitespace and shell syntax passed; credentials/build outputs remain ignored |

The first packaged gateway check exposed an internal-service Location URL. The controller now returns `customers/{id}` as a relative Location; the smoke test resolves it against the collection URL to validate the public gateway path. Integration expectations and affected tests were updated and rerun. An initial regex escaping compile error was corrected before any successful test claims.

The Postman runner was Newman 6.2.1; the desktop Postman UI was not used. Smoke and Postman runs retain inactive synthetic customers in the local development database; generated IDs can have gaps after rejected duplicate inserts. No user data or volumes were erased.

The full fifteen-container stack was verified during Phase 1, not rerun as a whole for Phase 2; the changed Customer flow and deployment were tested in the five-container target and the eight-application host workflow. Optional monitoring, account integration, financial processing, JWT/ownership enforcement, Kafka delivery and Kubernetes remain later phases. The five-container target left approximately 7.9 GB free on this Docker VFS cloud machine.
