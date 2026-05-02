# Research: Achieve 80% Test Coverage

**Phase**: 0 — Outline & Research
**Date**: 2026-05-01
**Plan**: [plan.md](plan.md)

---

## Research Area 1: JaCoCo + Quarkus Integration

### Decision
Add `jacoco-maven-plugin` with **report** and **check** goals only. Do **not** add `prepare-agent`. Keep `quarkus-jacoco` as the sole JaCoCo agent provider.

### Rationale
`quarkus-jacoco` already injects the JaCoCo agent and writes execution data to `target/jacoco-quarkus.exec`. Adding `prepare-agent` again causes double-instrumentation errors ("class already instrumented"). The Maven plugin is only needed to:
1. Convert `jacoco-quarkus.exec` into HTML/XML reports (for CI and developer consumption)
2. Enforce the 80% threshold by failing the build

The existing `<argLine>@{argLine}</argLine>` in Surefire and Failsafe is already correctly wired for `quarkus-jacoco` and must not be changed.

### Recommended Version
`jacoco-maven-plugin` **0.8.14** (released 2025-10-10, latest stable)

### Maven Plugin XML

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.14</version>
    <executions>
        <execution>
            <id>jacoco-report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
            <configuration>
                <dataFile>${project.build.directory}/jacoco-quarkus.exec</dataFile>
                <outputDirectory>${project.build.directory}/jacoco-report</outputDirectory>
                <formats>
                    <format>HTML</format>
                    <format>XML</format>
                </formats>
                <excludes>
                    <exclude>com/elicitsoftware/flow/*View.class</exclude>
                    <exclude>com/elicitsoftware/flow/*Layout.class</exclude>
                    <exclude>com/elicitsoftware/report/*.class</exclude>
                    <exclude>**/*$$*.class</exclude>
                </excludes>
            </configuration>
        </execution>
        <execution>
            <id>jacoco-check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <dataFile>${project.build.directory}/jacoco-quarkus.exec</dataFile>
                <excludes>
                    <exclude>com/elicitsoftware/flow/*View</exclude>
                    <exclude>com/elicitsoftware/flow/*Layout</exclude>
                    <exclude>com/elicitsoftware/report/*</exclude>
                    <exclude>**/*$$*</exclude>
                </excludes>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>INSTRUCTION</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Note on exclude path formats**: In `<configuration><excludes>`, patterns use class-file path style (`com/elicitsoftware/flow/*View.class`). In `<check><excludes>`, patterns use JVM internal style (`com/elicitsoftware/flow/*View` — no `.class` suffix).

### `quarkus.jacoco.report` property
Leave at default (`false`). The Maven plugin handles reporting. Using both creates redundant reports and adds complexity.

### Alternatives Considered
- Using `quarkus.jacoco.report=true` property only: rejected because it produces HTML only (no XML for CI) and has no threshold enforcement.
- Replacing `quarkus-jacoco` with `jacoco-maven-plugin:prepare-agent`: rejected because it would break the Quarkus classloader coverage instrumentation and require `exclClassLoaders` workarounds.

---

## Research Area 2: TestContainers for Self-Contained PostgreSQL Tests

### Decision
Use **raw TestContainers** via `QuarkusTestResourceLifecycleManager` with `TestResourceScope.GLOBAL`. This replaces the hardcoded `localhost:5452` dependency.

### Rationale
The project uses two datasources (`default` and `owner`) with a custom `elicit_owner` PostgreSQL user. Quarkus Dev Services starts with a single `quarkus` superuser and cannot easily configure the additional users needed by Flyway migrations. A `QuarkusTestResourceLifecycleManager` gives full control over user creation before Flyway runs.

### Maven Dependency
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
    <!-- Version managed by Quarkus BOM — do not specify explicitly -->
