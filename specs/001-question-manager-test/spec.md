# Feature Specification: QuestionManager Integration Tests

**Feature Branch**: `001-question-manager-test`  
**Created**: 2026-05-16  
**Status**: Approved  
**Input**: Implement integration tests for QuestionManager using the V005 Library Survey and V005.5 Tess Tester dataset

---

## Implementation Guidance for AI Agents

Before writing **any** code or tests, the implementing agent MUST:

1. Call `quarkus_skills(projectDir, query="hibernate-orm-panache")` — learn correct Panache entity access patterns used in this project.
2. Call `quarkus_skills(projectDir, query="rest")` — learn REST and `@QuarkusTest` patterns.
3. Call `quarkus_skills(projectDir, query="arc")` — learn CDI scope and injection patterns, especially relevant for `@NormalUIScoped`.
4. Start the app if not running: `quarkus_start(projectDir)`.
5. Use `quarkus_searchTools(projectDir)` to discover the Dev MCP test runner.
6. Run existing tests via `quarkus_callTool(toolName: "devui-testing_runTests")` before adding new ones to establish a green baseline.
7. After writing each new test method, run it via `quarkus_callTool(toolName: "devui-testing_runTest", toolArguments: '{"className":"com.elicitsoftware.survey.QuestionManagerTest"}')` to confirm red → green.
8. If a compilation or runtime error occurs, use `quarkus_callTool(toolName: "devui-exceptions_getLastException")` before attempting a fix.

Skipping any of these steps is a constitution violation (Principle II — Test-First).

---

## Context

`QuestionManager` is the core survey engine (1,753 LOC). It drives every
respondent interaction: initializing answers, resolving conditional
relationships, executing SHOW/REPEAT/TEXT actions, replacing tokens, and
building navigation. It has **zero test coverage today**.

V005 (`V005__Library_Test.sql`) defines a deliberately exhaustive survey
("Public Library Card Registration & Media Checkout Request") that exercises:
- All 14 question types
- All 6 relationship operators (BOOLEAN, EQUAL, NOT_EQUAL, GREATER_THAN, FIELD_EXIST, CONTAINS)
- All 3 action types (SHOW, REPEAT, TEXT)
- Token patterns: `{S#}`, `{Q#}`, `{EMAIL}`, `{PHONE}`
- Repeated step (checkout) and nested repeated section (renewal within checkout)
- 3-level nested conditional chain (Q50→Q51→Q52)

V005.5 (`V005.5__Tess_Tester_Data.sql`) provides a completed respondent
("Tess Tester", token `test1`) with answers that walk every active conditional
path through the survey — making it the canonical fixture for
assertion-based tests.

---

## Logic Coverage Matrix

Every operator and action type defined in V005 MUST have at least one test
scenario that exercises the true-path AND one that exercises the false-path
(or the "condition not met" path). The table below is the contract; the
implementing agent MUST verify every row is covered before marking this
feature complete.

| # | Operator / Action | V005 Relationship | True-path test | False-path test |
|---|---|---|---|---|
| 1 | BOOLEAN — true | R18: Q36 terms=TRUE → SHOW ss_patron | US-1 init / US-6 | US-6 false-path |
| 2 | BOOLEAN — false | R19: Q36 terms=FALSE → SHOW ss_decline | US-6 false-path | — |
| 3 | EQUAL | R21: Q43 digital=TRUE → SHOW ss_digital | US-3 scenario 1 | US-3 scenario 2 |
| 4 | NOT_EQUAL | R30: Q68 itemType NOT_EQUAL 'book' → SHOW Q69 | US-7 | US-7 false-path |
| 5 | GREATER_THAN | R23: Q49 qty>0 → REPEAT ss_checkout | US-4 scenario 1 | US-4 scenario 2 (qty=0) |
| 6 | FIELD_EXIST | R26: Q51 exists → SHOW Q52 (level 3 chain) | US-8 scenario 3 | US-8 scenario 1 (chain blocked) |
| 7 | CONTAINS | R24: Q44 CONTAINS 'dvd' → SHOW ss_mediaprefs | US-3 scenario 3 | US-3 (value without 'dvd') |
| 8 | SHOW action | R21, R24, R20, R27 | US-3, US-6, US-7 | respective false-paths |
| 9 | REPEAT action | R23 (step), R29 (section) | US-4 | US-4 decrement |
| 10 | TEXT action | R28: Q67 display text token substitution | US-5 / US-9 | n/a (always fires) |
| 11 | 3-level nested chain | R25→R26→(Q52) Q50→Q51→Q52 | US-8 full chain | US-8 chain blocked at L1 |
| 12 | Soft-delete restore | Any SHOW: condition flips true→false→true | US-3 scenario 2 + re-true | US-3 |
| 13 | Repeated section in step | R29: Q62 renewals>0 → REPEAT ss_renewal inside checkout | US-4 scenario nested | US-4 (renewals=0) |

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Init seeds the correct baseline answers (Priority: P1)

