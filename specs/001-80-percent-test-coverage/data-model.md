# Data Model: Achieve 80% Test Coverage

**Phase**: 1 — Design & Contracts
**Date**: 2026-05-01
**Plan**: [plan.md](plan.md)

---

## Overview

This feature introduces no new production data entities. It adds test infrastructure objects and configuration. The entities documented here are the **test-infrastructure entities** — the new classes and configuration objects that together implement the 80% coverage system.

---

## Entity 1: `PostgresTestResource`

**What it represents**: A Quarkus test resource that starts and stops a PostgreSQL container for the duration of the test suite. It bridges TestContainers with Quarkus' test lifecycle.

**Key attributes**:
- `POSTGRES`: static `PostgreSQLContainer<?>` — shared across all `@QuarkusTest` classes
- `start()` → returns JDBC URL map overriding `quarkus.datasource.jdbc.url` and `quarkus.datasource.owner.jdbc.url`
- `stop()` → stops the container after the full suite completes

**Lifecycle**: Singleton — one container per JVM process (`TestResourceScope.GLOBAL`)

**Location**: `src/test/java/com/elicitsoftware/PostgresTestResource.java`

---

## Entity 2: `UITestProfile` (optional)

**What it represents**: A `QuarkusTestProfile` that can override CDI bean configurations for tests that need a non-UI-scoped context.

**Key attributes**:
- `getConfigOverrides()` → may return scope-altering properties
- `getEnabledAlternatives()` → list of `@Mock`-annotated bean classes to activate

**Lifecycle**: Per-test-class — applied via `@TestProfile(UITestProfile.class)`

**Location**: `src/test/java/com/elicitsoftware/service/UITestProfile.java`

---

## Entity 3: JaCoCo Coverage Report

**What it represents**: A build artifact (not a Java class) produced by the `jacoco-maven-plugin` after test execution.

**Key attributes**:
- **Source file**: `target/jacoco-quarkus.exec` (written by `quarkus-jacoco` agent)
- **HTML report**: `target/jacoco-report/index.html` (human-readable, per-class line highlighting)
- **XML report**: `target/jacoco-report/jacoco.xml` (machine-readable, for CI dashboards)
- **Coverage threshold**: 80% INSTRUCTION COVEREDRATIO enforced at BUNDLE level

**Excluded from threshold**:
- `com/elicitsoftware/flow/*View` — Vaadin route views (UI navigation layer)
- `com/elicitsoftware/flow/*Layout` — Vaadin layout classes
- `**/*$$*` — Quarkus/Arc runtime-generated proxy classes

---

## Entity 4: Test Class (per production class)

**What it represents**: A JUnit 5 test class that exercises a production class. Each test class is associated 1:1 with a primary production class.

**Naming convention**: `{ProductionClassName}Test.java`

**Annotations**:
- CDI service tests: `@QuarkusTest` + `@WithTestResource(PostgresTestResource.class)`
- Data-layer tests: `@QuarkusTest` + `@TestTransaction`
- Vaadin component tests: plain JUnit 5 (no `@QuarkusTest`) + `MockVaadin.setup()`
- Vaadin view tests: plain JUnit 5 (no `@QuarkusTest`) + `MockVaadin.setup(routes)`

**Test method conventions**:
- Method name pattern: `given_[state]_when_[action]_then_[result]` or plain `[actionUnderTest]_[scenario]`
- Each method covers one logical behavior path
- `@TestTransaction` used on data-mutating tests to roll back after execution

---

## Entity 5: Coverage Threshold Configuration

**What it represents**: The Maven plugin `<rules>` configuration that enforces the 80% minimum.

**Fields**:
- `element`: `BUNDLE` (entire project, not per-class)
- `counter`: `INSTRUCTION` (JaCoCo bytecode instruction metric)
- `value`: `COVEREDRATIO`
- `minimum`: `0.80`

**Location**: `pom.xml` — `jacoco-maven-plugin` `jacoco-check` execution

---

## Production Entities Under Test (existing — no changes to schema or structure)

These are the existing production classes that must achieve coverage targets. Listed for traceability against spec requirements.

### CDI Services (Phase 2 target — ≥ 80% instruction coverage)

