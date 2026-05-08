# Elicit Survey — System Reference

> AI reference document. Read this before making changes to understand how the system works end-to-end.

---

## Purpose

Elicit Survey is a configurable, web-based survey engine built for the Rogel Cancer Center (University of Michigan). It administers multi-step, multi-section surveys where questions and entire sections can **conditionally appear, repeat, or have their display text altered** based on earlier answers. Access is token-based (no user accounts). After completion, the system generates PDF reports and fires configurable post-survey webhook actions. The canonical use case is a clinical Family Health History Survey (FHHS).

Licensed under PolyForm Noncommercial.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Runtime | Quarkus 3.34.1 |
| UI | Vaadin Flow 25.1.1 (via `vaadin-quarkus`) |
| ORM | Hibernate ORM with Panache |
| Database | PostgreSQL 17 |
| DB Migrations | Flyway (owner datasource) |
| REST | JAX-RS (RESTEasy) + MicroProfile REST Client |
| Observability | OpenTelemetry (OTLP/gRPC traces + metrics) + Micrometer/Prometheus |
| Health | SmallRye Health (`/q/health`) |
| PDF | Apache PDFBox + Batik (SVG rendering) |
| Build | Maven (`mvnw`) |
| Container | Docker (multi-stage, `buildDockerImage.sh`) |

---

## Domain Model

All entities are in `com.elicitsoftware.model`, mapped to the `survey` PostgreSQL schema.

### Core Structure

```
Survey
 ├── Step (ordered; groups sections into logical stages)
 │    └── StepsSections  (join table; assigns Section to Step with a display_key)
 │         └── Section (a page/group of questions)
 │              └── SectionsQuestion (join table; assigns Question to Section)
 │                   └── Question (the actual prompt; typed via QuestionType)
 │                        └── SelectGroup → SelectItem[] (options for COMBOBOX / RADIO / etc.)
 └── Respondent (one per token)
      └── Answer[]  (one per question-instance per respondent; keyed by display_key)
           └── Dependent[]  (records active relationship evaluations for this respondent)
```

### Key Tables

| Table | Description |
|---|---|
| `survey.surveys` | Survey definitions. `initial_display_key` is the first section's address. |
| `survey.respondents` | One row per token. `finalized_dt` set on completion. |
| `survey.steps` | Ordered logical stages within a survey. |
| `survey.sections` | A group of questions, belonging to a step. |
| `survey.steps_sections` | Many-to-many with `display_key` — the canonical position address. |
| `survey.questions` | Question definitions. `text` up to 8,000 chars; supports `{token\|default}` substitution. |
| `survey.question_types` | Seeded enum: `CHECKBOX`, `DATE_PICKER`, `COMBOBOX`, `HTML`, `INTEGER`, `DOUBLE`, `RADIO`, `TEXT`, `TEXTAREA`, `MULTI_SELECT`, `CHECKBOX_GROUP`, `DATETIME`, `EMAIL`, `PASSWORD`. |
| `survey.sections_questions` | Join table: assigns a question to a section with `display_order`. |
| `survey.answers` | One row per question-instance per respondent. `deleted=true` means conditionally hidden. |
| `survey.select_groups` / `select_items` | Option lists for select-type questions. |
| `survey.relationships` | Conditional logic rules (upstream → downstream with operator + action). |
| `survey.dependents` | Per-respondent instantiation of a `relationship` between two `answer` rows. |
| `survey.action_types` | `SHOW`, `REPEAT`, `TEXT`. |
| `survey.operator_types` | `BOOLEAN`, `GREATER THAN`, `EQUAL`, `NOT_EQUAL`, `FIELD_EXIST`, `CONTAINS`. |
| `survey.reports` | Pluggable report definitions (name + external URL) per survey. |
| `survey.post_survey_actions` | Webhook calls to fire in order after survey completion. |
| `survey.respondent_psa` | Tracks execution status of each post-survey action per respondent. |
| `survey.excluded_xids` | Department-scoped block-list (staff/test accounts). |

