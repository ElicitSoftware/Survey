# Contributing to Elicit Survey

Thank you for your interest in contributing to Elicit Survey! This document provides guidelines and standards for contributing to this repository specifically — the respondent-facing module that presents questions and records answers. 

## Table of Contents
- [Development Methodology](#development-methodology)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
- [Coding Standards](#coding-standards)
- [Testing Requirements](#testing-requirements)
- [Pull Request Process](#pull-request-process)
- [Domain Knowledge](#domain-knowledge)
- [Getting Help](#getting-help)

## Development Methodology

### AI Unified Process (AIUP)

This project follows the **[AI Unified Process](https://unifiedprocess.ai/)**. The artifacts under `docs/` are the source of truth for *what* the system is supposed to do; `src/` is the implementation regenerated and refactored around them. See `CLAUDE.md` for the full workflow and working agreements — in short:

- `docs/vision.md`, `docs/requirements.md`, `docs/entity_model.md`, and `docs/use_cases/UC-*.md` describe intended behavior. If a change alters behavior, update the relevant artifact in the same PR (or explain in the PR why it doesn't apply yet).
- **Tests must be traceable to a use case.** Reference the `UC-XXX` (and, where relevant, `BR-XXX` business-rule) ID in the test's Javadoc/comment — see [Testing Requirements](#testing-requirements) for the exact convention already in use.
- Don't mix in jOOQ-specific patterns — data access is Hibernate ORM with Panache.
- Don't regenerate or hand-write code that bypasses Vaadin's component APIs (this is how the app gets WCAG 2.1 AA accessibility "for free" — see `NFR-001` in `docs/requirements.md`).
- If you're using an AI coding agent (Claude Code, the Quarkus Agent MCP tools, etc.), read `CLAUDE.md` and `AGENTS.md` first — they contain agent-specific rules (extension-first development, when to restart dev mode, commit conventions) that apply whether the change was written by a person or an agent.

There is no enforced code-coverage percentage gate in this repo's build — write the tests a change actually needs, and make sure they carry the use-case traceability above. Untested behavior changes to `QuestionManager`, the ETL pipeline (`etl/*`), or PDF/report generation should be treated as high risk given how central they are to respondent data integrity.

## Technology Stack

### Backend
- **Java**: 25 (see `.java-version`; `maven.compiler.release=25` in `pom.xml`)
- **Framework**: Quarkus 3.39.x
- **ORM**: Hibernate ORM with Panache — **active record** style (`extends PanacheEntityBase`, public fields, no repository classes)
- **Database**: PostgreSQL 17, two datasources (`default` for the app, `owner` for Flyway-owned DDL — see `application.properties`)
- **Migrations**: Flyway, `src/main/resources/db/migration`
- **Dependency Injection**: CDI (Jakarta EE), field injection (`@Inject`) — this codebase does not use constructor injection
- **REST APIs**: `quarkus-rest` (JAX-RS)
- **Observability**: OpenTelemetry, Micrometer + Prometheus (see `guides/OPENTELEMETRY_SETUP.md`, `guides/METRICS_GUIDE.md`)

### Frontend
- **Framework**: Vaadin 25.2.x **Flow** (server-side Java UI, integrated via `vaadin-quarkus-extension` — not Spring Boot)
- **Custom fields**: `flow/input/Elicit*` wrap Vaadin's input components with the project's `Answer`/`Question` binding conventions

### Testing
- **Test framework**: JUnit 5 (Jupiter) via `quarkus-junit`
- **Integration style**: `@QuarkusTest` against a real, externally-running PostgreSQL instance (see [Getting Started](#getting-started)) — this project does **not** use Testcontainers/Dev Services for tests (`%test.quarkus.datasource.devservices.enabled=false`)
- **Assertions**: plain `org.junit.jupiter.api.Assertions` — no AssertJ or Mockito in the current dependency set
- **Coverage tooling**: `quarkus-jacoco` is on the test classpath, but no threshold is enforced in the build or CI

### Build & Tools
- **Build**: Maven, via the wrapper (`./mvnw`) — don't assume a global `mvn` matches the project's expected version
- **CI**: GitHub Actions (`.github/workflows/maven.yml`) — currently builds and publishes the container image on version tags; it does **not** run the test suite, so tests are a manual/reviewer responsibility until that changes
- **Containers**: Docker (`src/main/docker/`, `buildDockerImage.sh`)

## Getting Started

### Prerequisites
- Java 25 JDK
- Docker (to run a local PostgreSQL 17)
- An IDE with Quarkus + Vaadin support (IntelliJ IDEA or VS Code with the Quarkus/Java extensions)

### Setup Development Environment

1. **Clone the repository**
   ```bash
   git clone https://github.com/ElicitSoftware/Survey.git
   cd Survey
   ```

2. **Start a local PostgreSQL 17 and create the expected roles/database**

   The Flyway migrations only `GRANT` to `survey_user`, `surveyadmin_user`, `surveyreport_user`, and `elicit_owner` — they don't create them, so a fresh local database needs a one-time setup step:
   ```bash
   docker run -d --name survey-postgres -p 5452:5432 \
     -e POSTGRES_PASSWORD=postgres postgres:17

   docker exec -i survey-postgres psql -U postgres <<'SQL'
   CREATE ROLE survey_user LOGIN PASSWORD 'SURVEYPW';
   CREATE ROLE surveyadmin_user LOGIN PASSWORD 'SURVEYPW';
   CREATE ROLE surveyreport_user LOGIN PASSWORD 'SURVEYPW';
   CREATE ROLE elicit_owner LOGIN PASSWORD 'SURVEYPW' CREATEROLE CREATEDB;
   CREATE DATABASE survey OWNER elicit_owner;
   SQL
   ```
   This matches the `%dev.`/`%test.` datasource URLs already in `application.properties` (`localhost:5452`, database `survey`). The passwords above are local-dev placeholders baked into the committed `application.properties` — never reuse them outside a local/throwaway database.

3. **Run the app in dev mode**
   ```bash
   ./mvnw quarkus:dev
   ```
   Flyway runs the migrations automatically on startup (`quarkus.flyway.owner.migrate-at-start=true`). Open http://localhost:8080.

4. **Run the tests** — against the same local database (create a `survey_test` database the same way if you want an isolated one; see the `%test.` properties)
   ```bash
   ./mvnw test
   ```

If you're using an AI coding agent with the Quarkus Agent MCP tools connected, prefer driving dev mode and tests through those tools (`quarkus_start`, `quarkus_callTool` → `devui-testing_runTests`) rather than raw Maven — see `AGENTS.md` for the full rationale (hot reload, structured failure reporting, etc.).

## Coding Standards

### Naming Conventions
- **Classes**: PascalCase (e.g., `QuestionManager`, `ETLRespondentService`)
- **Methods/variables**: camelCase
- **Constants**: UPPER_SNAKE_CASE
- **Packages**: flat under `com.elicitsoftware` — `flow` (Vaadin views), `flow.input` (custom field components), `model` (Panache entities), `etl`, `report` / `report.pdf` / `report.pdfbox`, `response` (DTOs), `util`, `common.health`

### License header

Every source file carries a license header block managed by `license-maven-plugin`:
```java
/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */
```
New files should include it. To have Maven insert/refresh it automatically, run `./mvnw package -Dlicense`.

### Entity/Model Classes

Entities use Panache's active-record style with public fields — no getters/setters boilerplate unless a field needs computed/derived access:

```java
@Entity
@Table(name = "surveys", schema = "survey")
public class Survey extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SURVEY_ID_GENERATOR")
    @SequenceGenerator(name = "SURVEY_ID_GENERATOR", schema = "survey", sequenceName = "surveys_seq", allocationSize = 1)
    @Column(name = "id", unique = true, nullable = false)
    public Integer id;

    @Column(name = "name")
    public String name;

    @OneToMany(mappedBy = "survey", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    public Set<ReportDefinition> reports;
}
```

### Service Layer

CDI beans use field injection, not constructor injection:

```java
@ApplicationScoped
public class QuestionManager {
    @Inject
    EntityManager entityManager;

    @Transactional
    public void saveAnswer(Answer answer) {
        // business logic here
    }
}
```

### Vaadin UI Code

Views are annotated `@NormalUIScoped` to prevent cross-tab session leakage, and reference `MainLayout` as the router layout:

```java
@Route(value = "section", layout = MainLayout.class)
@NormalUIScoped
public class SectionView extends VerticalLayout implements HasDynamicTitle {

    @Inject
    QuestionService service;

    @Inject
    UISessionDataService sessionDataService;
}
```

Compose new input fields from the existing `flow/input/Elicit*` wrappers where possible rather than dropping raw Vaadin components (or worse, raw HTML) into a view — that's the layer that keeps `Answer`/`Question` binding and theming consistent. Before writing a new UI pattern, check whether a Quarkus or Vaadin extension already covers it — this project's `AGENTS.md` has an extension-first rule for exactly this reason.

## Testing Requirements

### Naming and use-case traceability

Newer tests follow **given/when/then** method names and cite the `UC-XXX` use case (and `BR-XXX` business rule, where one applies) they exercise, either in a class-level Javadoc or an inline comment on the test:

```java
/**
 * UC-005: View Reports & Download PDF — covers the unauthenticated (BR-013)
 * short-lived server-side PDF cache.
 */
class PDFDownloadResourceTest {

    @Test
    // UC-005 BR-013: a missing key parameter is rejected with 400.
    void given_nullKey_when_downloadPDF_then_400BadRequest() {
        Response response = resource.downloadPDF(null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }
}
```

A handful of older tests still use plain `testXxx()` names without a UC reference — that's legacy, not the pattern to copy. New tests should follow the given/when/then + `UC-XXX` convention above.

### Integration-style tests

Most tests are `@QuarkusTest`s that run against the real local database rather than mocks:

```java
@QuarkusTest
class QuestionManagerTest {

    @Inject
    QuestionManager questionManager;

    @Inject
    EntityManager em;

    @Test
    void given_answeredBooleanQuestion_when_buildDownstreamQuestions_then_dependentSectionAppears() {
        // exercise real Panache entities/queries against the local Postgres instance
    }
}
```

Use `@TestTransaction` (from `io.quarkus.test`) when a test needs to mutate data and roll it back afterward, following the existing tests in `src/test/java`.

### Running Tests

```bash
# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=QuestionManagerTest

# Run tests in a package
./mvnw test -Dtest=com.elicitsoftware.etl.*
```

**Never run `mvn clean`/`./mvnw clean` while Quarkus dev mode is running against the same project** — it deletes `target/test-classes` and breaks the dev-mode test runner (see `AGENTS.md`).

## Pull Request Process

### Before Submitting
1. Run `./mvnw test` and make sure everything passes (CI does not run tests for you — see [Technology Stack](#technology-stack)).
2. If behavior changed, update the corresponding AIUP artifact under `docs/` (or note in the PR why it's deferred).
3. Update `README.md` if you added/changed an endpoint, extension, or setup step.
4. Keep license headers intact (`./mvnw package -Dlicense` if you added new files).

### Commit Message Format

Use [Conventional Commits](https://www.conventionalcommits.org/):
```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Types**: `feat`, `fix`, `test`, `refactor`, `docs`, `style`, `perf`, `chore`

**Examples:**
```
fix(etl): parameterize dimension-value SQL to remove native-query splicing

test(report): add UC-005 coverage for PDF cache-key generation
```

If you're contributing with the help of an AI assistant, follow `CLAUDE.md`'s commit rules — notably, **do not** append a `Co-Authored-By: <AI tool>` trailer to commit messages in this repo.

### Pull Request Template

```markdown
## Description
Brief description of changes

## Related Use Case(s)
UC-XXX (if applicable)

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation / AIUP artifact update

## Testing
- [ ] `./mvnw test` passes locally
- [ ] New/changed tests reference the relevant UC-XXX / BR-XXX

## Checklist
- [ ] `docs/` updated if behavior changed
- [ ] License headers present on new files
- [ ] Self-review completed
```

## Domain Knowledge

### Display Keys

Hierarchical addressing system for survey elements:
```
survey-step-step_instance-section-section_instance-question-question_instance
Example: 0001-0002-0001-0003-0002-0015-0001
```
See `DisplayKey.java` and `docs/entity_model.md`.

### Token System

Dynamic text replacement for personalized question text, implemented in `QuestionManager`:
- `{S1|default}` — subject/person identifier
- `{G1|default}` — general relationship term
- `{C1|default}` — category/condition reference
- `{CS2|default}` — category-subject combination
- `{R1|default}` — relationship context

**Example:** `"What is {S1|your}'s age?"` renders as `"What is John's age?"` when `S1 = "John"`.

### Conditional Logic

- **Operators** (`OperatorType`): BOOLEAN, GREATER_THAN, EQUAL, NOT_EQUAL, FIELD_EXIST, CONTAINS, and others
- **Actions** (`ActionType`): SHOW (display a question/section), REPEAT (replicate a section), TEXT (replace a token)
- Evaluated via `Relationship` rows connecting an upstream question/answer to a downstream question, section, or step

### Metadata & Dimensional Modeling (ETL)

- Ontology tags on questions/sections automatically drive `dim_*` dimension tables in the `surveyreport` schema
- `ETLService`/`ETLRespondentService` populate `surveyreport.fact_sections` and `surveyreport.fact_respondents` on survey finalize
- Table/column identifiers derived from ontology tags are validated (`Sql.requireValidIdentifier`) before being spliced into native SQL, since JDBC can't bind identifiers as parameters — respondent-entered *values* are always passed as bind parameters, never spliced

### Key Database Tables

**Core:** `surveys`, `steps`, `sections`, `questions`, `question_types`, `select_groups`, `select_items`, `steps_sections`, `sections_questions`

**Logic:** `relationships`, `dependents`, `operator_types`, `action_types`

**Responses:** `respondents`, `answers`, `respondent_psa` (post-survey-action call log)

**Reporting/analytics:** `dimensions`, `ontology`, `metadata`, `dim_*` (auto-generated), `fact_sections`, `fact_respondents`, `reports`

## Getting Help

- **AIUP artifacts**: `docs/vision.md`, `docs/requirements.md`, `docs/entity_model.md`, `docs/use_cases/UC-*.md`, `docs/use_cases.puml`
- **Observability guides**: `guides/OPENTELEMETRY_SETUP.md`, `guides/METRICS_GUIDE.md`
- **AI agent instructions**: `CLAUDE.md` (AIUP workflow), `AGENTS.md` (Quarkus-specific agent rules)
- **Reference deployment**: [FHHS](https://github.com/ElicitSoftware/FHHS)
- **Issues**: [github.com/ElicitSoftware/Survey/issues](https://github.com/ElicitSoftware/Survey/issues)

## License

Elicit Survey is distributed under the [PolyForm Noncommercial License 1.0.0](https://polyformproject.org/licenses/noncommercial/1.0.0). By contributing, you agree that your contributions will be licensed under the same license.

---

**Thank you for contributing to Elicit Survey!**
