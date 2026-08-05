# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Envision Ad is a B2B marketplace platform connecting media owners (who have physical/digital ad spaces) with advertisers (small businesses). Built for Visual Impact. The repo contains two applications: a Next.js frontend and a Spring Boot backend, orchestrated via Docker Compose with Doppler for secrets management.

## Commands

### Frontend (`cd frontend/`)
- **Dev server:** `npm run dev`
- **Build:** `npm run build`
- **Lint:** `npm run lint` (ESLint 9 flat config)
- **Architecture lint:** `npx steiger src/` (Feature-Sliced Design rules)
- **E2E tests:** `npx playwright test` / `npx playwright test path/to/test.spec.ts`

### Backend (`cd webservice/`)
- **Run tests:** `./gradlew test`
- **Run single test:** `./gradlew test --tests "com.envisionad.webservice.ClassName.methodName"`
- **Build:** `./gradlew build`
- **Code coverage report:** `./gradlew jacocoTestReport` (output in `build/reports/jacoco/`)
- **Coverage verification:** `./gradlew check` (enforces 90% minimum coverage)

### Full Stack (Docker)
- **Start all services:** `doppler run -- docker-compose up` (requires Doppler CLI configured)
- **Backend only (no frontend):** `docker-compose -f docker-compose-no-frontend.yml up`
- **Container runtime is OrbStack**, not Docker Desktop — same `docker`/`docker-compose` CLI, same commands above.
- **Local dev Postgres runs natively on the host.** Neither `docker-compose.yml` nor `docker-compose-no-frontend.yml` defines a `postgres` service — both webservice containers reach it via `SPRING_DATASOURCE_URL` pointed at `host.docker.internal:5432`. Query the real dev DB via `psql -h localhost -p 5432 -U envision_admin -d envision_ad_db`.

## Knowledge Graph

A pre-built knowledge graph of this codebase lives at `../graphify-out/graph.json` (3,650 nodes, 9,339 edges). Before grepping or reading multiple files to understand architecture, data flows, or how components connect, query the graph first:

```
graphify query "how does X work"
graphify path "ServiceA" "ServiceB"
graphify explain "ClassName"
```

Use `Read`/grep only for precise line lookups. Run `/graphify envision-ad --update` after significant code changes to keep the graph fresh.

## Architecture

### Frontend (Next.js 16 / TypeScript / Mantine 8)

Uses **Feature-Sliced Design (FSD)** — layers are strictly ordered by dependency:

```
src/
├── app/          → Next.js App Router, global providers
├── pages/        → Page-level components (route compositions)
├── features/     → Business logic scoped to a feature (auth, payment, media-management, etc.)
├── entities/     → Domain models and stores (organization, media, reservation, ad-campaign)
├── widgets/      → Reusable composite UI (Header, SideBar, Cards, Map, Carousel)
├── shared/       → Generic utilities, API config (axios), UI kit, types, i18n
```

**Key conventions:**
- Path aliases: `@/app`, `@/pages`, `@/widgets`, `@/features`, `@/entities`, `@/shared`
- Each layer exports through `index.ts` public API files
- Auth: Auth0 via `@auth0/nextjs-auth0`
- i18n: `next-intl` with messages in `src/messages/`
- UI components: Mantine core + Tabler Icons
- Maps: React Leaflet + Leaflet Geosearch
- Payments: Stripe Elements (`@stripe/react-stripe-js`)

### Backend (Spring Boot 3.5 / Java 21 / Gradle)

Layered 3-tier architecture, organized by domain module:

```
src/main/java/com/envisionad/webservice/
├── config/          → Security (Auth0/Okta), Stripe, CORS, global exception handling
├── business/        → Organization/Business entity
├── advertisement/   → Ad entity
├── media/           → Media/ad-space entity
├── reservation/     → Reservation entity
├── payment/         → Stripe Connect integration
├── proofofdisplay/  → Proof-of-display entity
└── utils/           → Shared utilities
```

