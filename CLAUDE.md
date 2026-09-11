# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Envision Ad is a two-sided B2B marketplace connecting media owners (operators of physical/digital ad screens) with advertisers (small businesses): advertisers manage ad campaigns and subscribe to bundles of screens; media owners manage their screens (media) and earn revenue from ads shown on them. Built for Visual Impact (the client company; Envision Ad is their product). The repo contains two applications: a Next.js frontend and a Spring Boot backend, orchestrated via Docker Compose with Doppler for secrets management.

## Commands

### Frontend (`cd frontend/`)
- **Dev server:** `npm run dev`
- **Build:** `npm run build`
- **Lint:** `npm run lint` (ESLint 9 flat config)
- **Architecture lint:** `npm run lint:fsd` (steiger, Feature-Sliced Design rules — runs in CI; must report no problems)
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
- **Local dev Postgres runs natively on the host**, not in Compose — webservice containers reach it via `SPRING_DATASOURCE_URL` → `host.docker.internal:5432`. Query it with `psql -h localhost -p 5432 -U envision_admin -d envision_ad_db`.

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
├── widgets/      → Composite UI used by several pages or the app shell (app-navigation, footer, media-carousel, media-details)
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
- Local-only sample data: `src/main/resources/db/seed/R__seed_dev_data.sql`, a Flyway *repeatable* migration (idempotent via sentinel guards), loaded only under the `local` profile — `application-local.yml` adds `classpath:db/seed` to `spring.flyway.locations`; never add it to a non-local profile. Scope is supply-side only (venue/media_location/media/bundles); no fabricated business/employee rows, because `employee.user_id` is the raw Auth0 JWT `sub` and a fake value makes seeded data invisible to whoever's logged in. Sole exception: one real teammate's business/employee record, keyed to their actual Auth0 sub and guarded on the UNIQUE `employee.user_id`.
- Tests run against a real PostgreSQL 15 database via Testcontainers (`config/TestcontainersConfig.java`, wired in through `@ServiceConnection`); integration tests extend `config/BaseIntegrationTest`. Test profile disables Flyway and uses `ddl-auto: create`, so the test schema is generated from the JPA entities. Requires a running Docker daemon.
- JaCoCo enforces 90% code coverage at build time

### Infrastructure

- **Secrets:** Doppler. Projects `envision-ad-frontend` and `envision-ad-backend`, but **`envision-ad-backend` is the source of truth for both apps** — it holds the frontend's keys too, and `docker-compose`/`deploy.yml` nest the calls as `doppler run <frontend> -- doppler run <backend> -- …`, so the inner (backend) call overwrites every conflicting key (the frontend project's `AUTH0_AUDIENCE` and Cloudinary copies are dead). Put new secrets for either app in `envision-ad-backend`; configs are `dev`, `dev_personal`, `stg`, `prd`.
- **Local email:** the `local` profile never sends real mail. `application-local.yml` points Spring Mail at **Mailpit** — SMTP on `:1025`, web inbox at http://localhost:8025 — the `mailpit` service in both dev compose files, or `localhost` for a host-native `bootRun`. Doppler's `EMAIL_*` values are read only by non-local profiles. Don't point local at a real SMTP server: dev data includes real people's Auth0 accounts, so a test that emails media owners would reach actual client staff.
- **Production:** Docker Compose on EC2, PostgreSQL on RDS, Nginx reverse proxy with Let's Encrypt SSL
- **Routing (prod):** Nginx forwards `/api/*` to webservice:8080, everything else to frontend:3000
- **CI/CD:** GitHub Actions — lint, build, JaCoCo coverage, auto-deploy on push to `main` (images build in Actions → GHCR; EC2 pulls). **Playwright e2e is not a gate** — it runs on manual `workflow_dispatch` only (CI runner has no backend/Postgres) and has never blocked a deploy.

## Coding constraints (must follow)

Hard rules for any change in this repo. When a rule conflicts with a request, follow the rule and flag it.

1. **Match the existing layering exactly.**
   - Backend: 3-tier per-domain module — `presentationlayer/` (controllers + DTOs) → `businesslogiclayer/` (services) → `dataaccesslayer/` (JPA entities + repositories), with MapStruct mappers in `mappinglayer/`. A new domain gets its own top-level package under `com.envisionad.webservice`, mirroring `venue/`.
   - Frontend: Feature-Sliced Design — respect the layer dependency order, and expose every slice only through its `index.ts` public API. API calls are one file per endpoint, mirroring `features/venue-management/api/*`. `npm run lint:fsd` (steiger's recommended config, no overrides) enforces this in CI. The specifics it doesn't spell out:
     - Code with a single consumer lives inside that consumer's page (`pages/<x>/api|model|ui|lib`) until a second consumer exists; only then does it become a feature, entity or widget.
     - An entity imports another entity only through the target's `@x/<consumer>.ts` cross-import API (e.g. `entities/venue/@x/media.ts`), never its `index.ts`.
     - Server-only code (anything reaching `@auth0/nextjs-auth0/server` or the Auth0 Management API) is exported from a separate `index.server.ts`, never from the client `index.ts` — see `shared/api/index.server.ts`. Server routes import the same barrels as client components, so a client-only hook or component exported through any `index.ts` must carry `"use client"`.
     - Route files under the root `app/` stay thin: they import only slice public APIs, and layout composition lives in `src/app/layouts`.

2. **No new dependencies without asking.** Use the established stack only — backend: Spring Boot, Lombok, MapStruct, Stripe Java SDK, Flyway; frontend: Mantine + Tabler Icons, next-intl, axios, Stripe Elements (`@stripe/react-stripe-js`), React Leaflet, Auth0. Do not add a library, or a new major version of one, without proposing it first and getting a yes.

3. **Implement pinned contracts verbatim.** When a brief or existing code specifies an interface, method signature, DTO shape, or API contract, reproduce it exactly — do not rename, reorder parameters, change types, or "improve" it. Other modules and future projects depend on these signatures. If a pinned contract looks wrong, raise it; do not silently change it.

4. **Tests are part of "done" and use the existing harness.** 90% JaCoCo coverage is enforced by `./gradlew check`; write tests alongside each change, not in a final pass. Backend integration tests **extend `config/BaseIntegrationTest`** — do NOT redeclare `@SpringBootTest`, the `JwtDecoder`/`EmailService` mocks, or `WebTestClient`, and do NOT add new `@MockitoBean` fields (each unique combination forks a separate Spring context and slows the build; prefer real repository data). Tests require a running Docker daemon (Testcontainers Postgres).

5. **Money handling.** All monetary math uses `BigDecimal` with `HALF_UP` rounding to 2 decimal places. Never trust a client-supplied price or amount — always recompute server-side at the point of purchase. The platform fee comes from `stripe.platform-fee-percent` config, never a hardcoded literal.

6. **Ask rather than invent.** Match existing patterns first. When neither the brief nor the repo answers a question — a naming choice, an edge case, an unspecified behavior — stop and ask instead of guessing. A clarifying question is always cheaper than a wrong implementation built on an assumption.
