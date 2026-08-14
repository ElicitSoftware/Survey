# Fix Review Findings: Correctness, Security, Testing, and Skill-Compliance Cleanup

## Context

A skill-driven code review (7 parallel agents, one per `.claude/skills/*` area) produced 30
findings against this Quarkus + Vaadin 25 Flow survey app. A follow-up verification pass
confirmed exact file/line locations for the correctness and security items and cross-referenced
them against the existing AIUP use-case docs (`docs/use_cases/UC-001..006.md`).

Two findings are **documented business decisions**, not just code smells:
- UC-005 **BR-013** already says the PDF endpoint is intentionally public, relying on the cache
  key's unguessability — but the key is actually a predictable `time+nanoTime` string, so the
  *implementation* doesn't match what the *business rule* claims. Per the user's decision, the
  fix keeps the endpoint public (avoids the OIDC-redirect-on-new-tab problem noted in
  `application.properties:189`) but replaces the key with a `SecureRandom` value — this
  actually makes BR-013 true rather than aspirational.
- UC-001 **BR-002** already correctly scopes `token.autoRegister` to dev/test only, and
  verification confirmed no prod-profile line enables it — **no code fix needed there**, it was a
  false-positive-adjacent finding (real risk only if someone edits config later).

Per the user's decisions: harden the PDF cache key only (not add a full ownership/auth check),
and reverse the prior "Karibu-Testing/TestBench explicitly out of scope" decision recorded in
`SessionPersistenceServiceTest.java:31` / `UISessionDataServiceTest.java:33-35` — add
Karibu-Testing and start covering the 29 currently-untested Flow classes.

**Where AIUP applies:** this is overwhelmingly a bug-fix/refactor pass, not a requirements
change — most findings never touched a UC doc (per verification: field-validator bugs, CORS,
layout/theming/component-design issues have zero UC references) and should just be fixed with
no AIUP step. Two spots genuinely need the `/use-case-spec` skill run **before or alongside**
the code change, because the code changes what a documented BR asserts:
1. **UC-005** — update BR-013's wording once the cache key is hardened (from "relies on
   unguessability" describing a weak key, to describing an actual `SecureRandom` key).
2. **UC-006** — add a sentence/BR noting that logout now invalidates the underlying
   HTTP/Vaadin session (currently silent on this; adding real invalidation doesn't contradict
   anything, but the doc should say so once true).

No other finding requires `/requirements`, `/entity-model`, or new `/use-case-spec` runs.

## Work Streams

Execute in this order — later streams touch files earlier streams also touch (e.g. testing
needs the bug fixes done first to test correct behavior; component refactors touch the same
input classes as the validator bug fixes).

### 1. Critical correctness bugs (input components)
Fix in `src/main/java/com/elicitsoftware/flow/input/`:
- `ElicitDoubleField.java` — invert the range-validator predicate (currently `value < min ||
  value > max` is used as "is valid"; fix to `value >= min && value <= max`).
- `ElicitTextArea.java` `setValue()` — remove the recursive self-call that causes
  `StackOverflowError` on pre-populated values.
- `ElicitPasswordField.java` `setValue()` — same recursive self-call removal.
- `ElicitTextArea.java` and `ElicitPasswordField.java` length validators — change `||` to `&&`
  (currently a tautology that never rejects out-of-range length).
- `ElicitTimePicker.java` `setValue()` — implement it (currently a `// TODO` stub that drops
  saved time answers); follow the pattern already used in `ElcitDatePicker.java`/
  `ElicitDateTimePicker.java` for parsing/restoring a stored answer.
- While in `ElicitTextField`/`ElicitEmailField`/`ElicitIntegerField`/`ElicitDoubleField`/
  `ElicitPasswordField`/`ElicitTextArea`: merge the double `binder.forField()` calls (required +
  min/max) into one chained `.asRequired().withValidator(...).bind(...)` per field, per the
  forms-and-validation skill.
- `ElicitCheckbox.java` — support an optional "must be checked" required mode instead of
  hard-coding checkboxes as never-required (needed for consent/agreement use cases).