Each domain module follows the same internal structure:
- `presentationlayer/` — REST controllers + request/response DTOs
- `businesslogiclayer/` — Services with business rules
- `dataaccesslayer/` — JPA repositories + entity models
- `mappinglayer/` (or `datamapperlayer/`) — MapStruct DTO<->Entity mappers

**Key conventions:**
- Lombok for boilerplate reduction
- MapStruct for object mapping (annotation processor with Lombok binding)
- PostgreSQL 15 with JSONB support (hibernate-types-60)
- Spring profiles: `local` (host-native Postgres, reached via `host.docker.internal:5432` — see the OrbStack/Postgres note above), `prod` (AWS RDS)
- Schema managed via Flyway migrations in `src/main/resources/db/migration/` (`ddl-auto: none`, `baseline-on-migrate: true`). There is no `schema.sql`.
- Tests run against a real PostgreSQL 15 database via Testcontainers (`config/TestcontainersConfig.java`, wired in through `@ServiceConnection`); integration tests extend `config/BaseIntegrationTest`. Test profile disables Flyway and uses `ddl-auto: create`, so the test schema is generated from the JPA entities. Requires a running Docker daemon.
- JaCoCo enforces 90% code coverage at build time

### Infrastructure

- **Secrets:** Doppler (projects: `envision-ad-frontend`, `envision-ad-backend`; configs: `dev`, `prd`)
- **Production:** Docker Compose on EC2, PostgreSQL on RDS, Nginx reverse proxy with Let's Encrypt SSL
- **Routing (prod):** Nginx forwards `/api/*` to webservice:8080, everything else to frontend:3000
- **CI/CD:** GitHub Actions — lint, build, Playwright e2e, JaCoCo coverage, auto-deploy on push to `main`

## Coding constraints (must follow)

Hard rules for any change in this repo. When a rule conflicts with a request, follow the rule and flag it.

1. **Match the existing layering exactly.**
   - Backend: 3-tier per-domain module — `presentationlayer/` (controllers + DTOs) → `businesslogiclayer/` (services) → `dataaccesslayer/` (JPA entities + repositories), with MapStruct mappers in `mappinglayer/`. A new domain gets its own top-level package under `com.envisionad.webservice`, mirroring `venue/`.
   - Frontend: Feature-Sliced Design — respect the layer dependency order, and expose every slice only through its `index.ts` public API. API calls are one file per endpoint, mirroring `features/venue-management/api/*`.

2. **No new dependencies without asking.** Use the established stack only — backend: Spring Boot, Lombok, MapStruct, Stripe Java SDK, Flyway; frontend: Mantine + Tabler Icons, next-intl, axios, Stripe Elements (`@stripe/react-stripe-js`), React Leaflet, Auth0. Do not add a library, or a new major version of one, without proposing it first and getting a yes.

3. **Implement pinned contracts verbatim.** When a brief or existing code specifies an interface, method signature, DTO shape, or API contract, reproduce it exactly — do not rename, reorder parameters, change types, or "improve" it. Other modules and future projects depend on these signatures. If a pinned contract looks wrong, raise it; do not silently change it.

4. **Tests are part of "done" and use the existing harness.** 90% JaCoCo coverage is enforced by `./gradlew check`; write tests alongside each change, not in a final pass. Backend integration tests **extend `config/BaseIntegrationTest`** — do NOT redeclare `@SpringBootTest`, the `JwtDecoder`/`EmailService` mocks, or `WebTestClient`, and do NOT add new `@MockitoBean` fields (each unique combination forks a separate Spring context and slows the build; prefer real repository data). Tests require a running Docker daemon (Testcontainers Postgres).

5. **Money handling.** All monetary math uses `BigDecimal` with `HALF_UP` rounding to 2 decimal places. Never trust a client-supplied price or amount — always recompute server-side at the point of purchase. The platform fee comes from `stripe.platform-fee-percent` config, never a hardcoded literal.

6. **Ask rather than invent.** Match existing patterns first. When neither the brief nor the repo answers a question — a naming choice, an edge case, an unspecified behavior — stop and ask instead of guessing. A clarifying question is always cheaper than a wrong implementation built on an assumption.
