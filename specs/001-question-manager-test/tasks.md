# Tasks: QuestionManager Integration Tests

**Input**: `specs/001-question-manager-test/spec.md` + `specs/001-question-manager-test/plan.md`  
**Branch**: `001-question-manager-test`

> **TDD Rule**: Every test task below follows Red → Green. Write the method, confirm it FAILS (or compiles but assertions fail), then confirm it PASSES. No test is marked done until it is green in the Dev MCP test runner.

---

## Phase 1: MCP Setup ⚠️ MUST COMPLETE BEFORE WRITING ANY CODE

**Purpose**: Load extension knowledge and establish a green baseline.

- [ ] T001 Call `quarkus_skills(query="hibernate-orm-panache")` and read the output
- [ ] T002 Call `quarkus_skills(query="arc")` and read the output
- [ ] T003 Call `quarkus_skills(query="rest")` and read the output
- [ ] T004 `quarkus_start(projectDir)` — wait for `running` status
- [ ] T005 `quarkus_searchTools(projectDir)` — confirm `devui-testing_runTests` is available
- [ ] T006 `quarkus_callTool("devui-testing_runTests")` — **all existing tests must be green before proceeding**

**Checkpoint**: All existing tests green. Extension skills loaded.

---

## Phase 2: Foundation ⚠️ BLOCKS ALL SUBSEQUENT PHASES

**Purpose**: Production code and config changes required before any test can compile or run.

- [ ] T007 **`application.properties`** — remove `%test.quarkus.datasource.jdbc.url` and `%test.quarkus.datasource.owner.jdbc.url` (hardcoded URLs block Dev Services)
- [ ] T008 **`application.properties`** — add Dev Services config:
  ```
  %dev.quarkus.datasource.devservices.enabled=false
  %dev.quarkus.datasource.owner.devservices.enabled=false
  %test.quarkus.datasource.devservices.reuse=false
  %test.quarkus.datasource.owner.devservices.reuse=false
  ```
- [ ] T009 **`application.properties`** — add Flyway locations for test profile:
  ```
  %test.quarkus.flyway.locations=db/migration,db/test
  ```
- [ ] T010 `quarkus_callTool("devui-testing_runTests")` — verify all existing tests still green after config changes
- [ ] T011 **`QuestionManager.java`** — change scope annotation from `@NormalUIScoped` to `@ApplicationScoped`
- [ ] T012 Verify hot-reload completes without errors after scope change (`quarkus_logs` + `devui-exceptions_getLastException`)
- [ ] T013 **`QuestionManager.java`** — change `private static String replaceTokens(...)` to package-private (remove `private` modifier)
- [ ] T014 Create `src/test/java/com/elicitsoftware/survey/QuestionManagerTokenTest.java` — empty class with `@Test` stub, confirm it compiles
- [ ] T015 Create `src/test/java/com/elicitsoftware/survey/QuestionManagerTest.java` — skeleton with `@QuarkusTest`, `@Inject QuestionManager`, `@Inject QuestionService`, constant `TESS_RESPONDENT_ID = 1`, confirm it compiles and deploys

**Checkpoint**: Both test files compile. Existing tests still green. Dev Services container starts for test profile.

---

## Phase 3: US-5 — Token Unit Tests (plain JUnit, no DB, no Docker)

**Goal**: Verify `replaceTokens()` in isolation — sub-second, no infrastructure.  
**Independent Test**: Run `QuestionManagerTokenTest` alone; it must complete in < 1 s.