### 2. Security fixes
- **PDF cache key** — `PDFDownloadResource.java`: replace the `"pdf_" + currentTimeMillis +
  "_" + nanoTime` key with a `SecureRandom`-generated token (reuse the approach in
  `RandomStringGenerator.java`). Endpoint stays `permit`-all per the user's decision (avoids the
  OIDC redirect issue called out in the config comment) — the fix is entirely "make the key
  actually unguessable," matching what BR-013 already claims.
  - **AIUP step:** run `/use-case-spec UC-005` afterward to update BR-013's wording to describe
    the real key-generation mechanism.
- **Stored XSS** — `QuestionManager.replaceTokens` (`QuestionManager.java`, token substitution
  using `getKeyValues`): HTML-escape substituted respondent-answer values before they're
  inserted into question/section text that later renders via `innerHTML` in `ElicitHtml.java`
  and `AboutView.java`.
- **Session invalidation on logout** — `LogoutView.java`: in addition to the existing
  `sessionDataService.clear()` call, invalidate the underlying Vaadin/HTTP session
  (`VaadinSession.getCurrent().close()` or equivalent) before the JS redirect, to close the
  session-fixation gap.
  - **AIUP step:** run `/use-case-spec UC-006` afterward to add a sentence documenting that
    logout now invalidates the session, not just app-level state.
- **CORS** — `application.properties:128`: replace `quarkus.http.cors.origins=/.*/` with an
  explicit list of known deployment origins (needs the user's actual origin list — flag as a
  config value to fill in, not a code change).
- **SQL parameterization** — `QuestionManager.getUpstreamAnswerByRelationshipId`: switch the
  string-concatenated native query to bound parameters (`setParameter`), matching every other
  native query in the class. Defense-in-depth; not currently exploitable (ints only) but
  inconsistent.

### 3. Testing — add Karibu-Testing, cover the fixed bugs and critical views
- Add `com.github.mvysny.kaributesting:karibu-testing-v10` (Quarkus/JUnit 5 flavor) as a test
  dependency in `pom.xml`. Reverses the explicit prior rejection documented in
  `SessionPersistenceServiceTest.java:29-34` and `UISessionDataServiceTest.java:31-39` — update/
  remove those class-level Javadoc comments once real UI coverage exists, since they'll no
  longer be accurate.
- Priority test coverage (don't need to hit all 29 classes in one pass, but must cover what
  this plan just changed, so regressions are caught):
  - `ElicitDoubleField`, `ElicitTextArea`, `ElicitPasswordField`, `ElicitTimePicker` — validator
    and `setValue()` round-trip tests (the exact bugs fixed in stream 1).
  - `LogoutView` — verify session invalidation actually happens (the bug fixed in stream 2).
  - `SectionView` — at least one navigation/validation-gating test, since it's the main
    consumer of every `Elicit*` field and currently has zero coverage.
- Traceability: name new tests referencing the relevant UC-XXX per `CLAUDE.md`'s working
  agreement (e.g. `SectionViewNavigationTest` referencing UC-002, `LogoutViewSessionTest`
  referencing UC-006).

### 4. Views & navigation cleanup
In `src/main/java/com/elicitsoftware/flow/`:
- Replace hardcoded route strings (`ui.navigate("section")`, `ui.navigate("report")`, etc. in
  `MainView.java`, `SectionView.java`, `ReviewView.java`) with class-based navigation
  (`ui.navigate(SectionView.class)`).
- Move session/auth guard logic out of `@PostConstruct` and into `BeforeEnterObserver.
  beforeEnter()` with `event.forwardTo()`/`rerouteTo()`, in `SectionView.java`,
  `ReviewView.java`, `MainView.java`.
- Annotate `MainLayout.java` with `@Layout` and drop the explicit `layout = MainLayout.class`
  from every `@Route` (`AboutView`, `VersionView`, `ReportView`, `SectionView`, `ReviewView`,
  `MainView`).
- Rebuild the `SideNav` in `MainLayout.java` using `@Menu` + `MenuConfiguration
  .getMenuEntries()` instead of hardcoded path strings, and add `setMatchNested(true)` to each
  `SideNavItem`.
- `VersionView.java` — move the synchronous `hostname`/`docker inspect` subprocess calls off
  the UI thread (background thread + `UI.access()`, or load on demand via a button) instead of
  blocking `@PostConstruct`.
- Add `@PageTitle`/`HasDynamicTitle` to `AboutView`, `LogoutView`, `VersionView`, `ReportView`.

