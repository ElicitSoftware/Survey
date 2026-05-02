# Feature Specification: Achieve 80% Test Coverage

**Feature Branch**: `initial_spec_testing`
**Created**: 2026-05-01
**Status**: Draft
**Input**: User description: "The goal of this spec is to bring testing up to the 80% standard. This may need to be broken down into multiple steps. If that is the case break it down into sections based on technology. EJB vs Vaadin, etc. Use JaCoCo if it isn't installed already."

---

## Overview

This feature covers all work required to bring the Elicit Survey application's automated test coverage to a verified 80% minimum. The effort is divided into phases by technology layer because different layers present distinct testing challenges, tooling requirements, and risk profiles. JaCoCo is already present on the test classpath via `quarkus-jacoco`; this spec adds explicit report generation, an enforced minimum threshold, and the tests themselves.

The four phases are:

| Phase | Layer | Priority |
|-------|-------|----------|
| 1 | JaCoCo Configuration & Baseline | P1 |
| 2 | CDI Business-Logic Services | P1 |
| 3 | Data Layer (Panache Entities & Repositories) | P2 |
| 4 | Vaadin UI (Views & Custom Input Components) | P3 |

---

## Clarifications

### Session 2026-05-01

- Q: Will `mvn verify` run in a CI pipeline with Docker available? → A: CI pipeline planned; Docker available on the runner (GitHub Actions / GitLab)
- Q: Should `PDFService` have dedicated tests as part of this feature? → A: No — exclude `PDFService`; remove from US2 description
- Q: Should the `ElcitDatePicker` typo be fixed as part of this feature? → A: Yes — rename `ElcitDatePicker.java` → `ElicitDatePicker.java` in production and update all references
- Q: How should SC-006 (≥ 80 active `@Test` methods) be verified? → A: Automated — `exec-maven-plugin` `verify`-phase execution runs `grep -rE '@Test' src/test/java | grep -v '^\ *//' | wc -l` and fails the build if count < 80
- Q: Should `ReportService` and `PDFDownloadResource` be tested or excluded from threshold? → A: Exclude the entire `com/elicitsoftware/report/*` package from the JaCoCo threshold (same treatment as Vaadin views)

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — JaCoCo Baseline and Enforcement (Priority: P1)

A developer can run a single build command and receive a coverage report that fails the build if overall instruction coverage falls below 80%.

**Why this priority**: Without measurable coverage output and an enforced gate, all other test work cannot be verified to meet the 80% target. This must be in place before any new tests are written.

**Independent Test**: Run `mvn verify` on the project as-is; the build produces a JaCoCo HTML/XML report under `target/jacoco-report/` and the build fails with a clear coverage threshold violation message (because coverage is currently below 80%).

**Acceptance Scenarios**:

1. **Given** the project is built with `mvn verify`, **When** overall instruction coverage is below 80%, **Then** the build fails with a coverage threshold error citing the actual vs. required percentage.
2. **Given** the project is built with `mvn verify`, **When** overall instruction coverage is at or above 80%, **Then** the build passes and a coverage report is produced at a well-known location.
3. **Given** a developer opens the JaCoCo HTML report, **When** they navigate to any class, **Then** they see line-by-line coverage highlighting.
4. **Given** Vaadin UI classes are out of scope for unit-level coverage measurement, **When** the coverage threshold is evaluated, **Then** Vaadin view classes and generated frontend code are excluded from the threshold calculation.

---

### User Story 2 — CDI Business-Logic Service Tests (Priority: P1)

A developer can run tests that exercise all major code paths in the core business-logic services (`TokenService`, `QuestionService`, `QuestionManager`, `SessionPersistenceService`, `ETLService`, `BrandUtil`, `DatabaseHealthCheck`) and see those classes fully represented in the coverage report.

**Why this priority**: These services contain the majority of the application's business rules and currently have almost no test coverage. They are the highest-value area for reaching 80%.

