# Implementation Plan: QuestionManager Integration Tests

**Branch**: `001-question-manager-test` | **Date**: 2026-05-16 | **Spec**: [spec.md](spec.md) | **Status**: Approved  
**Input**: Feature specification from `specs/001-question-manager-test/spec.md`

---

## Summary

Add a `@QuarkusTest` integration test class and a plain JUnit 5 unit test class
for `QuestionManager` — the core survey engine (1,753 LOC, currently 0% covered).
Tests use the V005 Library Survey (all 14 question types, 6 operators, 3 action
types) and V005.5 Tess Tester fixture loaded via Flyway into a Dev Services
PostgreSQL container scoped to the `%test` profile only.

---

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: Quarkus 3.35.3, Hibernate ORM Panache, JUnit 5, `@QuarkusTest`, Flyway  
**Storage**: PostgreSQL via Quarkus Dev Services (`%test` profile only); real PostgreSQL for `%dev`  
**Testing**: `@QuarkusTest` (integration), plain JUnit 5 (unit), `@TestTransaction` (rollback isolation)  
**Target Platform**: JVM — tests run in-process, no server packaging  
**Performance Goals**: Full test class < 60 s after Quarkus is running; unit tests < 1 s  
**Constraints**: Single shared Quarkus instance (no `@TestProfile` restarts); no Micrometer assertions; no `@QuarkusIntegrationTest`  
**Scale/Scope**: ≥ 25 test methods; ≥ 70% line coverage on `QuestionManager.java`

---

## Constitution Check

| Principle | Status | Notes |
|---|---|---|
| I — Spec-first | PASS | `spec.md` written and under review |
| II — Test-first (TDD) | PASS | All test methods written before production changes |
| III — Browserless UI testing | N/A | No Vaadin views in scope |
| IV — 70% coverage gate | REQUIRED | `QuestionManager.java` starts at 0%; plan targets ≥ 70% |
| V — Observability | N/A | No new state-changing operations introduced |

**Blocker to resolve in Phase 0**: `QuestionManager` is `@NormalUIScoped` — a
Vaadin CDI scope that is not active in a `@QuarkusTest` context. Must resolve
before any test can inject it. See Phase 0, Task 3.

---

## Project Structure

```text
specs/001-question-manager-test/
├── spec.md              ← approved feature spec
└── plan.md              ← this file

src/
├── main/
│   └── resources/
│       └── application.properties        ← add %test Flyway + Dev Services config
└── test/
    ├── java/com/elicitsoftware/survey/
    │   ├── QuestionManagerTest.java       ← NEW: @QuarkusTest integration tests
    │   └── QuestionManagerTokenTest.java  ← NEW: plain JUnit 5 unit tests (replaceTokens)
    └── resources/db/test/
        ├── V005__Library_Test.sql         ← already exists (untracked)
        └── V005.5__Tess_Tester_Data.sql   ← already exists (untracked)
```

---

## Phase 0 — Setup & Blockers (do first, nothing else starts until this is green)

### Task 0.1 — Quarkus MCP skills (mandatory before writing any code)
```
quarkus_skills(query="hibernate-orm-panache")
quarkus_skills(query="arc")
quarkus_skills(query="rest")
quarkus_start(projectDir)
quarkus_searchTools(projectDir)
quarkus_callTool("devui-testing_runTests")   ← establish green baseline
```

### Task 0.2 — Configure application.properties for test profile

Edit `src/main/resources/application.properties`:

```properties
# REMOVE these two lines (hardcoded URLs block Dev Services from starting):
# %test.quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5452/survey_test
# %test.quarkus.datasource.owner.jdbc.url=jdbc:postgresql://localhost:5452/survey_test

# Dev Services: test profile only — disabled for dev (real PostgreSQL)
%dev.quarkus.datasource.devservices.enabled=false
%dev.quarkus.datasource.owner.devservices.enabled=false
%test.quarkus.datasource.devservices.reuse=false
%test.quarkus.datasource.owner.devservices.reuse=false

# Flyway: load production migrations + test fixtures in test profile
%test.quarkus.flyway.locations=db/migration,db/test
```