### Reporting Schema (`surveyreport`)

Star-schema for analytics, populated by `ETLService` and `ETLRespondentService`:

| Table | Description |
|---|---|
| `dim_date` | Date dimension. |
| `dim_step` / `dim_section` | Dimension tables for steps and sections. |
| `dim_status` | Status lookup (0=not started, 1=in progress, 2=finished). |
| `fact_respondents` | One row per respondent; date keys + status + duration. |
| `fact_sections` | One row per section-instance visited per respondent. |

---

## The DisplayKey Addressing Scheme

Every position in a survey is identified by a 7-part hyphen-delimited key:

```
{surveyId}-{stepOrder}-{stepInstance}-{sectionOrder}-{sectionInstance}-{questionOrder}-{questionInstance}
```

Example: `1-0002-0000-0003-0000-0005-0000`

This key is:
- Stored in `steps_sections.display_key` (section-level addresses)
- Stored in every `Answer` row
- Used as the navigation pointer in session state (`elicit.current.nav.path`)
- Used by `QuestionManager.navigate()` to find the next/previous section

Instance fields (`stepInstance`, `sectionInstance`, `questionInstance`) are non-zero when a section/step has been repeated via a `REPEAT` relationship.

---

## Conditional Logic System

Conditional behaviour is driven by `Relationship` rows. Each relationship links:

- **Upstream**: a specific `question` (and optionally `section`/`step`)
- **Downstream**: the thing to act on (question, section, or step)
- **OperatorType**: how to compare the upstream answer value
- **ActionType**: what to do when the condition is met
- **`referenceValue`**: the value to compare against

### Action Types

| Action | Behaviour |
|---|---|
| `SHOW` | Create downstream `Answer` rows (make them visible) when the condition is true; soft-delete them when false. |
| `REPEAT` | Instantiate the downstream section/step N times, where N is the upstream integer answer (e.g. "How many siblings?"). Tracked via `stepInstance`/`sectionInstance`. |
| `TEXT` | Substitute a `{token\|default}` placeholder in a downstream question's display text with the upstream answer's value. |

### Operator Types

`BOOLEAN`, `GREATER THAN`, `EQUAL`, `NOT_EQUAL`, `FIELD_EXIST`, `CONTAINS`

Evaluation happens in `Relationship.evaluateOperator(upstreamAnswer)`. `QuestionManager` calls this after every `saveAnswer()` and creates or soft-deletes `Dependent` + `Answer` rows accordingly.

### Soft Delete

`Answer.deleted` and `Dependent.deleted` are boolean flags. Answers are **never physically removed** during navigation — they are toggled deleted/undeleted as conditions change. This preserves history and allows re-activation if an upstream answer changes.

---

## Service Layer

All services are in `com.elicitsoftware`.

### `QuestionService` (`@NormalUIScoped`)

The **primary survey engine** — the entry point for all navigation and answer operations.

- `init()` — initialises placeholder answers for all sections; navigates to the first section; stores `NavResponse` in session
- `saveAnswer(answer)` — persists an answer; triggers downstream relationship evaluation via `QuestionManager`
- `review()` — returns a `ReviewResponse` built from a native SQL UNION query (all non-deleted answers grouped by section)
- `deactivate()` — finalises the respondent: sets `finalized_dt`, calls `ETLRespondentService`, then fires all `PostSurveyAction` webhooks in `execution_order`

### `QuestionManager` (`@NormalUIScoped`)

Low-level navigation and relationship evaluation.

- `init()` — creates the initial `Answer` rows for all `StepsSections` (one per section-question combination)
- `navigate(displayKey, direction)` — returns `NavResponse` for the next/previous section; skips sections whose answers are all deleted
- Evaluates all `Relationship` rows after each answer save; creates/deletes `Dependent` and `Answer` rows
- `replaceTokens(text, answers)` — substitutes `{token|default}` patterns from prior answers. Includes hardcoded grammar fixes: `" her's "→" her "`, `" his's "→" his "`, `s's→s'`