</dependency>
```

### Test Resource Implementation

```java
// src/test/java/com/elicitsoftware/PostgresTestResource.java
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("docker.io/library/postgres:17")
            .withDatabaseName("survey_test")
            .withUsername("quarkus")
            .withPassword("quarkus");

    @Override
    public Map<String, String> start() {
        POSTGRES.start();
        // Create project-specific users that Flyway migrations require
        POSTGRES.execInContainer("psql", "-U", "quarkus", "-d", "survey_test", "-c",
            "CREATE USER elicit_owner WITH PASSWORD 'SURVEYPW' SUPERUSER;" +
            "CREATE USER survey_user WITH PASSWORD 'SURVEYPW';" +
            "CREATE USER surveyadmin_user WITH PASSWORD 'SURVEYPW';" +
            "CREATE USER surveyreport_user WITH PASSWORD 'SURVEYPW';");
        String jdbcUrl = POSTGRES.getJdbcUrl();
        return Map.of(
            "quarkus.datasource.jdbc.url", jdbcUrl,
            "quarkus.datasource.owner.jdbc.url", jdbcUrl
        );
    }

    @Override
    public void stop() { POSTGRES.stop(); }
}
```

Activate globally so all `@QuarkusTest` classes share one container:
```java
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
```

### `application.properties` changes
Remove the two hardcoded `%test` JDBC URL lines:
```properties
# DELETE these two lines:
# %test.quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5452/survey_test
# %test.quarkus.datasource.owner.jdbc.url=jdbc:postgresql://localhost:5452/survey_test
```
The lifecycle manager returns these values at runtime.

### Flyway
The existing `quarkus.flyway.owner.migrate-at-start=true` and `%test.quarkus.flyway.owner.locations=db/migration,db/test` config is correct as-is. Flyway runs automatically before any test method executes. Seed data in `db/test/V005__Populate_Test_Data.sql` is available to all tests.

### Test Isolation
Use `@TestTransaction` (Quarkus annotation) to roll back data-mutating tests after each test method, preserving the V005 seed data across the run.

### Alternatives Considered
- Dev Services (auto-started container): rejected because the two-datasource / `elicit_owner` user setup is too fragile with `init-script-path` (script runs before user creation ordering is predictable).
- Keeping external `localhost:5452`: rejected because SC-005 requires a self-contained test suite.

---

## Research Area 3: Karibu-Testing with Vaadin 25

### Decision
Use **Karibu-Testing 2.7.0** (`karibu-testing-v10` + `karibu-testing-v23` artifacts).

### Rationale
Karibu-Testing 2.7.0 is the latest release (Feb 2026) and the first version to fully support Vaadin 25+. Earlier versions (2.4.x) only support up to Vaadin 24.8. The `v23` extras artifact adds support for `MultiSelectComboBox`, `VirtualList`, and `TabSheet` — components likely used by the 15 custom Elicit input wrappers.

### Maven Dependencies
```xml
<dependency>
    <groupId>com.github.mvysny.kaributesting</groupId>
    <artifactId>karibu-testing-v10</artifactId>
    <version>2.7.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.github.mvysny.kaributesting</groupId>
    <artifactId>karibu-testing-v23</artifactId>
    <version>2.7.0</version>
    <scope>test</scope>
</dependency>
```

### CDI Integration Pattern
Karibu-Testing does **not** start a CDI container. `@Inject` fields in views remain `null`. Inject mocks via reflection in `@BeforeEach`:

```java
Field f = MyView.class.getDeclaredField("myService");
f.setAccessible(true);
f.set(viewInstance, mockService);
```

Call `@PostConstruct` methods manually after field injection.

### Custom Component Test Skeleton
For components without CDI, no `MockVaadin.setup()` is needed:

```java
@Test
void textFieldRoundTrip() {
    ElicitTextField component = new ElicitTextField("Label");
    component.setValue("hello");
    assertEquals("hello", component.getValue());
}
```

### View Test Skeleton
```java
@BeforeAll static void discoverRoutes() {
    routes = new Routes().autoDiscoverViews("com.elicitsoftware.flow");
}
@BeforeEach void setup() { MockVaadin.setup(routes); }
@AfterEach void teardown() { MockVaadin.tearDown(); }