| Class | Scope | Test Class |
|-------|-------|------------|
| `TokenService` | `@RequestScoped` | `TokenServiceTest.java` (expand) |
| `QuestionService` | `@NormalUIScoped` | `QuestionServiceTest.java` (rewrite with `@InjectMock`) |
| `QuestionManager` | `@NormalUIScoped` | `QuestionManagerTest.java` (new) |
| `SessionPersistenceService` | `@RequestScoped` | `SessionPersistenceServiceTest.java` (new) |
| `ETLService` | `@ApplicationScoped` | `ETLServiceTest.java` (new) |
| `DatabaseHealthCheck` | `@ApplicationScoped` | `DatabaseHealthCheckTest.java` (new) |
| `BrandUtil` | `@ApplicationScoped` | `BrandUtilTest.java` (new) |
| `DisplayKey` | utility | `DisplayKeyTest.java` (new) |
| `RandomStringGenerator` | utility | `RandomStringGeneratorTest.java` (expand) |

### Panache Entities (Phase 3 target — ≥ 75% instruction coverage)

| Entity | Relationships | Test Class |
|--------|---------------|------------|
| `Respondent` | → `Answer`, → `Survey` | `RespondentEntityTest.java` |
| `Answer` | → `Question`, → `Respondent` | `AnswerEntityTest.java` |
| `Survey` | → `Step` | `SurveyEntityTest.java` |
| `Question` | → `QuestionType`, → `SelectGroup` | `QuestionEntityTest.java` |
| `Section` | → `SectionsQuestion` | `SectionEntityTest.java` |
| `SelectGroup` | → `SelectItem` | `SelectGroupEntityTest.java` |

### Custom Vaadin Input Components (Phase 4 target — ≥ 70% instruction coverage)

| Component | Wraps | Test Class |
|-----------|-------|------------|
| `ElicitTextField` | `TextField` | `ElicitTextFieldTest.java` |
| `ElicitTextArea` | `TextArea` | `ElicitTextAreaTest.java` |
| `ElicitIntegerField` | `IntegerField` | `ElicitIntegerFieldTest.java` |
| `ElicitDoubleField` | `NumberField` | `ElicitDoubleFieldTest.java` |
| `ElicitEmailField` | `EmailField` | `ElicitEmailFieldTest.java` |
| `ElicitPasswordField` | `PasswordField` | `ElicitPasswordFieldTest.java` |
| `ElicitCheckbox` | `Checkbox` | `ElicitCheckboxTest.java` |
| `ElicitCheckboxGroup` | `CheckboxGroup` | `ElicitCheckboxGroupTest.java` |
| `ElicitRadioButtonGroup` | `RadioButtonGroup` | `ElicitRadioButtonGroupTest.java` |
| `ElicitComboBox` | `ComboBox` | `ElicitComboBoxTest.java` |
| `ElicitMultiSelectComboBox` | `MultiSelectComboBox` | `ElicitMultiSelectComboBoxTest.java` |
| `ElicitDatePicker` (renamed from `ElcitDatePicker` — FR-021) | `DatePicker` | `ElicitDatePickerTest.java` |
| `ElicitDateTimePicker` | `DateTimePicker` | `ElicitDateTimePickerTest.java` |
| `ElicitTimePicker` | `TimePicker` | `ElicitTimePickerTest.java` |
| `ElicitHtml` | `Html` | `ElicitHtmlTest.java` |

### Vaadin Views (Phase 4 — smoke tests only; excluded from threshold)

| View | Route | Test Class |
|------|-------|------------|
| `MainView` | `""` / `"login"` | `MainViewTest.java` |
| `SectionView` | `"section"` | `SectionViewTest.java` |
| `ReviewView` | `"review"` | `ReviewViewTest.java` |
| `ReportView` | `"report"` | `ReportViewTest.java` |
| `AboutView` | `"about"` | `AboutViewTest.java` |
| `LogoutView` | `"logout"` | `LogoutViewTest.java` |
| `VersionView` | `"version"` | `VersionViewTest.java` |

---

## Coverage Target Summary

| Layer | Package Pattern | Target | Enforcement |
|-------|----------------|--------|-------------|
| CDI Services | `com.elicitsoftware` (root + `etl/` + `util/`) | ≥ 80% instruction | Build gate (jacoco-check) |
| Data Layer | `com.elicitsoftware.model` | ≥ 75% instruction | Build gate (jacoco-check) |
| Input Components | `com.elicitsoftware.flow.input` | ≥ 70% instruction | Build gate (jacoco-check) |
| Vaadin Views | `com.elicitsoftware.flow` (top-level views) | Smoke only | **Excluded** from threshold |
| PDF / Report | `com.elicitsoftware.report` | N/A | **Excluded** from threshold (clarification Q5) |