Verify by running `quarkus_callTool("devui-testing_runTests")` — all existing
tests must still pass after this change.

### Task 0.3 — Change QuestionManager scope to @ApplicationScoped

`QuestionManager` is currently `@NormalUIScoped` (Vaadin UI scope), which
cannot be activated in a `@QuarkusTest` context. Change the annotation:

```java
// Before:
@NormalUIScoped
public class QuestionManager {

// After:
@ApplicationScoped
public class QuestionManager {
```

`QuestionManager` holds no per-session state — its only field is an injected
`EntityManager` which is transaction-scoped and thread-safe. This change is
safe and in scope for this feature (approved during clarification).

Verify the running app still works after hot-reload before proceeding.

---

## Phase 1 — Plain JUnit 5 Unit Tests (no Quarkus, no Docker)

**File**: `src/test/java/com/elicitsoftware/survey/QuestionManagerTokenTest.java`

`replaceTokens` is `private static`. Access via package-private visibility
(preferred) — change `private` to package-private (no modifier) in
`QuestionManager.java`. This is the only production code change in this
feature. If the team rejects visibility change, use reflection as a fallback.

| Test method | Scenario | Matrix row |
|---|---|---|
| `given_matchedToken_when_replaceTokens_then_valueSubstituted` | `{NAME\|friend}` + values → "Alice" | Row 10 |
| `given_unmatchedToken_when_replaceTokens_then_defaultKept` | `{NAME\|friend}` + empty map → "friend" | Row 10 |
| `given_multipleTokens_when_replaceTokens_then_allSubstituted` | `{EMAIL}` + `{PHONE}` both in map | Row 10 |
| `given_possessiveSName_when_replaceTokens_then_normalized` | "Dennis's" → "Dennis'" | Row 10 |
| `given_hersPossessive_when_replaceTokens_then_normalized` | " her's " → " her " | Row 10 |

Run via `quarkus_callTool("devui-testing_runTest", {"className":"com.elicitsoftware.survey.QuestionManagerTokenTest"})`.

---

## Phase 2 — Integration Tests: Init & Navigate (read-only, no @TestTransaction needed)

**File**: `src/test/java/com/elicitsoftware/survey/QuestionManagerTest.java`

```java
@QuarkusTest
class QuestionManagerTest {
    @Inject QuestionManager questionManager;
    @Inject QuestionService questionService;   // used for saveAnswer() in mutating tests
    static final int TESS_RESPONDENT_ID = 1;  // from V005.5 fixture
}
```

| Test method | User Story | Matrix row |
|---|---|---|
| `given_tessAnswers_when_navigateWelcome_then_answersInOrder` | US-2 scenario 1 | Row 8 SHOW |
| `given_tessDigitalAccess_when_navigateDigitalSection_then_sectionPresent` | US-2 scenario 2 | Row 3 EQUAL |
| `given_tessMysteryNotChosen_when_navigateCollectionPrefs_then_noMysteryAnswers` | US-2 scenario 3 | Row 4 NOT_EQUAL false |
| `given_tessAnswers_when_navigateTokenSection_then_tokenSubstituted` | US-9 scenario 1 | Row 10 TEXT |
| `given_noTokenSource_when_navigateTokenSection_then_defaultTextShown` | US-9 scenario 2 | Row 10 TEXT fallback |

---

## Phase 3 — Integration Tests: Mutating (all require @TestTransaction)