**Independent Test**: Running `mvn test` produces a coverage report showing the CDI service layer at ≥ 80% instruction coverage. This can be evaluated before Phase 3 (data layer) or Phase 4 (Vaadin) tests exist.

**Acceptance Scenarios**:

1. **Given** a test database is available, **When** `TokenService` tests run, **Then** token generation, respondent login, and deactivation paths are all exercised and covered.
2. **Given** a test database is available, **When** `QuestionService` tests run, **Then** survey navigation, answer save, and finalization paths are all exercised and covered.
3. **Given** `@NormalUIScoped` beans cannot be injected in the standard test context, **When** tests for `QuestionService` and `QuestionManager` are written, **Then** they use an approved workaround (e.g., a dedicated test profile or mocking strategy) that enables injection without a real Vaadin UI context.
4. **Given** `ETLService` tests run, **When** ETL orchestration logic executes, **Then** all major ETL paths (success, partial failure, retry) are covered.
5. **Given** `DatabaseHealthCheck` tests run, **When** the health probe is invoked, **Then** both healthy and degraded database states are exercised.

**Edge Cases**:

- What happens when `TokenService` receives an already-deactivated token?
- What happens when `QuestionService` attempts to save an answer while the database is unavailable?
- What happens when `ETLService` receives malformed respondent data?

---

### User Story 3 — Data Layer Tests (Priority: P2)

A developer can run tests that exercise Panache entity queries, finders, and relationships so that the data access layer is verified independently of UI.

**Why this priority**: The data layer underpins every service. Panache entity active-record methods and named queries are a common source of runtime bugs that unit tests cannot catch.

**Independent Test**: Running `mvn test` produces a coverage report showing the `model` package at ≥ 75% instruction coverage. Tests can run against an isolated test database.

**Acceptance Scenarios**:

1. **Given** a test database is seeded with known data, **When** entity finder methods are called (e.g., `Respondent.findByToken()`, `Survey.listAll()`), **Then** they return the expected records.
2. **Given** a test database is available, **When** an entity is persisted and then retrieved, **Then** all mapped fields round-trip correctly.
3. **Given** a referential integrity constraint exists between entities (e.g., `Answer` → `Question`), **When** an orphaned record is attempted, **Then** the operation fails with the expected constraint violation.
4. **Given** the test database starts empty, **When** Flyway migrations run (including test-only migrations), **Then** the schema is created without errors.

**Edge Cases**:

- What happens when a `SelectGroup` has no `SelectItem` children?
- What happens when a `Respondent` has both active and inactive records?

---

### User Story 4 — Vaadin UI Layer Tests (Priority: P3)

A developer can run tests that verify the behavior of the 15 custom Elicit input components and smoke-test the major Vaadin views, so that UI-layer regressions are caught without requiring a full browser.

**Why this priority**: Vaadin views are hardest to test programmatically and represent lower risk for the 80% threshold (they will be excluded from unit-level enforcement), but the 15 custom input components contain non-trivial validation and value-handling logic that should be covered.

**Independent Test**: Running `mvn test` covers ≥ 70% of the `flow/input/` package. Views (`MainView`, `SectionView`, `ReviewView`, `ReportView`) have at least one smoke-test each confirming they instantiate and render without exception.

**Acceptance Scenarios**:

1. **Given** an `ElicitTextField` is instantiated, **When** a value is set and retrieved, **Then** the returned value matches the input.
2. **Given** an `ElicitCheckboxGroup` is instantiated with options, **When** multiple options are selected, **Then** all selected values are returned correctly.
3. **Given** an `ElicitRadioButtonGroup` is instantiated, **When** an option is selected, **Then** only that option's value is returned.
4. **Given** an `ElicitDatePicker` is instantiated, **When** a date is set, **Then** the formatted value conforms to expected output.
5. **Given** `MainView` is instantiated in a test context, **When** the view initializes, **Then** no exception is thrown and the login form is present.
6. **Given** `SectionView` is instantiated with a mock session, **When** the view renders, **Then** no exception is thrown and the question display area is present.

**Edge Cases**:

- What happens when an `ElicitIntegerField` receives a non-numeric string?
- What happens when an `ElicitEmailField` receives an invalid email format?
- What happens when a `SelectGroup` with no items is bound to a `ElicitComboBox`?

---

## Requirements *(mandatory)*

### Functional Requirements

#### Phase 1: JaCoCo Configuration

- **FR-001**: The build system MUST generate a JaCoCo instruction-coverage report on every `mvn verify` execution, with output in both XML and HTML formats.
- **FR-002**: The build MUST enforce a minimum overall instruction coverage of 80% and fail with a descriptive error when coverage falls below this threshold.
- **FR-003**: Vaadin view classes (classes annotated with `@Route` or residing in `flow/` views packages) and all classes in `com.elicitsoftware.report` (PDF generation and HTTP streaming — untestable without significant mocking overhead) MUST be excluded from the coverage threshold calculation via JaCoCo XML `<excludes>` patterns in `pom.xml`, per constitution Principle IV. Framework-generated proxy classes (`**/*$$*`) MUST also be excluded.
- **FR-004**: The JaCoCo configuration MUST integrate with the existing Surefire `@{argLine}` placeholder without requiring developers to change their existing `mvn test` workflow.
- **FR-005**: Coverage reports MUST be accessible at a stable, documented path after each build.

#### Phase 2: CDI Business-Logic Service Tests

- **FR-006**: Tests MUST exist for `TokenService` covering: token generation, respondent authentication, and token deactivation.
- **FR-007**: Tests MUST exist for `QuestionService` covering: answer save, survey navigation, and survey finalization.
- **FR-008**: Tests MUST exist for `QuestionManager` covering: section/step lifecycle management and question sequencing.
- **FR-009**: Tests MUST exist for `SessionPersistenceService` covering: session save and session restore.
- **FR-010**: Tests MUST exist for `ETLService` covering: successful ETL execution and error-path handling.
- **FR-011**: Tests MUST exist for `DatabaseHealthCheck` covering: healthy-state and degraded-state probes.
- **FR-012**: Tests for `@NormalUIScoped` beans MUST use `@InjectMock @MockitoConfig(convertScopes = true)` (from `quarkus-junit5-mockito`) as the isolation strategy, so they run without a live Vaadin UI context. No `QuarkusTestProfile` subclass is required unless a specific bean binding override is needed beyond scope conversion.
- **FR-012b**: Tests MUST exist for `BrandUtil` covering: default-brand path (no custom brand file present), custom-brand-file present, and missing-file fallback behavior.
- **FR-013**: If a live database is required for service tests, the test configuration MUST use TestContainers (or equivalent self-contained mechanism) so that tests run without a pre-existing external database.

#### Phase 3: Data Layer Tests

- **FR-014**: Tests MUST verify all non-trivial Panache entity finder/query methods for at minimum: `Respondent`, `Answer`, `Survey`, `Question`, `Section`, and `SelectGroup`.
- **FR-015**: Tests MUST verify entity persistence and retrieval round-trips for all mapped fields in the entities listed in FR-014.
- **FR-016**: Tests MUST verify referential-integrity constraints between related entities.
- **FR-017**: Tests MUST verify that Flyway migrations execute cleanly against a fresh schema.

#### Phase 4: Vaadin UI Tests

- **FR-018**: Tests MUST exist for all 15 custom Elicit input components covering: value set/get, validation behavior, and empty/null handling.
- **FR-019**: Each of the 7 Vaadin view classes MUST have at least one smoke test verifying instantiation without exception.
- **FR-020**: Vaadin component tests MUST run without a browser or full Vaadin server; a mock/headless Vaadin environment or Karibu-Testing is the expected mechanism.
- **FR-021**: The production class `ElcitDatePicker.java` MUST be renamed to `ElicitDatePicker.java` and all references (imports, usages, test class name) updated before tests are written.

### Key Entities