When `QuestionManager.init()` is called for a new respondent on the Library
survey, it must create Answer rows for every non-conditional (non-downstream)
question in the initial steps and sections — and only those questions.

**Why this priority**: `init()` is called on every respondent's first access;
a bug here silently skips questions or creates duplicate answers downstream.

**Independent Test**: Invoke `init(respondentId, surveyKey)` against a fresh
respondent (not Tess Tester) and assert on the count and displayKeys of the
created Answer rows using the real Flyway-migrated test database.

**Acceptance Scenarios**:

1. **Given** a respondent with no prior answers exists for the Library survey,
   **When** `init(respondentId, "2.0.0")` is called,
   **Then** exactly the non-downstream questions in the Welcome and Patron steps are present as Answer rows with `deleted = false`.

2. **Given** `init()` is called twice for the same respondent,
   **When** the second call completes,
   **Then** no duplicate Answer rows exist (idempotent).

---

### User Story 2 — navigate() returns the correct answers and nav items for a given section (Priority: P1)

`navigate()` is the method called on every page transition. It must return the
right answers for the current section, a correctly ordered nav item list, and
the correct current step — for both initial and already-answered sections.

**Why this priority**: Incorrect navigation state produces a broken survey UX
with questions missing or displayed in the wrong order.

**Independent Test**: Using Tess Tester's respondent data (all answers
pre-loaded by V005.5), call `navigate(respondentId, sectionKey)` for each
step/section key and assert on the returned `NavResponse` fields.

**Acceptance Scenarios**:

1. **Given** Tess Tester's completed answers are loaded,
   **When** `navigate(testsRespondentId, "2.1.8")` is called (Welcome section),
   **Then** the returned `NavResponse.answers` contains the Welcome section answers in displayKey order, and `NavResponse.step` matches step id for Welcome.

2. **Given** Tess Tester has answered the Digital Access conditional question TRUE,
   **When** `navigate(testsRespondentId, "2.2.10")` is called (Digital Access section),
   **Then** the Digital Access section answers are present and the nav items list includes the Digital Access nav entry.

3. **Given** Tess Tester has not triggered the Mystery genre branch,
   **When** `navigate(testsRespondentId, "2.3.11")` is called (Collection Prefs section),
   **Then** no mystery-branch downstream answers are present in the result.

---

### User Story 3 — saveAnswer(answer, newValue) helper in test class with a SHOW relationship conditionally creates downstream answers (Priority: P2)

When a respondent answers a question that has a downstream SHOW relationship,
`saveAnswer(answer, newValue) helper in test class` must create the downstream Answer row if the operator condition
evaluates to true, and must not create it (or must delete it) when the
condition evaluates to false.

**Why this priority**: The SHOW action is the most-used conditional type;
wrong evaluation silently hides or reveals questions for all respondents.

**Independent Test**: Against a fresh respondent on the Library survey, call
`saveAnswer(answer, newValue) helper in test class` for the "Digital access?" checkbox question with value TRUE, then
assert the Digital Access section answer rows appear. Then call it again with
FALSE and assert those rows are marked deleted.

**Acceptance Scenarios**:

1. **Given** a fresh respondent with init answers,
   **When** `saveAnswer(answer, newValue) helper in test class` is called for the "Digital access?" question with `textValue = "TRUE"` (EQUAL operator, R21),
   **Then** an Answer row for the Digital Access section (ss10) is created with `deleted = false`.

2. **Given** the same respondent has "Digital access?" = TRUE,
   **When** `saveAnswer(answer, newValue) helper in test class` is called again with `textValue = "FALSE"`,
   **Then** the Digital Access section answer is marked `deleted = true`.

3. **Given** a fresh respondent,
   **When** `saveAnswer(answer, newValue) helper in test class` is called for the media type question with `textValue = "dvd"` (CONTAINS operator, R24),
   **Then** Media Preferences section answer rows are created.

---

### User Story 4 — REPEAT action produces repeated step/section instances (Priority: P2)

When `saveAnswer(answer, newValue) helper in test class` triggers a REPEAT relationship (e.g., "How many checkout
items?" > 0), `QuestionManager` must create as many step or section instances
as the integer answer value specifies.

**Why this priority**: REPEAT drives multi-item workflows; an off-by-one error
creates ghost instances or drops items entirely.

**Independent Test**: Call `saveAnswer(answer, newValue) helper in test class` for the checkout quantity question
with value `"2"` (R23, GREATER_THAN '0', REPEAT ss_checkout) and assert that
two distinct step instance Answer groups exist with the correct displayKeys.

**Acceptance Scenarios**:

1. **Given** a fresh respondent,
   **When** `saveAnswer(answer, newValue) helper in test class` is called for the checkout quantity question with `textValue = "2"`,
   **Then** two checkout step instances are created, each with a unique step-instance suffix in their displayKeys.

2. **Given** two checkout instances exist,
   **When** `saveAnswer(answer, newValue) helper in test class` updates the checkout quantity to `"1"`,
   **Then** the second checkout instance's answers are marked deleted.

---

### User Story 5 — replaceTokens() substitutes all token types correctly (Priority: P3)

Token replacement (`{S#}`, `{Q#}`, `{EMAIL}`, `{PHONE}`) is used in question
display text. The static `replaceTokens` method must handle all token
patterns, remove unmatched tokens leaving the default text, and apply the
known text-normalization fixups (`s's → s'`, possessive corrections).

**Why this priority**: Token bugs corrupt display text without crashing; they
are invisible until a user sees garbled output.

**Independent Test**: Unit test (no DB) calling `replaceTokens` directly with
controlled input strings and value maps.

**Acceptance Scenarios**:

1. **Given** text `"Hello {NAME|friend}"` and values `{NAME: "Alice"}`,
   **When** `replaceTokens` is called,
   **Then** result is `"Hello Alice"`.

2. **Given** text `"Hello {NAME|friend}"` and an empty values map,
   **When** `replaceTokens` is called,
   **Then** result is `"Hello friend"` (default text kept, token removed).

3. **Given** text `"Dennis's card"`,
   **When** `replaceTokens` is called (normalization pass),
   **Then** result is `"Dennis' card"` (s's → s').

---

### User Story 6 — BOOLEAN operator gates an entire step (Priority: P1)

The terms-acceptance question (Q36) uses a BOOLEAN operator. TRUE shows the
Patron step (R18); FALSE shows the decline step (R19). This is the survey's
top-level gate — a bug here affects every respondent.

**Why this priority**: Every respondent hits this branch; a regression here
makes the whole survey unreachable.

**Independent Test**: Call `saveAnswer(answer, newValue) helper in test class` for Q36 with `"TRUE"` and assert
ss_patron answers are created; call again with `"FALSE"` and assert ss_patron
is deleted and ss_decline appears.

**Acceptance Scenarios**:

1. **Given** a fresh respondent,
   **When** `saveAnswer(answer, newValue) helper in test class` sets Q36 `textValue = "TRUE"` (BOOLEAN, R18),
   **Then** Patron step answer rows are created (`deleted = false`), decline section is absent.

2. **Given** Q36 is TRUE (Patron step visible),
   **When** `saveAnswer(answer, newValue) helper in test class` changes Q36 to `"FALSE"` (BOOLEAN, R19),
   **Then** all Patron step answers are marked `deleted = true`, and the decline section answer row appears.

3. **Given** Q36 has been FALSE then changed back to `"TRUE"`,
   **When** `saveAnswer(answer, newValue) helper in test class` completes (soft-delete restore cycle),
   **Then** Patron step answers are active again (`deleted = false`) — previously deleted rows are restored, not duplicated.

---

### User Story 7 — NOT_EQUAL operator conditionally shows a follow-up question (Priority: P2)

Within the checkout section, item type NOT_EQUAL 'book' (R30) shows an
additional format question (Q69). NOT_EQUAL is the least-tested operator in
the codebase.

**Why this priority**: NOT_EQUAL is easy to accidentally implement as EQUAL
during refactors; a dedicated test catches this inversion immediately.

**Independent Test**: Call `saveAnswer(answer, newValue) helper in test class` for the item type question with
`"audiobook"` (not 'book') and assert Q69 appears; then call with `"book"`
and assert Q69 is absent/deleted.

**Acceptance Scenarios**:

1. **Given** a fresh respondent in a checkout step instance,
   **When** `saveAnswer(answer, newValue) helper in test class` sets item type to `"audiobook"` (NOT_EQUAL 'book', R30),
   **Then** the format question (Q69) answer row is created (`deleted = false`).

2. **Given** item type is `"audiobook"` (Q69 visible),
   **When** `saveAnswer(answer, newValue) helper in test class` changes item type to `"book"`,
   **Then** the format question (Q69) answer row is marked `deleted = true`.

---

### User Story 8 — 3-level nested conditional chain evaluates each level independently (Priority: P2)

Q50 CONTAINS 'audiobook' → SHOW Q51 (R25, level 1).
Q51 FIELD_EXIST → SHOW Q52 (R26, level 2).
Q52 is only visible when both upstream conditions are satisfied.

**Why this priority**: Nested chains are the most common source of regression
when relationship evaluation logic changes; each level must gate the next.

**Independent Test**: Walk the chain step-by-step with a fresh respondent,
asserting the presence/absence of each downstream question at each level.

**Acceptance Scenarios**:

1. **Given** a fresh respondent has not answered the media type question,
   **When** `navigate()` is called for the checkout section,
   **Then** Q51 and Q52 answer rows are both absent.

2. **Given** `saveAnswer(answer, newValue) helper in test class` sets media type to `"audiobook"` (R25, CONTAINS),
   **When** `navigate()` is called again,
   **Then** Q51 answer row is present (`deleted = false`), Q52 is still absent (FIELD_EXIST not yet satisfied).

3. **Given** Q51 has been answered (any non-null value, satisfying FIELD_EXIST R26),
   **When** `saveAnswer(answer, newValue) helper in test class` is called for Q51,
   **Then** Q52 answer row is created (`deleted = false`).

4. **Given** the full chain Q51 and Q52 are visible,
   **When** `saveAnswer(answer, newValue) helper in test class` changes media type to remove 'audiobook',
   **Then** both Q51 and Q52 answer rows are marked `deleted = true` (cascade delete through chain).

---

### User Story 9 — TEXT action substitutes tokens in display text at navigation time (Priority: P2)

R28 uses the TEXT action to inject a previously-captured answer value into the
display text of Q67. The substituted text must appear in the Answer's display
text when `navigate()` returns that section.

**Why this priority**: TOKEN substitution is silent — wrong output looks like
valid text until a user notices the wrong name or value displayed.

**Independent Test**: Using Tess Tester's completed data (which includes the
token-substituted answers), call `navigate()` for the section containing Q67
and assert the returned Answer's display text contains the substituted value,
not the raw `{Q#|default}` token.

