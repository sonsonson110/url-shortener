# 0001. Project Foundation: Technology Stack and Repository Layout

**Status:** Accepted  
**Date:** 2026-11-14

## Context

Starting a new Java/Spring project from scratch. The owner has React/Node.js background and is new to Java. The project serves dual purpose: a learning vehicle and a job-hunting portfolio. We need a foundation that:

- Is fast to get running locally without fighting tooling
- Supports honest benchmarking with real infrastructure (no mocks)
- Makes observability a first-class concern from day one
- Keeps the stack boring and industry-standard so cognitive load stays on system design

## Options Considered

### 1. Spring Boot 3 + Maven (chosen)
**Pros:** Industry standard; excellent ecosystem; Micrometer built in; Spring Actuator gives `/actuator/prometheus` for free; Maven wrapper means no install required.  
**Cons:** Slower startup than Quarkus/Micronaut; more verbose than Node. Acceptable because startup time is not a portfolio bottleneck, and Java familiarity is the goal.

### 2. Spring Boot 3 + Gradle
**Pros:** Faster incremental builds; Kotlin DSL more concise.  
**Cons:** One more thing to learn when Java itself is new. Maven XML is verbose but transparent.

### 3. Quarkus or Micronaut
**Pros:** Faster startup; native image possible.  
**Cons:** Smaller community; less employer recognition; unnecessary complexity for the learning goal.

## Decision

Spring Boot 3.4 with Maven 3.9.9 (via Maven wrapper). Java 21 LTS.

HikariCP is Spring Boot's default pool — no extra config needed beyond sizing. Micrometer + Prometheus + Grafana is the standard observability stack. Flyway for migrations (never Hibernate DDL generation in production). Testcontainers for real-database integration tests.

## Consequences

- Every integration test needs Docker running. That is acceptable and enforces honest testing.
- Hibernate `ddl-auto: validate` means schema must stay in sync with entities; Flyway is the single source of truth for schema.
- `spring.jpa.open-in-view=false` is set explicitly to avoid the N+1 antipattern that the OSIV session enables silently.
- Maven wrapper (`mvnw`) downloads Maven on first use. Build will be slow on first run only.

## Evidence

- Project builds and all tests pass: TBD (run `make test` after seeding)
- Stack starts with `make up`: TBD
- Grafana latency dashboard loads: TBD
