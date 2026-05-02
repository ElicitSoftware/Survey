# Implementation Plan: Achieve 80% Test Coverage

**Branch**: `initial_spec_testing` | **Date**: 2026-05-01 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-80-percent-test-coverage/spec.md`

## Summary

Bring the Elicit Survey application from its current ~13 active test methods to a verified
80% instruction-coverage minimum enforced on every build. The work is divided into four
phases: (1) JaCoCo tooling configuration, (2) CDI business-logic service tests, (3)
Panache data-layer tests, and (4) Vaadin UI tests using Karibu-Testing. TestContainers
replaces the hard-coded external PostgreSQL dependency, making the suite self-contained.
The `report/` package (`PDFService`, `ReportService`, `PDFDownloadResource`) and Vaadin
view classes are excluded from the coverage threshold. The `ElcitDatePicker` typo is
corrected to `ElicitDatePicker` as part of this feature. SC-006 (≥ 80 `@Test` methods)
is enforced automatically via a Maven Enforcer rule or CI script.

## Technical Context

**Language/Version**: Java 25 (`maven.compiler.release=25`)
**Primary Dependencies**: Quarkus 3.34.1, Vaadin 25.1.1, Hibernate ORM with Panache, Flyway
**Storage**: PostgreSQL 16 (production); PostgreSQL via TestContainers (tests)
**Testing**:
  - Existing: JUnit 5 (via `quarkus-junit`), JaCoCo (via `quarkus-jacoco`)
  - To add: `jacoco-maven-plugin` 0.8.14 (report + check goals), `org.testcontainers:postgresql`, `quarkus-junit5-mockito`, `quarkus-junit-component`, Karibu-Testing 2.7.0
**Target Platform**: Linux/macOS server (Quarkus fat-jar / Docker image); CI runner with Docker available (GitHub Actions / GitLab)
**Project Type**: Full-stack web application (Vaadin frontend + Quarkus CDI backend)
**Performance Goals**: Full test suite completes in ≤ 10 minutes on developer hardware
**Constraints**: Tests must be self-contained — no pre-existing external database required; Docker available in CI (no Docker-free fallback needed)
**Scale/Scope**: ~50 production classes; target ≥ 80 active test methods (from 13); 4 tech-layer phases

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I — Spec-First | ✅ PASS | Spec written and reviewed before any code changes |
| II — TDD (Red-Green-Refactor) | ✅ PASS | All new tests written before the production-code changes they enable |
| III — Karibu-Testing for Vaadin | ✅ PASS | FR-020 mandates Karibu-Testing for all Vaadin component and view tests |
| IV — 70% line coverage gate | ✅ PASS (raised) | Spec targets 80% *instruction* coverage — exceeds constitutional 70% line minimum |
| V — Observability on state-changing ops | ✅ N/A | This feature adds tests only; no new state-changing production paths introduced |

**Pre-existing deviation (not introduced here)**: Constitution specifies Java 21; project compiles at Java 25. A separate spec amendment is required to ratify this.

**Coverage metric alignment**: Constitution says "line coverage"; JaCoCo threshold configured at "instruction" level (JaCoCo default, stricter than line). 80% instruction satisfies the constitutional 70% line floor.

**Post-design re-check**: All principles still pass. The `report/` exclusion (clarification Q5) and `ElcitDatePicker` rename (clarification Q3) do not introduce any constitution violations.

## Project Structure

### Documentation (this feature)

```text
specs/001-80-percent-test-coverage/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
├── contracts/           ← Phase 1 output (N/A — no public API surface)
└── tasks.md             ← Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
pom.xml                         ← Add jacoco-maven-plugin 0.8.14, karibu-testing 2.7.0,
                                    testcontainers:postgresql, quarkus-junit5-mockito,
                                    quarkus-junit-component; Maven Enforcer @Test count gate

src/
├── main/
│   └── java/com/elicitsoftware/
│       ├── *.java              ← No changes to production code (except rename below)
│       ├── flow/
│       │   └── input/
│       │       ├── ElcitDatePicker.java  ← RENAME → ElicitDatePicker.java (FR-021)
│       │       └── [14 other components — no changes]
│       ├── model/              ← 18 Panache entities (data-layer tests)
│       ├── etl/                ← ETL services (service-layer tests)
│       └── report/             ← EXCLUDED from coverage threshold (PDFService, ReportService,
│                                   PDFDownloadResource — PDF generation, HTTP streaming)
└── test/
    └── java/com/elicitsoftware/
        ├── PostgresTestResource.java     ← NEW: QuarkusTestResourceLifecycleManager (GLOBAL)
        ├── survey/             ← Existing tests — add @WithTestResource, expand
        │   ├── TokenServiceTest.java     (expand — deactivation, invalid-token paths)
        │   ├── QuestionServiceTest.java  (rewrite with @InjectMock + @MockitoConfig)
        │   ├── RandomStringGeneratorTest.java (expand edge cases)
        │   └── RespondentTest.java       (expand — active/inactive state)
        ├── DisplayKeyTest.java           ← NEW
        ├── service/            ← NEW: Phase 2 CDI service tests
        │   ├── UITestProfile.java        (QuarkusTestProfile placeholder)
        │   ├── QuestionManagerTest.java
        │   ├── SessionPersistenceServiceTest.java
        │   ├── ETLServiceTest.java
        │   ├── DatabaseHealthCheckTest.java
        │   └── BrandUtilTest.java
        ├── model/              ← NEW: Phase 3 data-layer tests
        │   ├── FlywayMigrationTest.java
        │   ├── RespondentEntityTest.java
        │   ├── AnswerEntityTest.java
        │   ├── SurveyEntityTest.java
        │   ├── QuestionEntityTest.java
        │   ├── SectionEntityTest.java
        │   └── SelectGroupEntityTest.java
        └── flow/               ← NEW: Phase 4 Vaadin UI tests (Karibu-Testing)
            ├── input/
            │   ├── ElicitTextFieldTest.java
            │   ├── ElicitDatePickerTest.java   ← uses renamed ElicitDatePicker
            │   └── ... (13 more component tests)
            └── views/
                ├── MainViewTest.java
                ├── SectionViewTest.java
                ├── ReviewViewTest.java
                ├── ReportViewTest.java
                ├── AboutViewTest.java
                ├── LogoutViewTest.java
                └── VersionViewTest.java
```

**JaCoCo exclusion patterns (updated after clarifications)**:
- `com/elicitsoftware/flow/*View` — Vaadin route views
- `com/elicitsoftware/flow/*Layout` — Vaadin layout classes
- `com/elicitsoftware/report/*` — PDF generation and HTTP streaming (Q5 clarification)
- `**/*$$*` — Quarkus/Arc runtime-generated proxy classes

**Structure Decision**: Single-project Maven layout. Tests mirror the production package
structure under `src/test/java`. New test subpackages (`service/`, `model/`, `flow/`)
keep phase work separable and independently reviewable. No new production source
directories introduced (only the `ElicitDatePicker` rename in existing `flow/input/`).

## Complexity Tracking

> No constitution violations requiring justification for this feature.
