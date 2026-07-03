---
paths:
  - "src/test/**/*.java"
---

# Test Conventions

## Layer → Test style

| Target | Setup | Why |
|---|---|---|
| Domain (`domain/**`) | Plain JUnit, no Spring | POJO logic; fastest |
| Service (`service/**`) | `@ExtendWith(MockitoExtension.class)` + `@Mock` for repositories, manual `new EmployeeService(...)` in `@BeforeEach` | Unit; isolate from DB |
| Repository (`repository/**`) — query behavior itself | `@DataJpaTest` | Slice; real JPA against H2. Each test runs in a rolled-back transaction, so commit-time effects and tx boundaries are NOT exercised here |
| Service↔DB integration (`service/**`, `*IntegrationTest` / `*ConcurrentTest`) — tx boundaries, constraints, real wiring | `@SpringBootTest` | Real commit/rollback semantics and concurrency are part of the behavior under test |
| Controller (`controller/**`) | `@SpringBootTest` + `@AutoConfigureMockMvc` + `MockMvcTester`, services as `@MockitoBean` | Wire/serialization + filters (auth) |

Use the narrowest slice that covers the behavior. Regular service tests should avoid `@SpringBootTest`.

## Naming

- Class: `<Target>Test` (e.g. `EmployeeServiceTest`).
- Method names are short and English; the human-readable scenario goes in `@DisplayName` (Korean is the project default — match existing files).
- Group cases with `@Nested` per endpoint or per method under test, with `@DisplayName` on the nested class (e.g. `"POST /employee"`).

## DB dialect

- Before writing a DB test, read the query under test first (JPQL vs native SQL, dialect-specific functions or syntax).
- Tests run against H2 by default; H2 passing does NOT prove MySQL behavior.
- If the logic is MySQL-specific (native query, dialect functions, `ON DUPLICATE KEY UPDATE`, index/locking behavior, …), the test must verify it against real MySQL (e.g. Testcontainers-MySQL), not H2.

## Determinism

- Inject `Clock` (already used in services); in tests pass a fixed `Clock` or supply `ZonedDateTime` literals explicitly. Never call `ZonedDateTime.now()` directly in test arrange code.
- Time zone in tests: `ZoneId.of("Asia/Seoul")` matches the `EmployeeBuilder` default.

## Tooling

- `MockMvcTester` (not legacy `MockMvc`).
- `@MockitoBean` (not the old `@MockBean`).
- Test package mirrors `src/main/java/...`.