@Test void viewInstantiatesWithoutException() throws Exception {
    MainView view = new MainView();
    // inject mocks via reflection
    UI.getCurrent().add(view);
    assertNotNull(_get(view, TextField.class));
}
```

### Known Limitations
- Karibu-Testing does not support server-side rendering push (WebSocket) simulation.
- `UI.getCurrent()` returns a mock UI; some Vaadin Router navigation edge cases may not behave identically to production.
- Vaadin 25 specific components may have minor incompatibilities in 2.7.0; pin to this version and monitor the Karibu changelog for patch releases.

### Alternatives Considered
- Selenium / TestBench: rejected by constitution (Principle III).
- Plain JUnit instantiation without MockVaadin: insufficient — Vaadin component internals (e.g., `ElementFactory`) require a mock server environment even for simple components.

---

## Research Area 4: `@NormalUIScoped` Bean Testing Strategy

### Decision
Use a **three-tier strategy** based on the type of test:

| Scenario | Strategy |
|---|---|
| Testing a class that **calls** a `@NormalUIScoped` bean | `@InjectMock` + `@MockitoConfig(convertScopes = true)` |
| Testing the `@NormalUIScoped` bean's business logic in isolation | Extract to a `@RequestScoped` helper service; test the helper |
| Narrow unit tests with no full Quarkus server | `@QuarkusComponentTest` |

### Rationale
`@NormalUIScoped` fails in `@QuarkusTest` because `VaadinSession.getCurrent()` and `UI.getCurrent()` are both `null` (no Vaadin servlet request processed). The CDI context reports inactive and throws `ContextNotActiveException` on first proxy method call. There is no Quarkus extension that provides a mock Vaadin context.

### Maven Dependency
```xml
<!-- Quarkus-managed Mockito wrapper — version from BOM -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5-mockito</artifactId>
    <scope>test</scope>
</dependency>
<!-- Lightweight component tests without full server -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit-component</artifactId>
    <scope>test</scope>
</dependency>
```

**Do not** add raw `org.mockito:mockito-core` directly — version mismatches with Quarkus ArC byte-code subclassing cause proxy errors. Use `quarkus-junit5-mockito` only.

### `@InjectMock` Skeleton
```java
@QuarkusTest
class SomeServiceTest {

    @InjectMock
    @MockitoConfig(convertScopes = true)
    QuestionService questionService;   // @NormalUIScoped — replaced with ApplicationScoped mock

    @Inject
    TokenService tokenService;         // @RequestScoped — injects normally

    @Test
    void whenTokenServiceCallsQuestion_mockIsUsed() {
        Mockito.when(questionService.getCurrentSection()).thenReturn(mockSection);
        // test TokenService behavior that delegates to QuestionService
    }
}
```

### Recommended Long-Term Architecture
Extract business logic from `@NormalUIScoped` beans into `@RequestScoped` services:
- `QuestionService` / `QuestionManager` → keep UI state in the scoped bean, extract computation to a `@RequestScoped QuestionLogicService`
- Tests target `QuestionLogicService` directly with no scope workaround needed

This is the correct TDD/architectural fix per Constitution Principle II ("eliminates untestable designs").

### Alternatives Considered
- Manual `UI.setCurrent()` / `VaadinSession.setCurrent()` in a test resource: fragile, couples tests to Vaadin internals, rejected.
- `@Mock @ApplicationScoped` subclass in `src/test/java`: applies globally (all tests get the same stub), too coarse-grained for varied test scenarios, rejected as primary strategy.

---

## Open Questions (Resolved)

| Question | Resolution |
|----------|-----------|
| Does adding `jacoco-maven-plugin` conflict with `quarkus-jacoco`? | Yes if `prepare-agent` is added; resolved by adding report/check goals only |
| Which TestContainers approach works for two-datasource setup? | `QuarkusTestResourceLifecycleManager` with explicit user creation |
| Which Karibu version supports Vaadin 25? | 2.7.0 (released Feb 2026) |
| Can `@NormalUIScoped` beans be injected in `@QuarkusTest`? | No; use `@InjectMock + @MockitoConfig(convertScopes=true)` or extract to `@RequestScoped` |
| Should `quarkus.jacoco.report=true` be used? | No; Maven plugin handles reporting with more capability |