### `TokenService` (`@RequestScoped`)

- Generates 9-character tokens from a consonant-safe charset (excludes ambiguous chars `0 O 1 I l`)
- `login(token, surveyId)` — validates token, increments `logins`, sets `first_access_dt`
- `addToken(surveyId)` / `putToken(surveyId, token)` — create respondent rows
- Config flag `token.autoRegister=false` controls whether unknown tokens are auto-registered

### `UISessionDataService` (`@NormalUIScoped`)

Holds all per-browser-tab state: `surveyId`, `respondent`, `navResponse`. Every setter calls `SessionPersistenceService.persistSessionData()` to write to `VaadinSession`, enabling browser-refresh survival.

### `SessionPersistenceService` (`@RequestScoped`)

Reads/writes `VaadinSession` attributes: `elicit.survey.id`, `elicit.respondent.id`, `elicit.respondent.token`, `elicit.current.nav.path`. `restoreSessionData()` re-validates the token and re-navigates to the stored path on refresh.

### `ETLService` (`@ApplicationScoped` + `@Startup`)

On application startup: if `surveyreport.dim_section` is empty, populates all dimension tables. Always calls `populateAllFactSectionsTable()` to back-fill any missing `fact_sections` rows.

### `ETLRespondentService` (`@NormalUIScoped`)

Called during `deactivate()`. `populateFactSectionTable(respondentId)` inserts/updates `fact_sections`, `dim_step`, and `dim_section` rows for the completed respondent.

### `PDFService` (`@RequestScoped`)

Generates PDF from `List<ReportResponse>` using Apache PDFBox. Renders text, tables (`PDFTableGenerator`), and embedded SVG (Batik). Returns `byte[]`.

### `BrandUtil` (`@ApplicationScoped`)

Detects the active brand at runtime from filesystem path `/brand` (Docker volume) or `brand/` (local). Returns `BrandInfo` (CSS path, logo path, display name). Used for white-labelling.

### `DatabaseRetryUtil` (static)

Wraps any DB operation: 5 attempts, 200ms delay, retries on `SQLTransientException` / `PersistenceException`.

---

## Vaadin UI Views

All views are in `com.elicitsoftware.flow`, all `@NormalUIScoped`.

| View | Route | Purpose |
|---|---|---|
| `MainLayout` | (shell) | `AppLayout` with branded header; `SideNav` (Home, About, Logout). |
| `MainView` | `""` / `"login"` | Token entry + optional survey picker. On login: initialises session, calls `questionService.init()`, navigates to `"section"` (or `"report"` if already finalised). |
| `SectionView` | `"section"` | Renders the current section's questions from `NavResponse`. Uses `LinkedHashMap<String, Component>` (`displayMap`) to diff and only rebuild changed components. Previous/Next buttons call `saveAnswer()`. 900ms debounce on value change. Validation via Vaadin `Binder`. |
| `ReviewView` | `"review"` | Shows all answered questions grouped by section as `ReviewCard` components. "Finish" calls `questionService.deactivate()`. |
| `ReportView` | `"report"` | Calls external `ReportService` REST clients for each `ReportDefinition`; renders `ReportCard` components. "Generate PDF" button via `PDFService`. |
| `AboutView` | `"about"` | Survey names/descriptions + app version + build timestamp. |
| `LogoutView` | `"logout"` | Clears session, redirects to login. |
| `AppConfig` | (shell config) | `AppShellConfigurator`; loads `styles.css`; configures favicon and brand CSS via three-tier fallback. |

---

## REST Endpoints

| Class | Path | Notes |
|---|---|---|
| `PDFDownloadResource` | `GET /api/pdf/download?key={key}` | Serves cached PDF bytes (10-min `ConcurrentHashMap` cache). Publicly permitted — no OIDC redirect. |
| `BrandResourceHandler` | `GET /brand/{filePath}` | Serves brand assets with three-tier fallback. Path-traversal protection: blocks `..` and `//`. |
| `ReportService` (REST client) | External POST | MicroProfile REST Client interface. Instantiated at runtime via `RestClientBuilder` from `ReportDefinition.url`. |