### 5. Component design cleanup
- `ElicitComponent.java` — make the wrapped `component` field non-public; extend
  `Composite<T>` and add delegating methods (`addValueChangeListener`, `setValueChangeMode`,
  etc.) so `SectionView.java` stops reaching into `.component` directly (currently ~15 call
  sites). Update those call sites in `SectionView.java` to use the new delegating API. Remove
  the dead `@Tag("ElicitComponent")` annotation.
- `ReportCard.java` — replace the `setProperty("innerHTML", ...)` + `executeJs` SVG-styling
  combo with `com.vaadin.flow.component.html.Html` and CSS-based styling.
- Extract a shared `Card`/`CardBase` component (`Composite<VerticalLayout>` with a title +
  content slot) from the near-duplicate scaffolding in `ReviewCard.java` and `ReportCard.java`.
- Rename `ElcitDatePicker.java` → `ElicitDatePicker.java` (fix the typo), updating its one or
  two call sites (`SectionView.java`).

### 6. Layout & responsive fixes
- `AboutView.java` — remove the no-op `setFlexGrow(1)` call (no target component), and replace
  the raw `getStyle().set("flex-grow","1")` spacer with `setFlexGrow(1, spacer)`.
- `ReviewCard.java` / `ReportCard.java` — replace hardcoded `setWidth("60%")` with
  `setWidthFull()` + `setMaxWidth("60%")` (or an equivalent responsive CSS rule) so cards don't
  shrink to ~225px on mobile.
- `ReviewView.java` / `ReportView.java` — drop `setSizeFull()` on the route-level layout, or
  wrap the growing card list in a `Scroller`, to avoid the double-scrollbar overflow trap.
- `ReviewCard.java` — add `setWrap(true)` to the question/answer row layouts so long text
  reflows instead of overflowing horizontally.
- `ReportCard.java` — remove the invalid `getStyle().set("align", "center")` (not a real CSS
  property).

### 7. Theming fixes
- `styles.css` — fix `@import 'lumo/lumo-utility.css'` → `@import 'lumo/utility.css'` (the
  actual path in the Lumo theme jar; currently 404s, so `LumoUtility.Padding.SMALL` usage in
  `MainLayout.java` has no effect).
- `fonts.css` and `brand-typography.css` — remove the universal `* { font-family: ... }`
  selectors; set the font once via `--lumo-font-family` on `html` instead, so icon-font glyphs
  aren't put at risk.
- `AppConfig.java` — add `@StyleSheet(Lumo.STYLESHEET)` before the existing
  `@StyleSheet("context://styles.css")`, and drop the in-CSS `@import 'lumo/lumo.css'` from
  `styles.css`, per the theming skill's recommended loading mechanism.
- Consolidate the duplicate brand→Lumo variable mappings currently split between `styles.css`
  and `brand-colors.css` into one file (single source of truth for `--lumo-primary-color`,
  `--lumo-error-color`, etc.).

## Verification

- **Unit/service tests:** run the full existing suite via the Quarkus Dev MCP test runner
  (`devui-testing_runTests`) after each stream — must stay green throughout, especially after
  stream 1 (validator fixes) and stream 2 (security fixes).
- **New Karibu tests (stream 3):** run via the same Dev MCP test runner once added; confirm the
  new `ElicitDoubleField`/`ElicitTextArea`/`ElicitPasswordField`/`ElicitTimePicker` tests fail on
  the old code (sanity-check by temporarily reverting stream 1, optional) and pass after the fix.
- **Manual browser check (`/run` or Quarkus dev mode):**
  - Enter a survey token, answer a double-range question at the boundary and out-of-bounds
    values, a textarea/password field with a saved value on revisit, and a time-picker question
    — confirm no StackOverflow, correct validation, and saved values restored.
  - Generate a PDF, confirm the download link/key looks like a random token rather than a
    timestamp string (check the URL).
  - Log out, then check dev tools/Application tab that the session cookie is invalidated
    (or attempt to reuse the old session and confirm it's rejected).
  - Resize the browser to a mobile width and check `ReviewView`/`ReportView` cards no longer
    truncate to ~60% width and text wraps instead of overflowing.
- **AIUP doc sync:** after stream 2, run `/use-case-spec UC-005` and `/use-case-spec UC-006` to
  regenerate/hand-edit BR-013 and add the session-invalidation note; diff against the current
  files under `docs/use_cases/` to confirm only the intended BRs changed.