**Acceptance Scenarios**:

1. **Given** Tess Tester has a previously-captured answer for the token source question,
   **When** `navigate()` returns the section containing Q67 (TEXT action, R28),
   **Then** the display text of the Q67 Answer contains the resolved value, not the raw token pattern.

2. **Given** no prior answer exists for the token source question,
   **When** `navigate()` returns Q67,
   **Then** the display text falls back to the default text defined in the `{TOKEN|default}` pattern.

---

### Edge Cases

These are concrete test scenarios, not questions. Each MUST have a
corresponding test method.

- **EC-01** `navigate()` on a section with no prior answers (first visit) must
  call `buildInitialAnswers()` and return a non-empty answer list, not an
  empty list or null.
- **EC-02** `init()` called for a non-existent respondentId must not throw;
  it must return cleanly with zero answers created.
- **EC-03** `saveAnswer(answer, newValue) helper in test class` with a null `textValue` on a BOOLEAN operator
  relationship must treat null as false — no downstream answers created.
- **EC-04** `saveAnswer(answer, newValue) helper in test class` with an empty string `textValue` on a CONTAINS
  operator must not match any downstream relationships.
- **EC-05** REPEAT with quantity decreased to 0: all created instances must be
  marked deleted; no orphaned Answer rows remain active.
