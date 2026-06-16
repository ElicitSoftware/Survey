# Elicit Survey — Claude / AIUP Context

This project follows the **AI Unified Process (AIUP)**
([unifiedprocess.ai](https://unifiedprocess.ai/)). Treat the artifacts under
`docs/` as the source of truth for *what* this system is supposed to do; the
code under `src/` is the implementation that AIUP regenerates and refactors
around them.

## Stack

- Java 25, Quarkus 3.34.x, Maven build
- Vaadin 25.1.x (Flow, server-side UI)
- Hibernate ORM with Panache (JPA) — **not jOOQ**
- PostgreSQL
- Docker for deployment
- OpenTelemetry (see `guides/OPENTELEMETRY_SETUP.md`, `guides/METRICS_GUIDE.md`)

When using the Quarkus Agent MCP tools, this is an **existing project** — start
with `quarkus_update` and `quarkus_skills` per those tools' instructions.

## AIUP Workflow

The plugin `aiup-core` provides these methodology skills. Run them roughly in
this order; review and hand-edit each artifact before continuing.

1. `/requirements` — generates `docs/requirements.md` from `docs/vision.md`.
2. `/entity-model` — produces a Mermaid ER diagram and attribute tables.
3. `/use-case-diagram` — creates a PlantUML diagram with stable UC IDs.
4. `/use-case-spec UC-XXX` — writes detailed per-use-case specifications.
5. *(implementation + tests — stack-specific; we are not using `aiup-vaadin-jooq`
   because this project uses Hibernate/Panache, not jOOQ.)*

**Brownfield entry point:** `/reverse-engineer` recovers AIUP artifacts from
the existing source. Run it before the forward workflow on this repo, since
the codebase predates AIUP adoption.

## Working Agreements

- Re-run upstream skills when requirements change so downstream artifacts
  (entity model, use-case specs) stay consistent.
- Tests must be traceable to a use case — reference the `UC-XXX` ID in test
  names or comments.
- The `docs/` folder is institutional memory. Commit it to version control.
- Do not mix in jOOQ-specific patterns; data access is Hibernate/Panache.
- Do not regenerate code that bypasses Vaadin component APIs (accessibility).

## Reference

- Vision: `docs/vision.md`
- Reference deployment: [FHHS](https://github.com/ElicitSoftware/FHHS)
- Marketplace: https://github.com/AI-Unified-Process/marketplace