- **JaCoCo Coverage Report**: Aggregated per-class instruction coverage data, generated after test execution, consumed by build enforcement rule and developers.
- **Test Class**: A JUnit 5 class under `src/test/java` containing `@Test` methods that exercise production code.
- **Test Profile**: A Quarkus `QuarkusTestProfile` implementation used to configure alternative CDI scopes or bean overrides for tests.
- **Coverage Threshold**: The minimum acceptable instruction-coverage percentage (80%) enforced by the build gate.
- **Exclusion List**: The set of class patterns (Vaadin views, generated code, DTOs) excluded from threshold enforcement.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Running the full build (`mvn verify`) produces a JaCoCo coverage report and the build passes with overall instruction coverage ≥ 80%.
- **SC-002**: The CDI service layer (all classes in the root `com.elicitsoftware` package excluding `flow/` and `report/`) achieves ≥ 80% instruction coverage as shown in the JaCoCo report. Verified by inspecting `target/jacoco-report/index.html` after `mvn verify` (T056); not enforced by a per-package JaCoCo `<rule>` — the BUNDLE-level 80% rule (SC-001) provides build enforcement.
- **SC-003**: The data model layer (`com.elicitsoftware.model`) achieves ≥ 75% instruction coverage.
- **SC-004**: The custom input component layer (`com.elicitsoftware.flow.input`) achieves ≥ 70% instruction coverage.
- **SC-005**: All tests pass reliably in a clean environment without requiring a pre-existing external database — build is self-contained.
- **SC-006**: The number of active (non-commented-out) `@Test` methods increases from the current 13 to at least 80, enforced automatically by an `exec-maven-plugin` execution in the `verify` phase that runs `grep -rE '@Test' src/test/java | grep -v '^\ *//' | wc -l` and fails the build if the count is below 80. This enforcement runs on every `mvn verify` locally and in CI.
- **SC-007**: The build enforces the 80% threshold automatically: a deliberate deletion of a tested method causes the build to fail with a coverage violation, without any manual intervention.
- **SC-008**: New test infrastructure (TestContainers, JaCoCo plugin config) is documented in the project README so that any developer can run the full test suite after cloning the repository.

---

## Assumptions

- JaCoCo is already on the test classpath via `quarkus-jacoco`; the explicit `jacoco-maven-plugin` will be added to `pom.xml` for report and check goals only — no removal of the existing Quarkus integration.
- TestContainers will be used to provide a self-contained PostgreSQL instance for integration tests, replacing the dependency on a pre-existing database at `localhost:5452`.
- Karibu-Testing (or equivalent headless Vaadin testing library) will be used for Vaadin component and view tests, as Vaadin views cannot be instantiated in a plain `@QuarkusTest` context.
- `@NormalUIScoped` beans (`QuestionService`, `QuestionManager`, `UISessionDataService`) will be tested using `@InjectMock @MockitoConfig(convertScopes = true)` (from `quarkus-junit5-mockito`) as the mandated isolation strategy (see FR-012). No `QuarkusTestProfile` subclass is required for scope conversion.
- Mockito will be added as a test dependency to support mocking CDI beans that cannot be injected in the test scope.
- Coverage exclusions will include: all classes under `com.elicitsoftware.flow` (Vaadin views), all classes under `com.elicitsoftware.report` (PDF generation and download resource — excluded from threshold), all generated code under `frontend/`, and Flyway migration SQL files.
- The 80% threshold applies to instruction coverage (bytecode instructions), not line or branch coverage, consistent with JaCoCo defaults.
- Integration tests (`src/test/java` classes annotated `@QuarkusIntegrationTest`) remain disabled by default (`skipITs=true`) and are not required to reach the 80% threshold.
- Mobile or browser-based end-to-end tests are out of scope for this feature.
- The CI pipeline uses a runner with Docker available (e.g., GitHub Actions `ubuntu-latest` or GitLab shared runner with Docker). No Docker-free fallback strategy is required; `PostgresTestResource` can start a real container in CI without additional configuration.
- The production class `ElcitDatePicker.java` contains a typo and will be renamed to `ElicitDatePicker.java` as part of this feature before its test class is written.