- **EC-06** REPEAT with quantity increased from 1 to 3: only 2 new instances
  are created (not 3), and the existing instance is preserved unchanged.
- **EC-07** The 3-level nested chain (Q50→Q51→Q52): answering Q50 with a
  non-matching value (does not CONTAIN 'audiobook') must not create Q51 or Q52,
  even if Q51 previously existed from a prior answer.
- **EC-08** Soft-delete restore: an Answer row that was deleted when a
  condition became false must be reactivated (not duplicated) when the
  condition becomes true again.
- **EC-09** `navigate()` answer list must be in ascending displayKey order
  for every section — order must be stable across multiple calls.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Tests MUST use `@QuarkusTest` backed by a **Quarkus Dev Services** PostgreSQL container scoped to the `%test` profile only. Dev Services MUST be disabled for the `%dev` profile (which uses the real PostgreSQL instance). The existing `%test` hardcoded JDBC URLs (`%test.quarkus.datasource.jdbc.url` and `%test.quarkus.datasource.owner.jdbc.url`) MUST be removed — Quarkus only starts Dev Services when no explicit URL is configured for the profile. Set the following in `application.properties`:
  ```
  # Remove or comment out:
  # %test.quarkus.datasource.jdbc.url=...
  # %test.quarkus.datasource.owner.jdbc.url=...

  %dev.quarkus.datasource.devservices.enabled=false
  %dev.quarkus.datasource.owner.devservices.enabled=false
  %test.quarkus.datasource.devservices.reuse=false
  %test.quarkus.datasource.owner.devservices.reuse=false
  ```
  `devservices.reuse=false` on both datasources ensures the containers are **removed from Docker on shutdown**. Both the default (`survey_user`) and owner (`elicit_owner`) datasources need this treatment since both are active in the `%test` profile.
- **FR-002**: Flyway MUST apply migrations from two locations in order:
  1. `src/main/resources/db/migration/` — V001–V004 core schema and seed data (production scripts, applied first).
  2. `src/test/resources/db/test/` — V005 Library Survey structure and V005.5 Tess Tester data (test-only fixtures, applied second).
  Configure Flyway `locations` in `application.properties` under `%test` profile to include both paths.
- **FR-003**: `QuestionManagerTest` MUST inject `QuestionManager` as a CDI bean (`@Inject`) — no mocking of the class under test.
- **FR-004**: EntityManager and all Panache queries MUST hit the real test database — no mock data access layer.
- **FR-005**: Each test that mutates database state MUST run in a transaction that is rolled back after the test (`@TestTransaction`). This is the primary mechanism for keeping tests fast and isolated — no truncate/reload between methods.
- **FR-006**: Tests for `replaceTokens` (User Story 5) MUST be plain JUnit 5 unit tests with **no** `@QuarkusTest` annotation. No Quarkus context, no container, no datasource — pure Java. This keeps token logic tests near-instant.
- **FR-007**: All test methods MUST follow the Given/When/Then naming convention: `given_<state>_when_<action>_then_<outcome>`.
- **FR-008**: All `@QuarkusTest` methods in the class MUST share a **single Quarkus application instance**. Do not use `@TestProfile` in a way that forces a Quarkus restart between test classes. A restart costs 5–15 seconds and negates the benefit of dev mode continuous testing.
- **FR-009**: Do NOT assert on Micrometer metrics or inject `MeterRegistry` in any test. Metrics instrumentation adds container overhead and slows test startup; it is out of scope for this feature.
- **FR-010**: Do NOT use `@QuarkusIntegrationTest`. It starts a full packaged server (slow, high memory). `@QuarkusTest` runs in-process and is sufficient.