---

## Startup Sequence

1. `AppConfig` (`@ApplicationScoped` + `@Startup`) — initialises brand paths
2. `ETLService` (`@ApplicationScoped` + `@Startup`) — checks `surveyreport.dim_section`; populates reporting schema if empty; back-fills `fact_sections`

---

## Survey Flow (End-to-End)

```
Browser → MainView.init()
           └─ TokenService.login()
               └─ UISessionDataService.set*()
                   └─ QuestionService.init()
                       └─ QuestionManager.init()  [creates placeholder Answer rows]
                           └─ QuestionManager.navigate()  [returns NavResponse]

Browser → SectionView.init()
           └─ reads NavResponse from UISessionDataService
           └─ on value change (debounced 900ms):
               └─ QuestionService.saveAnswer()
                   └─ QuestionManager evaluates Relationships
                       └─ creates/soft-deletes Dependent + Answer rows
           └─ Next → navigate forward
           └─ Previous → navigate back

Browser → ReviewView
           └─ QuestionService.review()  [native SQL UNION → ReviewResponse]
           └─ Finish → QuestionService.deactivate()
               ├─ sets respondent.finalized_dt
               ├─ ETLRespondentService.populateFactSectionTable()
               └─ fires PostSurveyAction webhooks in execution_order

Browser → ReportView
           └─ calls external ReportService REST clients
           └─ Generate PDF → PDFService → PDFDownloadResource
```

---

## Session Persistence (Browser Refresh Survival)

`UISessionDataService` writes to `VaadinSession` on every setter via `SessionPersistenceService`. On refresh, a new Vaadin UI is created, `restoreSessionData()` re-validates the token, and re-navigates to `elicit.current.nav.path`.

---

## Two-Datasource Security Pattern

| Datasource | User | Privileges | Used by |
|---|---|---|---|
| Default | `survey_user` | DML only (SELECT, INSERT, UPDATE, DELETE) | Hibernate ORM / application |
| Owner | `elicit_owner` | DDL owner | Flyway migrations + ETL views |

The application **cannot drop or alter its own schema**. This is an intentional security boundary.

---

## Database Triggers (V002 migration)

- `insert_fact_respondent()` — AFTER INSERT on `survey.respondents` → inserts into `surveyreport.fact_respondents`
- `update_fact_respondent()` — AFTER UPDATE on `survey.respondents` → updates `surveyreport.fact_respondents`

Both currently filter to `survey_id = 1`.

---

## Configuration Reference

Key properties in `src/main/resources/application.properties`:

| Property | Default / Note |
|---|---|
| `brand.file.system.path` | `/brand` — Docker volume mount for runtime CSS/logo |
| `token.autoRegister` | `false` |
| `quarkus.datasource.db-kind` | `postgresql` |
| `%prod.quarkus.datasource.jdbc.url` | `jdbc:postgresql://db:5432/survey` |
| `quarkus.flyway.owner.schemas` | `survey,surveyreport` |
| `quarkus.flyway.owner.locations` | `db/migration` |
| `quarkus.otel.*` | OTLP/gRPC to `localhost:4317` |
| `quarkus.micrometer.*` | Prometheus at `/q/metrics` |
| `quarkus.http.auth.permission.pdf.*` | `permit` (no auth on PDF endpoint) |
| `quarkus.http.cors.origins` | `/.*/` |
| `LOG_LEVEL` | `WARN` (env-var override) |

Dev profile seeds a test survey via `src/main/resources/db/dev/V005__Create_test_survey.sql`.

---

## Survey Data Authoring (SQL Migration)

Surveys are seeded via Flyway SQL files. The canonical reference is the FHHS survey at `src/main/resources/db/migration/V0.0.1__POPULATE_FHHS_DATA.sql` in the FHHS repository. Deviating from these patterns causes subtle runtime bugs.

### Required Block Execution Order