| Test method | User Story | Matrix row |
|---|---|---|
| `given_freshRespondent_when_init_then_baselineAnswersCreated` | US-1 scenario 1 | Row 1 BOOLEAN |
| `given_freshRespondent_when_initTwice_then_noDuplicates` | US-1 scenario 2 | — |
| `given_freshRespondent_when_termsTrue_then_patronStepCreated` | US-6 scenario 1 | Row 1 BOOLEAN true |
| `given_termsTruePatronVisible_when_termsFalse_then_patronDeleted` | US-6 scenario 2 | Row 1 BOOLEAN false |
| `given_termsFalse_when_termsTrue_then_patronRestored` | US-6 scenario 3 | Row 12 soft-delete restore |
| `given_freshRespondent_when_digitalAccessTrue_then_sectionCreated` | US-3 scenario 1 | Row 3 EQUAL true |
| `given_digitalAccessTrue_when_digitalAccessFalse_then_sectionDeleted` | US-3 scenario 2 | Row 3 EQUAL false |
| `given_freshRespondent_when_mediaTypeContainsDvd_then_mediaPrefCreated` | US-3 scenario 3 | Row 7 CONTAINS true |
| `given_freshRespondent_when_itemTypeNotBook_then_formatQuestionShown` | US-7 scenario 1 | Row 4 NOT_EQUAL true |
| `given_itemTypeNotBook_when_itemTypeBook_then_formatQuestionDeleted` | US-7 scenario 2 | Row 4 NOT_EQUAL false |
| `given_freshRespondent_when_checkoutQty2_then_twoInstancesCreated` | US-4 scenario 1 | Row 9 REPEAT step |
| `given_twoCheckoutInstances_when_qtyDecreasedTo1_then_secondDeleted` | US-4 scenario 2 | Row 9 REPEAT decrement |
| `given_freshRespondent_when_renewalQtyGt0_then_renewalSectionRepeated` | US-4 nested | Row 13 REPEAT section |
| `given_noMediaTypeAnswer_when_navigate_then_q51q52Absent` | US-8 scenario 1 | Row 6 FIELD_EXIST blocked |
| `given_mediaTypeAudiobook_when_addAnswer_then_q51Created` | US-8 scenario 2 | Row 5 GREATER_THAN / Row 11 chain L1 |
| `given_q51Answered_when_addAnswer_then_q52Created` | US-8 scenario 3 | Row 6 FIELD_EXIST true |
| `given_fullChain_when_mediaTypeChanged_then_q51q52BothDeleted` | US-8 scenario 4 | Row 11 cascade |

---

## Phase 4 — Edge Case Tests (all @TestTransaction)

| Test method | Edge case |
|---|---|
| `given_firstVisit_when_navigate_then_answersNotEmpty` | EC-01 |
| `given_nullTextValue_when_addAnswerOnBoolean_then_noDownstream` | EC-03 |
| `given_emptyTextValue_when_addAnswerOnContains_then_noDownstream` | EC-04 |
| `given_repeatQtyZero_when_addAnswer_then_allInstancesDeleted` | EC-05 |
| `given_repeatQty1_when_qtyIncreasedTo3_then_only2NewInstances` | EC-06 |
| `given_softDeletedAnswer_when_conditionBecomesTrue_then_restoredNotDuplicated` | EC-08 |
| `given_tessAnswers_when_navigateAnySection_then_answersInDisplayKeyOrder` | EC-09 |

---

## Phase 5 — Coverage Verification

After all tests are green:

```
quarkus_callTool("devui-testing_runTests")
```

Review JaCoCo report to confirm `QuestionManager.java` ≥ 70% line coverage.
If below 70%, identify uncovered branches in the Logic Coverage Matrix and add
targeted tests for each gap before marking the feature complete.

---

## Complexity Tracking

| Item | Justification |
|---|---|
| `@ApplicationScoped` scope change | `QuestionManager` changed from `@NormalUIScoped` → `@ApplicationScoped`; safe because it holds no per-session state; approved during clarification |
| `%test` JDBC URL removal | Hardcoded `%test` datasource URLs must be removed for Dev Services to start; existing tests must be re-validated after this change |
| `replaceTokens` visibility change | `private static` → package-private enables direct unit testing without reflection; minimal production surface change |
| `QuestionService` injection in tests | Tests call `QuestionService.saveAnswer()` (the real application entry point) rather than `QuestionManager` methods directly; ensures tests exercise the full pipeline |