### Key Entities *(involved)*

- **Answer**: The primary mutable entity; test assertions check `textValue`, `deleted`, `displayKey` fields.
- **Respondent**: Each integration test scenario requires at least one respondent; V005.5 provides Tess Tester (id=1, token=`test1`); destructive tests use a separate fresh respondent inserted in-test.
- **Relationship**: Drives conditional logic; tests implicitly exercise the full relationship graph defined in V005.
- **StepsSections / SectionsQuestion**: Drive the displayKey structure; tests verify keys are formed correctly.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `QuestionManagerTest` contains at minimum one test method per user story scenario AND one per edge case — ≥ 25 test methods total.
- **SC-002**: All tests pass in CI with `mvn verify` against the test-profile PostgreSQL database.
- **SC-003**: Line coverage on `QuestionManager.java` reaches ≥ 70% after this test class is added (measured by JaCoCo), meeting the project-wide floor. The Logic Coverage Matrix is the primary quality gate; JaCoCo coverage is the verification tool.
- **SC-004**: Every row in the Logic Coverage Matrix has at least one test covering the true-path AND one covering the false-path (or "condition not met" path). The implementing agent MUST annotate each test method with a comment referencing the matrix row it covers (e.g., `// Matrix row 4: NOT_EQUAL false-path`).
- **SC-005**: No test uses `Mockito.mock()` or `@InjectMocks` on `QuestionManager` itself — only real CDI injection.
- **SC-006**: Test run time for the full `QuestionManagerTest` class is under 60 seconds after the Quarkus app is already running. The `replaceTokens` unit tests (no Quarkus context) MUST complete in under 1 second total.
- **SC-007**: All 6 operators (BOOLEAN, EQUAL, NOT_EQUAL, GREATER_THAN, FIELD_EXIST, CONTAINS) and all 3 action types (SHOW, REPEAT, TEXT) have explicit test method coverage traceable to the Logic Coverage Matrix.

---

## Assumptions

- Quarkus Dev Services provides a fresh PostgreSQL container for `@QuarkusTest` (test profile only). The existing `%test.quarkus.datasource.jdbc.url` and `%test.quarkus.datasource.owner.jdbc.url` entries in `application.properties` MUST be removed — Dev Services only activates when no explicit URL is configured. The dev profile uses the real PostgreSQL instance with Dev Services explicitly disabled.
- Flyway applies migrations in version order from both locations (`db/migration` then `db/test`); V005 and V005.5 are the last scripts applied, so their survey data is available to all test methods.
- The `%test` profile in `application.properties` must set `quarkus.flyway.locations` to `db/migration,db/test` (comma-separated) so both paths are picked up. This is a one-line config change.
- **`QuestionManager` scope change**: `@NormalUIScoped` → `@ApplicationScoped`. `QuestionManager` holds no per-session state (only a transaction-scoped `EntityManager`), so this is safe. This production code change is in scope for this feature.
- Tests call `QuestionManager` methods directly (`deleteDownstreamAnswers` + `buildDownstreamQuestions`) to trigger the full save + downstream processing pipeline. `QuestionService` must also be injected in `QuestionManagerTest`.
- V005.5 respondent `id=1`, `token='test1'` is the canonical "already completed" fixture; tests that mutate state insert a separate respondent to avoid corrupting the Tess Tester fixture for other tests.
- The `replaceTokens` method is `private static`; change to package-private (remove `private` modifier) in `QuestionManager.java` to enable direct unit testing. This is the only other production code change in this feature.
