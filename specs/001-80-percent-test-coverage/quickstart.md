# Quickstart: Running the Test Suite with Coverage

**Date**: 2026-05-01
**Plan**: [plan.md](plan.md)

---

## Prerequisites

| Requirement | Why |
|-------------|-----|
| Java 25+ | Project compiles at `maven.compiler.release=25` |
| Docker or compatible container runtime (e.g., OrbStack, Rancher Desktop) | TestContainers starts a PostgreSQL container automatically |
| Maven 3.9+ | Build tool |
| No pre-existing PostgreSQL at `localhost:5452` required | TestContainers replaces this dependency |

---

## Running All Tests

```bash
# Full build: compiles, tests, generates coverage report, enforces 80% threshold
mvn verify
```

The coverage report is produced at `target/jacoco-report/index.html` and `target/jacoco-report/jacoco.xml`.

The build fails with a descriptive message if overall instruction coverage falls below 80%.

---

## Running Tests Only (no coverage enforcement)

```bash
# Tests only, no jacoco-check phase
mvn test
```

---

## Viewing the Coverage Report

After `mvn verify`:

```bash
open target/jacoco-report/index.html
```

Navigate to individual packages or classes to see line-by-line coverage highlighting.

---

## Running a Specific Test Class

```bash
mvn test -Dtest=TokenServiceTest
mvn test -Dtest=ElicitTextFieldTest
```

---

## Running Tests by Phase

```bash
# Phase 2 — CDI service tests only
mvn test -Dtest="*ServiceTest,*HealthCheckTest,*ManagerTest"

# Phase 3 — Data-layer tests only
mvn test -Dtest="*EntityTest"

# Phase 4 — Vaadin UI tests only (Karibu-Testing, no @QuarkusTest)
mvn test -Dtest="*ViewTest,Elicit*Test"
```

---

## First-time Setup Notes

1. **Docker must be running** before `mvn test`. TestContainers will pull `docker.io/library/postgres:17` on first run.
2. **No database configuration changes needed.** The TestContainers lifecycle manager overrides the JDBC URLs at runtime.
3. **Flyway migrations run automatically.** Both `db/migration/` and `db/test/` scripts execute before the first test.
4. **Test seed data** is loaded by `db/test/V005__Populate_Test_Data.sql`. Data-mutating tests use `@TestTransaction` to roll back after each test method, keeping seed data intact.

---

## Adding New Tests

### CDI Service Test (Phase 2 pattern)

```java
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class MyServiceTest {

    @Inject
    MyService service;

    @Test
    void givenValidInput_whenProcess_thenSucceeds() {
        var result = service.process("input");
        assertNotNull(result);
    }
}
```

### `@NormalUIScoped` Bean (mock pattern)

```java
@QuarkusTest
class CallerOfUIScopedBeanTest {

    @InjectMock
    @MockitoConfig(convertScopes = true)
    QuestionService questionService;   // @NormalUIScoped — replaced with mock

    @Inject
    TokenService tokenService;         // @RequestScoped — injects normally

    @BeforeEach
    void setup() {
        Mockito.when(questionService.getCurrentSection()).thenReturn(new Section());
    }

    @Test
    void test() { /* ... */ }
}
```

### Data-Layer Entity Test (Phase 3 pattern)

```java
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class RespondentEntityTest {

    @Test
    @TestTransaction
    void givenSeededData_whenFindByToken_thenRespondentReturned() {
        Respondent r = Respondent.find("token", "TEST-TOKEN-001").firstResult();
        assertNotNull(r);
        assertEquals("TEST-TOKEN-001", r.getToken());
    }
}
```

### Vaadin Input Component Test (Phase 4 pattern)

```java
// No @QuarkusTest — Karibu runs standalone JUnit 5
class ElicitTextFieldTest {

    @Test
    void givenTextField_whenValueSet_thenValueReturned() {
        ElicitTextField field = new ElicitTextField("Name");
        field.setValue("Alice");
        assertEquals("Alice", field.getValue());
    }

    @Test
    void givenTextField_whenNoValue_thenGetValueReturnsEmpty() {
        ElicitTextField field = new ElicitTextField("Name");
        assertEquals("", field.getValue());
    }
}
```

### Vaadin View Smoke Test (Phase 4 pattern)

```java
// No @QuarkusTest — Karibu runs standalone JUnit 5
class MainViewTest {
    private static Routes routes;

    @BeforeAll
    static void discoverRoutes() {
        routes = new Routes().autoDiscoverViews("com.elicitsoftware.flow");
    }

    @BeforeEach void setup() { MockVaadin.setup(routes); }
    @AfterEach void teardown() { MockVaadin.tearDown(); }

    @Test
    void givenMainView_whenInstantiated_thenNoExceptionThrown() throws Exception {
        MainView view = new MainView();
        // inject any required mocks via reflection
        view.init();
        UI.getCurrent().add(view);
        // assert at least one child component is present
        assertFalse(view.getChildren().toList().isEmpty());
    }
}
```

---

## CI Integration

The coverage threshold is enforced automatically in CI because `mvn verify` runs the `jacoco-check` goal in the `verify` phase. No CI-specific configuration is needed beyond running `mvn verify`.

**CI prerequisite**: A Docker daemon must be accessible in the CI environment for TestContainers. Most CI providers (GitHub Actions, GitLab CI, Jenkins) support this. Use the `testcontainers.ryuk.disabled=true` property in `src/test/resources/application.properties` if the CI environment does not support Ryuk (resource cleanup daemon):

```properties
# src/test/resources/application.properties (add only if required by CI)
quarkus.testcontainers.devservices.enabled=false   # if using Dev Services path
```

For most CI environments, no change is needed.

---

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| `Could not find a valid Docker environment` | Docker not running | Start Docker Desktop / OrbStack |
| `ContextNotActiveException: NormalUIScoped` | Test injects `@NormalUIScoped` directly | Use `@InjectMock + @MockitoConfig(convertScopes=true)` |
| Coverage report not generated | `mvn test` used instead of `mvn verify` | Use `mvn verify` |
| Build fails with coverage < 80% | Not enough tests | Add tests; check report at `target/jacoco-report/index.html` |
| Flyway migration error in tests | TestContainers users not created | Check `PostgresTestResource.start()` `execInContainer` calls |
| `Class already instrumented` | Both `prepare-agent` and `quarkus-jacoco` active | Remove `prepare-agent` from `jacoco-maven-plugin`; keep report/check only |