Blocks **must appear in this order** within a migration file. Foreign-key dependencies and Hibernate initialisation are sensitive to insertion order:

1. `SURVEY`
2. `SELECT GROUPS`
3. `STEPS`
4. `SECTIONS` — sentinel row first (see below)
5. `STEPS_SECTIONS`
6. `QUESTIONS`
7. `SECTIONS_QUESTIONS`
8. `RELATIONSHIPS`
9. `DIMENSIONS`
10. `ONTOLOGY`
11. `METADATA`
12. `RESPONDENTS`
13. `SELECT ITEMS` — **must be last**; inserting it earlier (e.g. immediately after SELECT GROUPS) causes rendering defects

### Critical Per-Table Rules

| Table | Rule |
|---|---|
| `select_groups` | Always specify the `data_type` column explicitly: `(id, survey_id, name, description, data_type)` with value `'Text'`. The column has `DEFAULT 'Text'` but must not be omitted. |
| `select_items` | `display_order` must be a bare integer literal (`1`, `2`, `3`) — **not** a string (`'1'`). The column is `integer NOT NULL`; string literals cause type coercion issues. |
| `select_items` | Block must be the **last** block in the file (see order above). |
| `sections` | The sentinel row `(0, 1, 0, '', '', '')` is **required** by the engine for edge-case navigation handling. It must be the **first** INSERT in the sections block (before all `NEXTVAL`-based rows). |
| `questions` | RADIO questions (`type_id = 7`) must use `variant = 'vertical'`. Using `''` (empty string) causes duplicate option rendering (e.g. "Yes Yes No No"). |

### Question Type IDs (from `survey.question_types` seed)

| type_id | Type | Notes |
|---|---|---|
| 1 | `CHECKBOX` | Boolean toggle |
| 2 | `DATE_PICKER` | Date input |
| 3 | `COMBOBOX` | Single-select dropdown; requires `select_group_id` |
| 4 | `HTML` | Static HTML content block |
| 5 | `INTEGER` | Numeric integer input; supports `min_value`/`max_value`/`error_message` |
| 6 | `DOUBLE` | Numeric decimal input; supports `min_value`/`max_value`/`error_message` |
| 7 | `RADIO` | Radio button group; requires `select_group_id`; **must use `variant='vertical'`** |
| 8 | `TEXT` | Single-line text input |
| 9 | `TEXTAREA` | Multi-line text input |
| 10 | `MULTI_SELECT` | Multi-select list; requires `select_group_id` |
| 11 | `CHECKBOX_GROUP` | Multi-select checkboxes; requires `select_group_id` |
| 12 | `DATETIME` | Date + time input |
| 13 | `EMAIL` | Email text input |
| 14 | `PASSWORD` | Password input |

### Token Substitution in Question Text

Question `text` fields support `{TOKEN|default}` patterns resolved at render time from prior answers. Common patterns:

- `{FNAME|friend}` — substitutes respondent's first name answer, default `"friend"`
- `{S#|N}` — substitutes a section-level answer count (e.g. number of sessions)
- `{S1|unknown}` — substitutes the first instance of a section-scoped answer
- `{Q#|default}` — substitutes a specific question's answer by sequence number

---

## Key Business Rules

1. **DisplayKey is the canonical address** for every survey position (see [DisplayKey Addressing](#the-displaykey-addressing-scheme) above).
2. **Soft delete only** — `Answer` and `Dependent` rows are never physically removed during navigation.
3. **Token generation** uses a consonant-safe 9-char charset (no `0 O 1 I l`); max 4 retries before error.
4. **Grammar substitution** in `replaceTokens()`: `" her's "→" her "`, `" his's "→" his "`, `s's→s'`.
5. **`excluded_xids`** blocks specific identifiers by department (staff/test account exclusion).
6. **ETL is synchronous** — `fact_sections` is populated as part of `deactivate()`, not in a background job.
7. **PDF is cached** — `PDFDownloadResource` holds generated bytes in a `ConcurrentHashMap` with 10-minute expiry to allow download from a new tab without re-generating.
