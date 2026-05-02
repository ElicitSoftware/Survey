# Elicit Survey Constitution

## Core Principles

### I. Specification-First (SDD) — NON-NEGOTIABLE

A feature specification MUST be written and approved by the team before any test or
production code is written. No implementation work begins without a ratified spec.

- All specs live in the `specs/` directory and follow the project spec template.
- A spec is considered approved when it has been explicitly reviewed and accepted —
  silent approval is not permitted.
- Changing scope mid-implementation MUST trigger a spec amendment, reviewed before
  code changes continue.
- The specification is the single source of truth; code that drifts from the spec is
  non-conforming — the spec is not wrong, the code is.

**Rationale**: Prevents wasted implementation effort and ensures all stakeholders share a
common understanding before any code is written.

---

### II. Test-First (TDD) — NON-NEGOTIABLE

Every piece of production code MUST have a failing test written before it is implemented.
The Red-Green-Refactor cycle is the only acceptable development sequence.

1. **Red** — Write a test that expresses the intended behavior and confirm it fails.
2. **Green** — Write the minimum production code to make the test pass.
3. **Refactor** — Improve structure and clarity while keeping all tests passing.

Rules:
- No production code may be committed without a corresponding test that was failing first.
- Tests MUST be reviewed as part of the same PR as the implementation they cover.
- Skipping or disabling a test requires a comment referencing the open spec issue that
  tracks its resolution.

**Applies to all five layers**:
- Service / business logic (JUnit 5, Mockito)
- REST / API layer (`@QuarkusTest` integration tests)
- Repository / data layer (DB integration tests with real Flyway migrations)
- UI / Vaadin views (Karibu-Testing)
- ETL / reporting (fact table population tests)

**Rationale**: Eliminates untestable designs, provides executable documentation, and
ensures regression safety at every layer from the first commit.

---

### III. Browserless UI Testing

All Vaadin UI views MUST be tested using **Karibu-Testing** — a mock Vaadin environment
that runs in the JVM without a browser or Selenium.

- Every Vaadin `View`, `Dialog`, and composite component MUST have a Karibu-Testing test
  covering its primary user flows and data bindings.
- Browser-based (Selenium, Playwright) tests are NOT permitted for unit-level or
  integration-level view testing.
- Karibu-Testing tests follow the same Red-Green-Refactor discipline as all other tests
  (Principle II).
- UI tests MUST assert on component state and bound data — not on CSS class names,
  pixel positions, or layout details.

**Rationale**: Browser tests are slow, brittle, and require running infrastructure.
Karibu-Testing runs entirely in the JVM, making UI tests as fast and reliable as unit
tests and keeping them compatible with the TDD cycle.

---

### IV. Full-Stack Coverage Gate — 70%

All production code MUST maintain a minimum **line coverage of 70%**, enforced in CI on
every PR.

- Coverage is measured across all five layers (see Principle II).
- CI MUST fail the build if line coverage drops below 70% on any PR targeting `main`.
- Coverage reports are produced by JaCoCo and attached to every CI run.
- Coverage is a floor, not a ceiling — 70% is the minimum, not the target. TDD discipline
  (Principle II) is the primary quality gate; coverage is a regression detector.
- Excluding **individual classes** from coverage measurement requires an explicit
  `@ExcludeFromJacocoReport` annotation with a comment explaining why.
- **Package-level exclusions** (e.g., framework-generated proxies matching `**/*$$*`,
  or packages containing only untestable infrastructure such as PDF generation or HTTP
  streaming) MAY instead be declared as JaCoCo XML `<excludes>` patterns in `pom.xml`,
  provided the exclusion is explicitly justified in the feature spec that introduces it.

**Rationale**: A coverage floor prevents silent regression in test coverage as the codebase
grows, without creating false confidence that high numbers alone indicate quality.

---

### V. Observability — State-Changing Operations

Every state-changing operation MUST produce a structured log entry and, where data
integrity is at stake, a durable audit record.

- All create, update, publish, and delete operations MUST emit a structured log event
  (JSON format) containing: operation name, entity type and ID, actor identity (OIDC
  subject claim), and ISO-8601 timestamp.
- Operations that change survey structure (publish, version, alter) MUST write an audit
  record to the database.
- All errors on state-changing paths MUST be logged with full context: operation name,
  entity, error message, and stack trace.
- No state-changing operation may fail silently. Swallowing exceptions on write paths is
  a constitution violation.

**Rationale**: Survey data integrity and respondent trust are critical. Observability
enables debugging, compliance demonstration, and post-hoc audit of all structural changes.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Application framework | Quarkus 3.x |
| UI framework | Vaadin 25 |
| Database | PostgreSQL with Flyway migrations |
| Authentication | OIDC |
| Unit / integration testing | JUnit 5, Mockito, `@QuarkusTest` |
| UI testing | Karibu-Testing |
| Coverage | JaCoCo (70% line coverage minimum) |

New runtime dependencies MUST be justified by a spec-driven requirement. No dependency
may be added solely for convenience.

## Development Workflow

Each feature follows this mandatory sequence — no step may be skipped:

1. **Spec** — Write and get the spec approved (Principle I). No code until this is done.
2. **Red** — Write a failing test that expresses the spec behavior (Principle II).
3. **Green** — Write the minimum code to make the test pass.
4. **Refactor** — Improve structure while keeping all tests green.
5. **PR** — Open a pull request. CI MUST pass (all tests green, coverage ≥ 70%, build
   succeeds). PRs MUST NOT be merged with a failing CI run. No reviewer override is
   permitted on CI failures.
6. **Review** — Every reviewer MUST verify constitution compliance as part of their review.

## Governance

- This constitution supersedes all other coding conventions, style guides, and informal
  team agreements in the Elicit Survey module.
- **Amendments** require: (a) a written proposal identifying the principle being changed
  and the rationale, (b) team review with recorded discussion, and (c) the updated
  constitution committed to the repository as the merge commit for the amendment PR.
- Removing or weakening a **NON-NEGOTIABLE** principle (I or II) requires unanimous team
  approval.
- Constitution compliance is every reviewer's responsibility on every PR — not a
  designated role.
- Versioning follows semantic versioning:
  - **MAJOR** — removal or incompatible redefinition of a principle.
  - **MINOR** — new principle or section added, or material expansion of guidance.
  - **PATCH** — clarifications, wording improvements, or non-semantic refinements.

**Version**: 1.0.0 | **Ratified**: 2026-05-01 | **Last Amended**: 2026-05-01
