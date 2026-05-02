# Tasks: Achieve 80% Test Coverage

**Input**: Design documents from `specs/001-80-percent-test-coverage/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅
**Branch**: `initial_spec_testing`
**Date**: 2026-05-01 (updated after clarification session)

## Format: `[ID] [P?] [Story?] Description — file path`

- **[P]**: Can run in parallel (different files, no incomplete dependencies)
- **[US1/US2/US3/US4]**: Which user story this task belongs to
- File paths shown are relative to repository root

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add all new Maven dependencies, fix the `ElcitDatePicker` typo, remove the
hardcoded test database URL, and wire the SC-006 automated gate. Must complete before any
test can compile.

- [ ] T001 Add `jacoco-maven-plugin` 0.8.14 (report + check executions, no prepare-agent) to `pom.xml` — exclusion patterns MUST include `com/elicitsoftware/flow/*View`, `com/elicitsoftware/flow/*Layout`, `com/elicitsoftware/report/*`, and `**/*$$*` per FR-003 and research.md Area 1; additionally add per-package `<rule>` elements: `com.elicitsoftware.model` at ≥ 75% instruction and `com.elicitsoftware.flow.input` at ≥ 70% instruction (SC-003, SC-004)
- [ ] T002 Add `org.testcontainers:postgresql` test dependency (no version — BOM managed) to `pom.xml`
- [ ] T003 [P] Add `io.quarkus:quarkus-junit5-mockito` test dependency (no version — BOM managed) to `pom.xml`
- [ ] T004 [P] Add `io.quarkus:quarkus-junit-component` test dependency (no version — BOM managed) to `pom.xml`
- [ ] T005 [P] Add `com.github.mvysny.kaributesting:karibu-testing-v10:2.7.0` test dependency to `pom.xml`
- [ ] T006 [P] Add `com.github.mvysny.kaributesting:karibu-testing-v23:2.7.0` test dependency to `pom.xml`
- [ ] T007 Remove the two hardcoded `%test` JDBC URL lines from `src/main/resources/application.properties` (`%test.quarkus.datasource.jdbc.url=...` and `%test.quarkus.datasource.owner.jdbc.url=...`)
- [ ] T008 [P] Rename `src/main/java/com/elicitsoftware/flow/input/ElcitDatePicker.java` → `ElicitDatePicker.java` and update all imports and usages throughout the codebase (FR-021) — `src/main/java/com/elicitsoftware/flow/input/ElicitDatePicker.java`
- [ ] T009 Add `exec-maven-plugin` execution bound to the `verify` phase that runs `grep -rE '@Test' src/test/java | grep -v '^\s*//' | wc -l`, stores the result in a property, and fails the build if the count is below 80 (SC-006 automated gate, enforced on every `mvn verify` locally and in CI) — `pom.xml`

**Checkpoint**: `mvn compile -DskipTests` succeeds with all new dependencies; `ElicitDatePicker` compiles cleanly under its corrected name

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core test infrastructure that every `@QuarkusTest` class shares. Must be
complete before user story phases 3–6 begin.

- [ ] T010 Create `PostgresTestResource` (implements `QuarkusTestResourceLifecycleManager`, `TestResourceScope.GLOBAL`) in `src/test/java/com/elicitsoftware/PostgresTestResource.java` — starts `postgres:17` container, creates `elicit_owner`/`survey_user`/`surveyadmin_user`/`surveyreport_user` users, returns JDBC URL map (skeleton from research.md Area 2)
- [ ] T011 Annotate all existing `@QuarkusTest` classes (`TokenServiceTest`, `TokenServiceTestAutoRegister`, `QuestionServiceTest`, `RespondentTest`, `RandomStringGeneratorTest`) with `@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)` — `src/test/java/com/elicitsoftware/survey/`
- [ ] T012 Verify the entire existing test suite passes with TestContainers replacing `localhost:5452` — run `mvn test` and confirm no connection-refused failures
- [ ] T013 Confirm `UITestProfile` is NOT needed: `@InjectMock @MockitoConfig(convertScopes = true)` on the injected bean handles `@NormalUIScoped` isolation without a `@TestProfile` annotation (FR-012). Delete `UITestProfile.java` stub if accidentally created; document this decision in a comment on T020.

**Checkpoint**: `mvn test` on the existing 13 tests passes against the containerised database

---

## Phase 3: User Story 1 — JaCoCo Baseline and Enforcement (Priority: P1)

**Goal**: Running `mvn verify` produces a JaCoCo HTML+XML report under `target/jacoco-report/` and fails the build with a coverage violation message (current coverage is below 80%).

**Independent Test**: Run `mvn verify`. Observe build failure with JaCoCo threshold message. Confirm `target/jacoco-report/index.html` and `target/jacoco-report/jacoco.xml` are present. Confirm Vaadin view classes and `report/` package are excluded from the threshold.

- [ ] T014 [US1] Verify `jacoco-maven-plugin` executions (added in T001) are wired correctly: run `mvn verify` and confirm (a) `target/jacoco-quarkus.exec` exists after Surefire, (b) `target/jacoco-report/` is created by the `report` goal, (c) `check` goal fires and prints a threshold violation, (d) `@{argLine}` is not duplicated or overridden (confirm Surefire still uses the single `quarkus-jacoco`-supplied agent), (e) `report/` classes absent from violation output, (f) `flow/*View` classes absent from violation output — `pom.xml`
**Checkpoint**: Build fails with a JaCoCo coverage message; HTML report browsable; `report/` and view classes excluded; `@{argLine}` non-interference confirmed. US1 acceptance criteria 1, 3, and 4 verified.

---

## Phase 4: User Story 2 — CDI Business-Logic Service Tests (Priority: P1)

**Goal**: CDI service layer (`com.elicitsoftware` root + `etl/` + `util/`, excluding `report/`) reaches ≥ 80% instruction coverage.

**Independent Test**: Run `mvn test`. CDI service tests pass. JaCoCo report for the `com.elicitsoftware` root package (excluding `flow/` and `report/`) shows ≥ 80% instruction coverage.

### TokenService Tests (expand existing)

- [ ] T017 [P] [US2] Expand `TokenServiceTest` to cover token deactivation, invalid-token login, and already-deactivated-token edge case — `src/test/java/com/elicitsoftware/survey/TokenServiceTest.java`

### Utility Tests (no DB dependency)

- [ ] T018 [P] [US2] Expand `RandomStringGeneratorTest` to cover edge cases (min length, max length, charset constraints) — `src/test/java/com/elicitsoftware/survey/RandomStringGeneratorTest.java`
- [ ] T019 [P] [US2] Create `DisplayKeyTest` covering all constant lookups and null/unknown-key handling — `src/test/java/com/elicitsoftware/DisplayKeyTest.java`

### QuestionService Tests (rewrite — `@NormalUIScoped` workaround)

- [ ] T020 [US2] Rewrite `QuestionServiceTest` using `@InjectMock @MockitoConfig(convertScopes = true)` on `QuestionService` and `QuestionManager`; cover answer-save, survey-navigation, and finalization paths — `src/test/java/com/elicitsoftware/survey/QuestionServiceTest.java`

### New CDI Service Test Classes

- [ ] T021 [P] [US2] Create `QuestionManagerTest` covering section/step lifecycle and question sequencing; use `@InjectMock @MockitoConfig(convertScopes = true)` for `@NormalUIScoped` deps — `src/test/java/com/elicitsoftware/service/QuestionManagerTest.java`
- [ ] T022 [P] [US2] Create `SessionPersistenceServiceTest` covering session-save and session-restore with `@TestTransaction` rollback — `src/test/java/com/elicitsoftware/service/SessionPersistenceServiceTest.java`
- [ ] T023 [P] [US2] Create `ETLServiceTest` covering successful ETL execution, partial-failure path, and malformed-respondent-data handling — `src/test/java/com/elicitsoftware/service/ETLServiceTest.java`
- [ ] T024 [P] [US2] Create `DatabaseHealthCheckTest` (`@QuarkusTest`) covering healthy-database response and graceful degraded-state response — `src/test/java/com/elicitsoftware/service/DatabaseHealthCheckTest.java`
- [ ] T025 [P] [US2] [FR-012b] Create `BrandUtilTest` covering default-brand path (no custom brand file present), custom-brand-file present, and missing-file fallback — `src/test/java/com/elicitsoftware/service/BrandUtilTest.java`

**Checkpoint**: `mvn test` passes all Phase 3–4 tests. JaCoCo instruction coverage for `com.elicitsoftware` root package (excl. `flow/` and `report/`) is ≥ 80%.

---

## Phase 5: User Story 3 — Data Layer Tests (Priority: P2)

**Goal**: Panache entity finders, persistence, and referential-integrity constraints verified. `com.elicitsoftware.model` reaches ≥ 75% instruction coverage.

**Independent Test**: Run `mvn test`. Data-layer tests pass. JaCoCo report for `com.elicitsoftware.model` shows ≥ 75% instruction coverage.

### Flyway Baseline

- [ ] T026 [US3] Create `FlywayMigrationTest` with a `@QuarkusTest` method asserting `Survey.count() > 0` after migrations + seed data run, verifying clean schema creation (FR-017) — `src/test/java/com/elicitsoftware/model/FlywayMigrationTest.java`

### Entity Tests (each `@QuarkusTest @TestTransaction`)

- [ ] T027 [P] [US3] Create `RespondentEntityTest` covering `findByToken()`, persist + retrieve round-trip for all mapped fields, active/inactive respondent handling (FR-014, FR-015); also verify `Respondent → Answer` cascade-delete: deleting a `Respondent` with linked `Answer` records MUST cascade correctly (FR-016) — `src/test/java/com/elicitsoftware/model/RespondentEntityTest.java`
- [ ] T028 [P] [US3] Create `AnswerEntityTest` covering: persist + retrieve round-trip for all mapped fields (FR-015); `Answer → Question` referential-integrity constraint violation on orphaned answer (FR-016); `Respondent → Answer` cascade-delete behavior — `src/test/java/com/elicitsoftware/model/AnswerEntityTest.java`
- [ ] T029 [P] [US3] Create `SurveyEntityTest` covering `listAll()`, `findById()`, and `Survey → Step` relationship traversal (FR-014) — `src/test/java/com/elicitsoftware/model/SurveyEntityTest.java`
- [ ] T030 [P] [US3] Create `QuestionEntityTest` covering: `Question → QuestionType` and `Question → SelectGroup` relationships and named query methods (FR-014); `Question → SelectGroup` referential-integrity constraint — orphaned question with non-existent SelectGroup MUST fail (FR-016) — `src/test/java/com/elicitsoftware/model/QuestionEntityTest.java`
- [ ] T031 [P] [US3] Create `SectionEntityTest` covering: `Section → SectionsQuestion` relationship traversal; empty-children edge case (no SectionsQuestion rows); `Section → SectionsQuestion` referential-integrity — deleting a Section with existing SectionsQuestion children MUST behave per configured cascade (FR-014, FR-016) — `src/test/java/com/elicitsoftware/model/SectionEntityTest.java`
- [ ] T032 [P] [US3] Create `SelectGroupEntityTest` covering `SelectGroup → SelectItem` relationship, empty-items edge case, and `SelectGroup` with no children (FR-014); also verify `SelectGroup → SelectItem` referential-integrity constraint: persisting a `SelectItem` with a non-existent `SelectGroup` parent MUST fail (FR-016) — `src/test/java/com/elicitsoftware/model/SelectGroupEntityTest.java`
- [ ] T033 [P] [US3] Expand `RespondentTest` (existing) to remove commented-out tests, verify active + inactive state, add missing field round-trip assertions — `src/test/java/com/elicitsoftware/survey/RespondentTest.java`

**Checkpoint**: `mvn test` passes all Phase 5 tests. JaCoCo instruction coverage for `com.elicitsoftware.model` is ≥ 75%.

---

## Phase 6: User Story 4 — Vaadin UI Layer Tests (Priority: P3)

**Goal**: All 15 custom Elicit input components reach ≥ 70% instruction coverage. All 7 Vaadin views have at least one smoke test. Tests use Karibu-Testing; no browser or full server required.

**Independent Test**: Run `mvn test`. Karibu-Testing tests pass. JaCoCo report for `com.elicitsoftware.flow.input` shows ≥ 70% instruction coverage. View smoke tests complete without exception.

### Custom Input Component Tests (no `@QuarkusTest` needed for pure-component tests)

- [ ] T034 [P] [US4] Create `ElicitTextFieldTest` covering value set/get, empty value, null handling — `src/test/java/com/elicitsoftware/flow/input/ElicitTextFieldTest.java`
- [ ] T035 [P] [US4] Create `ElicitTextAreaTest` covering value set/get, empty, null — `src/test/java/com/elicitsoftware/flow/input/ElicitTextAreaTest.java`
- [ ] T036 [P] [US4] Create `ElicitIntegerFieldTest` covering integer value, non-numeric string rejection, null — `src/test/java/com/elicitsoftware/flow/input/ElicitIntegerFieldTest.java`
- [ ] T037 [P] [US4] Create `ElicitDoubleFieldTest` covering numeric value, null, precision boundary — `src/test/java/com/elicitsoftware/flow/input/ElicitDoubleFieldTest.java`
- [ ] T038 [P] [US4] Create `ElicitEmailFieldTest` covering valid email, invalid email format rejection, empty — `src/test/java/com/elicitsoftware/flow/input/ElicitEmailFieldTest.java`
- [ ] T039 [P] [US4] Create `ElicitPasswordFieldTest` covering value set/get, empty — `src/test/java/com/elicitsoftware/flow/input/ElicitPasswordFieldTest.java`
- [ ] T040 [P] [US4] Create `ElicitCheckboxTest` covering checked/unchecked state, default state — `src/test/java/com/elicitsoftware/flow/input/ElicitCheckboxTest.java`
- [ ] T041 [P] [US4] Create `ElicitCheckboxGroupTest` covering single selection, multi-selection, empty selection — `src/test/java/com/elicitsoftware/flow/input/ElicitCheckboxGroupTest.java`
- [ ] T042 [P] [US4] Create `ElicitRadioButtonGroupTest` covering single selection, deselect, null — `src/test/java/com/elicitsoftware/flow/input/ElicitRadioButtonGroupTest.java`
- [ ] T043 [P] [US4] Create `ElicitComboBoxTest` covering item selection, empty items (edge case from spec), null — `src/test/java/com/elicitsoftware/flow/input/ElicitComboBoxTest.java`
- [ ] T044 [P] [US4] Create `ElicitMultiSelectComboBoxTest` covering multi-value selection, empty, clear — `src/test/java/com/elicitsoftware/flow/input/ElicitMultiSelectComboBoxTest.java`
- [ ] T045 [P] [US4] Create `ElicitDatePickerTest` (uses the renamed `ElicitDatePicker` — FR-021) covering date set/get, null — `src/test/java/com/elicitsoftware/flow/input/ElicitDatePickerTest.java`
- [ ] T046 [P] [US4] Create `ElicitDateTimePickerTest` covering datetime set/get, null — `src/test/java/com/elicitsoftware/flow/input/ElicitDateTimePickerTest.java`
- [ ] T047 [P] [US4] Create `ElicitTimePickerTest` covering time set/get, null — `src/test/java/com/elicitsoftware/flow/input/ElicitTimePickerTest.java`
- [ ] T048 [P] [US4] Create `ElicitHtmlTest` covering HTML content set/get, empty — `src/test/java/com/elicitsoftware/flow/input/ElicitHtmlTest.java`

### Vaadin View Smoke Tests (require `MockVaadin.setup(routes)`)

- [ ] T049 [US4] Create `MainViewTest` with `@BeforeAll routes = new Routes().autoDiscoverViews("com.elicitsoftware.flow")`, `@BeforeEach MockVaadin.setup(routes)`, `@AfterEach MockVaadin.tearDown()`, smoke test asserting view instantiates without exception and login form is present — `src/test/java/com/elicitsoftware/flow/views/MainViewTest.java`
- [ ] T050 [P] [US4] Create `SectionViewTest` with MockVaadin setup; smoke test asserting view instantiates without exception and question area is present; inject mocks via reflection for `@NormalUIScoped` deps — `src/test/java/com/elicitsoftware/flow/views/SectionViewTest.java`
- [ ] T051 [P] [US4] Create `ReviewViewTest` with MockVaadin setup; smoke test asserting view instantiates without exception — `src/test/java/com/elicitsoftware/flow/views/ReviewViewTest.java`
- [ ] T052 [P] [US4] Create `ReportViewTest` with MockVaadin setup; smoke test asserting view instantiates without exception — `src/test/java/com/elicitsoftware/flow/views/ReportViewTest.java`
- [ ] T053 [P] [US4] Create `AboutViewTest` with MockVaadin setup; smoke test asserting view instantiates without exception — `src/test/java/com/elicitsoftware/flow/views/AboutViewTest.java`
- [ ] T054 [P] [US4] Create `LogoutViewTest` with MockVaadin setup; smoke test asserting view instantiates without exception — `src/test/java/com/elicitsoftware/flow/views/LogoutViewTest.java`
- [ ] T055 [P] [US4] Create `VersionViewTest` with MockVaadin setup; smoke test asserting view instantiates without exception — `src/test/java/com/elicitsoftware/flow/views/VersionViewTest.java`

**Checkpoint**: All Phase 6 tests pass. JaCoCo instruction coverage for `com.elicitsoftware.flow.input` is ≥ 70%.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verify the full enforcement gate, document the setup, and confirm all success criteria from spec.md are measurably met.

- [ ] T056 Run `mvn verify` end-to-end and confirm: (a) build passes with ≥ 80% instruction coverage at BUNDLE level (SC-001), (b) per-package rules pass: `com.elicitsoftware.model` ≥ 75% (SC-003), `com.elicitsoftware.flow.input` ≥ 70% (SC-004), (c) `target/jacoco-report/index.html` browsable (SC-001/FR-005), (d) `report/` package and Vaadin view classes absent from threshold — `pom.xml` + manual verification
- [ ] T057 [P] Update `README.md` with test-suite prerequisites (Docker required for TestContainers), run command (`mvn verify`), and report location (`target/jacoco-report/`) — per SC-008
- [ ] T058 Validate SC-006 automated gate (T009): confirm the `exec-maven-plugin` `grep` count check fires during `mvn verify` and fails the build when `@Test` count drops below 80 — trigger by temporarily commenting out one test method, run `mvn verify`, confirm build fails with count message, then restore
- [ ] T059 Validate SC-007 deliberate-deletion scenario: temporarily delete one tested method body, run `mvn verify`, confirm build fails with JaCoCo threshold error, then restore

**Checkpoint**: All 8 success criteria (SC-001 through SC-008) verified. Feature complete.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 completion — **BLOCKS all user story phases**
- **Phase 3 (US1 — JaCoCo)**: Depends on Phase 2; validates pom.xml from Phase 1
- **Phase 4 (US2 — CDI)**: Depends on Phase 2; can run in parallel with Phase 3 once T012 passes
- **Phase 5 (US3 — Data)**: Depends on Phase 2; can run in parallel with Phases 3 and 4
- **Phase 6 (US4 — Vaadin)**: Depends on Phase 2 + T008 (rename); can run in parallel with Phases 3, 4, and 5
- **Phase 7 (Polish)**: Depends on all Phases 3–6 complete

### User Story Dependencies

- **US1 (P1)**: After Foundational — no story dependencies
- **US2 (P1)**: After Foundational — no story dependencies; highest coverage ROI
- **US3 (P2)**: After Foundational — no story dependencies
- **US4 (P3)**: After Foundational + T008 (rename must precede T045) — no other story dependencies

### Within Each Phase

- Tasks marked `[P]` within a phase may be worked in parallel (different files)
- T001–T009 (pom.xml and application.properties edits) should be batched into one commit
- T008 (rename) must precede T045 (`ElicitDatePickerTest`) — only this ordering constraint within Phase 6

---

## Parallel Execution Examples

### Phase 4 (US2 — CDI): All new service test classes are independent

```bash
# Can all start after T010–T013 (Foundational) complete:
T021: QuestionManagerTest.java
T022: SessionPersistenceServiceTest.java
T023: ETLServiceTest.java
T024: DatabaseHealthCheckTest.java
T025: BrandUtilTest.java
```

### Phase 5 (US3 — Data): All entity tests are independent

```bash
# Can all start after T010–T013 complete:
T027: RespondentEntityTest.java
T028: AnswerEntityTest.java
T029: SurveyEntityTest.java
T030: QuestionEntityTest.java
T031: SectionEntityTest.java
T032: SelectGroupEntityTest.java
```

### Phase 6 (US4 — Vaadin): Component tests fully parallel after T008 (rename)

```bash
# T034–T048 (15 component tests): all independent, fully parallel
# T049 (MainViewTest) first for route discovery baseline
# T050–T055 (6 view smoke tests): parallel after T049
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 — both P1)

1. Complete Phase 1: Setup (pom.xml, application.properties, rename, SC-006 gate)
2. Complete Phase 2: Foundational (TestContainers + UITestProfile)
3. Complete Phase 3: US1 JaCoCo gate — confirms enforcement mechanism works
4. Complete Phase 4: US2 CDI service tests — highest coverage ROI
5. **STOP and VALIDATE**: Run `mvn verify` — if CDI layer ≥ 80% and BUNDLE threshold passes, MVP delivered; Phases 5–6 are incremental additions

### Incremental Delivery

1. Phase 1 + 2 → Foundation ready
2. Phase 3 → JaCoCo gate active (build fails correctly)
3. Phase 4 → CDI coverage climbs; gate may pass at this point
4. Phase 5 → Data layer coverage verified independently
5. Phase 6 → Vaadin UI coverage verified independently
6. Phase 7 → Full `mvn verify` green; README updated; SC-001–SC-008 confirmed

### Total Task Count

| Phase | Tasks | Notes |
|-------|-------|-------|
| Phase 1: Setup | 9 | pom.xml + application.properties + rename + SC-006 gate (exec-maven-plugin) |
| Phase 2: Foundational | 4 | TestContainers resource + existing test wiring + UITestProfile confirm |
| Phase 3: US1 | 1 | JaCoCo gate verification (T015/T016 merged into T014 sub-checks) |
| Phase 4: US2 | 9 | CDI service tests (expand + new); BrandUtil mapped to FR-012b |
| Phase 5: US3 | 8 | Data-layer entity tests (FK integrity expanded in T028/T030/T031) |
| Phase 6: US4 | 22 | 15 component tests + 7 view smoke tests |
| Phase 7: Polish | 4 | End-to-end verify + README + SC-003/SC-004 per-package gates |
| **Total** | **57** | |

### Parallel Opportunities

- **Phase 1**: T003–T006 (4 dependency adds) + T008 (rename) fully parallel after T001–T002
- **Phase 4**: T021–T025 (5 new service test classes) fully parallel
- **Phase 5**: T027–T033 (7 entity tests) fully parallel
- **Phase 6**: T034–T048 (15 component tests) fully parallel; T050–T055 (6 view tests) parallel after T049
- **Phase 7**: T057, T058 parallel (T059 sequential after T058)

### Test Count Projection

| Baseline | New methods (conservative, ~2 per new class) | Projected |
|----------|----------------------------------------------|-----------|
| 13 active `@Test` methods | ~70 new methods across 40 new test classes | ≥ 83 methods |

This satisfies SC-006 (≥ 80 active `@Test` methods, automatically enforced by T009).