- [ ] T016 [P] [US5] RED — write `given_matchedToken_when_replaceTokens_then_valueSubstituted` in `QuestionManagerTokenTest.java` — confirm compile + red
- [ ] T017 [P] [US5] GREEN — confirm T016 passes (// Matrix row 10)
- [ ] T018 [P] [US5] RED — write `given_unmatchedToken_when_replaceTokens_then_defaultKept` — confirm red
- [ ] T019 [P] [US5] GREEN — confirm T018 passes (// Matrix row 10)
- [ ] T020 [P] [US5] RED — write `given_multipleTokens_when_replaceTokens_then_allSubstituted` — confirm red
- [ ] T021 [P] [US5] GREEN — confirm T020 passes (// Matrix row 10)
- [ ] T022 [P] [US5] RED — write `given_possessiveSName_when_replaceTokens_then_normalized` — confirm red
- [ ] T023 [P] [US5] GREEN — confirm T022 passes (// Matrix row 10)
- [ ] T024 [P] [US5] RED — write `given_hersPossessive_when_replaceTokens_then_normalized` — confirm red
- [ ] T025 [P] [US5] GREEN — confirm T024 passes (// Matrix row 10)

**Checkpoint**: 5 token unit tests green. `QuestionManagerTokenTest` completes < 1 s.

---

## Phase 4: US-1 + US-2 — Init & Navigate (read-only, Tess Tester fixture)

**Goal**: Verify baseline init and navigation against pre-loaded Tess Tester data.  
**Independent Test**: These tests are read-only — no `@TestTransaction` needed; Tess Tester fixture is not modified.

- [ ] T026 [US1] RED — write `given_freshRespondent_when_init_then_baselineAnswersCreated` (`@TestTransaction`) — confirm red
- [ ] T027 [US1] GREEN — confirm T026 passes (// Matrix row 1)
- [ ] T028 [US1] RED — write `given_freshRespondent_when_initTwice_then_noDuplicates` (`@TestTransaction`) — confirm red
- [ ] T029 [US1] GREEN — confirm T028 passes
- [ ] T030 [US2] RED — write `given_tessAnswers_when_navigateWelcome_then_answersInOrder` — confirm red
- [ ] T031 [US2] GREEN — confirm T030 passes (// Matrix row 8 SHOW)
- [ ] T032 [US2] RED — write `given_tessDigitalAccess_when_navigateDigitalSection_then_sectionPresent` — confirm red
- [ ] T033 [US2] GREEN — confirm T032 passes (// Matrix row 3 EQUAL)
- [ ] T034 [US2] RED — write `given_tessMysteryNotChosen_when_navigateCollectionPrefs_then_noMysteryAnswers` — confirm red
- [ ] T035 [US2] GREEN — confirm T034 passes (// Matrix row 4 NOT_EQUAL false)

**Checkpoint**: 5 navigate/init tests green. Tess Tester fixture intact.

---

## Phase 5: US-6 — BOOLEAN Operator (P1, top-level survey gate)

**Goal**: Prove the terms-acceptance gate works for both paths and survives a true→false→true cycle.  
**Independent Test**: All 3 scenarios use `@TestTransaction`; Tess Tester unaffected.

- [ ] T036 [US6] RED — write `given_freshRespondent_when_termsTrue_then_patronStepCreated` (`@TestTransaction`) — confirm red
- [ ] T037 [US6] GREEN — confirm T036 passes (// Matrix row 1 BOOLEAN true)
- [ ] T038 [US6] RED — write `given_termsTruePatronVisible_when_termsFalse_then_patronDeleted` (`@TestTransaction`) — confirm red
- [ ] T039 [US6] GREEN — confirm T038 passes (// Matrix row 1 BOOLEAN false)
- [ ] T040 [US6] RED — write `given_termsFalse_when_termsTrue_then_patronRestored` (`@TestTransaction`) — confirm red
- [ ] T041 [US6] GREEN — confirm T040 passes (// Matrix row 12 soft-delete restore)

**Checkpoint**: BOOLEAN operator fully covered — true-path, false-path, restore cycle.

---

## Phase 6: US-3 — EQUAL + CONTAINS SHOW Actions (P2)

**Goal**: Verify conditional section creation and deletion for EQUAL and CONTAINS operators.

- [ ] T042 [US3] RED — write `given_freshRespondent_when_digitalAccessTrue_then_sectionCreated` (`@TestTransaction`) — confirm red
- [ ] T043 [US3] GREEN — confirm T042 passes (// Matrix row 3 EQUAL true)
- [ ] T044 [US3] RED — write `given_digitalAccessTrue_when_digitalAccessFalse_then_sectionDeleted` (`@TestTransaction`) — confirm red
- [ ] T045 [US3] GREEN — confirm T044 passes (// Matrix row 3 EQUAL false)
- [ ] T046 [US3] RED — write `given_freshRespondent_when_mediaTypeContainsDvd_then_mediaPrefCreated` (`@TestTransaction`) — confirm red
- [ ] T047 [US3] GREEN — confirm T046 passes (// Matrix row 7 CONTAINS true)

**Checkpoint**: EQUAL and CONTAINS operators covered.

---

## Phase 7: US-7 — NOT_EQUAL Operator (P2)

**Goal**: Prove NOT_EQUAL is not accidentally implemented as EQUAL.

- [ ] T048 [US7] RED — write `given_freshRespondent_when_itemTypeNotBook_then_formatQuestionShown` (`@TestTransaction`) — confirm red
- [ ] T049 [US7] GREEN — confirm T048 passes (// Matrix row 4 NOT_EQUAL true)
- [ ] T050 [US7] RED — write `given_itemTypeNotBook_when_itemTypeBook_then_formatQuestionDeleted` (`@TestTransaction`) — confirm red
- [ ] T051 [US7] GREEN — confirm T050 passes (// Matrix row 4 NOT_EQUAL false)

**Checkpoint**: NOT_EQUAL operator covered — both directions.

---

## Phase 8: US-4 — REPEAT Action (P2)

**Goal**: Verify step and section repetition and decrement/delete behavior.

- [ ] T052 [US4] RED — write `given_freshRespondent_when_checkoutQty2_then_twoInstancesCreated` (`@TestTransaction`) — confirm red
- [ ] T053 [US4] GREEN — confirm T052 passes (// Matrix row 9 REPEAT step)
- [ ] T054 [US4] RED — write `given_twoCheckoutInstances_when_qtyDecreasedTo1_then_secondDeleted` (`@TestTransaction`) — confirm red
- [ ] T055 [US4] GREEN — confirm T054 passes (// Matrix row 9 REPEAT decrement)
- [ ] T056 [US4] RED — write `given_freshRespondent_when_renewalQtyGt0_then_renewalSectionRepeated` (`@TestTransaction`) — confirm red
- [ ] T057 [US4] GREEN — confirm T056 passes (// Matrix row 13 REPEAT section nested)

**Checkpoint**: REPEAT action covered — step repeat, decrement, nested section repeat.

---

## Phase 9: US-8 — FIELD_EXIST + 3-Level Nested Chain (P2)

**Goal**: Verify each level of the Q50→Q51→Q52 chain gates independently and cascade-deletes.

- [ ] T058 [US8] RED — write `given_noMediaTypeAnswer_when_navigate_then_q51q52Absent` — confirm red
- [ ] T059 [US8] GREEN — confirm T058 passes (// Matrix row 6 FIELD_EXIST blocked)
- [ ] T060 [US8] RED — write `given_mediaTypeAudiobook_when_addAnswer_then_q51Created` (`@TestTransaction`) — confirm red
- [ ] T061 [US8] GREEN — confirm T060 passes (// Matrix row 11 chain L1)
- [ ] T062 [US8] RED — write `given_q51Answered_when_addAnswer_then_q52Created` (`@TestTransaction`) — confirm red
- [ ] T063 [US8] GREEN — confirm T062 passes (// Matrix row 6 FIELD_EXIST true)
- [ ] T064 [US8] RED — write `given_fullChain_when_mediaTypeChanged_then_q51q52BothDeleted` (`@TestTransaction`) — confirm red
- [ ] T065 [US8] GREEN — confirm T064 passes (// Matrix row 11 cascade delete)

**Checkpoint**: FIELD_EXIST and full nested chain covered — all 4 scenarios green.

---

## Phase 10: US-9 — TEXT Action (P2)

**Goal**: Verify token substitution in display text at navigation time.

- [ ] T066 [US9] RED — write `given_tessAnswers_when_navigateTokenSection_then_tokenSubstituted` — confirm red
- [ ] T067 [US9] GREEN — confirm T066 passes (// Matrix row 10 TEXT)
- [ ] T068 [US9] RED — write `given_noTokenSource_when_navigateTokenSection_then_defaultTextShown` (`@TestTransaction`) — confirm red
- [ ] T069 [US9] GREEN — confirm T068 passes (// Matrix row 10 TEXT fallback)

**Checkpoint**: TEXT action covered — substitution and fallback.

---

## Phase 11: Edge Cases

**Goal**: Harden boundary conditions not covered by user stories.

- [ ] T070 [EC] RED — write `given_firstVisit_when_navigate_then_answersNotEmpty` (`@TestTransaction`) — confirm red (EC-01)
- [ ] T071 [EC] GREEN — confirm T070 passes
- [ ] T072 [EC] RED — write `given_nullTextValue_when_saveAnswerOnBoolean_then_noDownstream` (`@TestTransaction`) — confirm red (EC-03)
- [ ] T073 [EC] GREEN — confirm T072 passes
- [ ] T074 [EC] RED — write `given_emptyTextValue_when_saveAnswerOnContains_then_noDownstream` (`@TestTransaction`) — confirm red (EC-04)
- [ ] T075 [EC] GREEN — confirm T074 passes
- [ ] T076 [EC] RED — write `given_repeatQtyZero_when_saveAnswer_then_allInstancesDeleted` (`@TestTransaction`) — confirm red (EC-05)
- [ ] T077 [EC] GREEN — confirm T076 passes
- [ ] T078 [EC] RED — write `given_repeatQty1_when_qtyIncreasedTo3_then_only2NewInstances` (`@TestTransaction`) — confirm red (EC-06)
- [ ] T079 [EC] GREEN — confirm T078 passes
- [ ] T080 [EC] RED — write `given_softDeletedAnswer_when_conditionBecomesTrue_then_restoredNotDuplicated` (`@TestTransaction`) — confirm red (EC-08)
- [ ] T081 [EC] GREEN — confirm T080 passes
- [ ] T082 [EC] RED — write `given_tessAnswers_when_navigateAnySection_then_answersInDisplayKeyOrder` — confirm red (EC-09)
- [ ] T083 [EC] GREEN — confirm T082 passes

**Checkpoint**: All 7 edge cases green.

---

## Phase 12: Coverage Verification

**Goal**: Confirm ≥ 70% line coverage on `QuestionManager.java` and all matrix rows satisfied.

- [ ] T084 `quarkus_callTool("devui-testing_runTests")` — full suite green, note final pass count
- [ ] T085 Review JaCoCo report for `QuestionManager.java` line coverage — must be ≥ 70%
- [ ] T086 Walk Logic Coverage Matrix (spec.md) row by row — confirm every true-path and false-path has a corresponding green test
- [ ] T087 If coverage < 70%: identify uncovered lines, add targeted test methods, repeat T084–T086
- [ ] T088 Commit all changes with message referencing spec + coverage result

**Checkpoint**: All tests green. Coverage ≥ 70%. Logic Coverage Matrix fully satisfied. Ready for PR.

---

## Dependencies & Execution Order

- **Phase 1** — No dependencies. Start here.
- **Phase 2** — Depends on Phase 1. Blocks everything else.
- **Phase 3** — Depends on Phase 2 only. Can run in parallel with Phases 4–10.
- **Phases 4–10** — All depend on Phase 2. Can run in any order or in parallel (different test methods, no file conflicts).
- **Phase 11** — Depends on Phase 2. Can interleave with Phases 3–10.
- **Phase 12** — Depends on all previous phases complete.

### Parallel Opportunities

Once Phase 2 is complete, a single developer works sequentially P1→P2→P3 through user stories. With two developers:
- Developer A: Phases 3, 5, 7, 9 (token tests + BOOLEAN + NOT_EQUAL + chain)
- Developer B: Phases 4, 6, 8, 10 (navigate + EQUAL/CONTAINS + REPEAT + TEXT)
- Both: Phase 11 edge cases split by EC number
- Both: Phase 12 together

---

## Notes

- Every RED task must be verified via `quarkus_callTool("devui-testing_runTest", {"className":"..."})` — do not assume failure without running
- Every GREEN task means the test runner reports PASSED — not just "no compile error"
- `@TestTransaction` is required on every test that calls `QuestionService.saveAnswer()` or `questionManager.init()`
- If `devui-exceptions_getLastException` reports an error, fix it before writing the next test
- Commit after each Phase checkpoint
