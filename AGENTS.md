# AGENTS.md

This file guides agentic coding assistants working in this repo.
Follow the existing architecture, test patterns, and naming conventions.

## Project Snapshot
- Language: Java 21
- Framework: Spring Boot 3.5.5
- Build Tool: Gradle (`./gradlew`)
- Database: H2 (dev), MySQL 8.0 (prod)
- Test stack: JUnit 5, Spring Boot test, MockMvc, Mockito
- REST Docs: Asciidoctor + Spring REST Docs

## Build / Run / Test
### Build
- Build (includes tests + REST Docs): `./gradlew build`
- Clean build: `./gradlew clean build`
- Generate REST Docs only: `./gradlew asciidoctor`

### Run
- Run app with dev profile (H2): `./gradlew bootRun`

### Tests
- All tests: `./gradlew test`
- Single test class:
  - `./gradlew test --tests "com.company.officecommute.service.employee.EmployeeServiceTest"`
- Single test method:
  - `./gradlew test --tests "com.company.officecommute.service.employee.EmployeeServiceTest.authenticate_success"`

### Lint / Format
- No dedicated linter/formatter configured in repo.
- Follow existing style; do not introduce new tooling.

## External Dependencies / Env
- External holiday API requires `PUBLIC_API_SERVICE_KEY`.
- Dev profile uses H2 TCP: `jdbc:h2:tcp://localhost/~/test`.

## Architecture Conventions
- Layering: Controller → Service → Repository → Database
- Domain layer hosts business rules and invariants.
- Auth is session-based using `AuthInterceptor`.
- Role enum values: `MANAGER`, `MEMBER`.

## Coding Style (Java)
### Imports
- Group imports: project → third-party → `java.*`.
- No wildcard imports.
- Keep static imports grouped and at the end of import list.

### Formatting
- Indent with 4 spaces.
- One blank line between class members (fields, constructors, methods).
- Method parameters wrap with standard alignment (see controllers/services).
- Keep braces on the same line (K&R style).

### Naming
- Packages: all lowercase (`com.company.officecommute.*`).
- Classes: `PascalCase` (e.g., `EmployeeService`).
- Methods/fields: `camelCase` (e.g., `updateEmployeeTeamName`).
- Constants: `UPPER_SNAKE_CASE`.
- Tests: `*Test`, `*ConcurrentTest`, `*ConcurrencyTest`.

### Types / DTOs
- Use Java `record` for request/response DTOs when immutable.
- Annotate request DTOs with `jakarta.validation` constraints.
- Prefer domain objects for business logic; map to DTOs in service/controller.

### Controllers
- Use Spring MVC annotations (`@RestController`, `@PostMapping`, etc.).
- Validate request bodies with `@Valid`.
- Use `@SessionAttribute` for session-scoped auth info.
- Authorization checks throw `ForbiddenException`.
- Return `ResponseEntity` or void for simple endpoints.

### Services
- Annotate with `@Service` and `@Transactional`.
- Use `@Transactional(readOnly = true)` for read paths.
- Prefer repository methods returning Optional; throw `IllegalArgumentException` for domain errors.

### Domain Model
- Enforce invariants in constructors or private validators.
- Use descriptive exception messages for invalid state.
- Prefer value objects or helper classes for domain calculations.

### Repositories
- Spring Data JPA repositories in `repository.*` packages.
- Query methods are named explicitly (`findByEmployeeCode`, etc.).

## Error Handling
- Business validation errors use `IllegalArgumentException`.
- Authorization errors use `ForbiddenException`.
- Global handler maps errors to `ErrorResult` / `ValidationErrorResult`.
- Log with SLF4J at `warn` for client errors, `error` for system failures.

## Tests
### Patterns
- Service tests: `@SpringBootTest` or `MockitoExtension` as needed.
- Controller tests: `@SpringBootTest` + `@AutoConfigureMockMvc` or `@WebMvcTest`.
- Use `MockMvcTester` for request/response assertions.
- Use `@DisplayName` and nested classes for grouped scenarios.

### Fixtures
- Builder utilities: `EmployeeBuilder`, `Employees`, `Teams`.
- Keep test data localized and descriptive.
- Prefer `BDDMockito.given()` and AssertJ assertions.

## REST Docs
- REST Docs output lives in `build/generated-snippets`.
- `./gradlew asciidoctor` depends on tests and generates HTML.

## Pitfalls
- REST Docs only renders after tests pass.
- External holiday API calls require `PUBLIC_API_SERVICE_KEY`.
- Session-based auth means missing `@SessionAttribute` yields `FORBIDDEN`.
- H2 dev profile expects TCP URL `jdbc:h2:tcp://localhost/~/test`.

## Repo Rules (Cursor/Copilot)
- No `.cursor/rules/`, `.cursorrules`, or `.github/copilot-instructions.md` found.

## Working With This Repo
- Keep changes minimal and consistent with existing style.
- Avoid adding new dependencies unless necessary.
- Do not modify build tooling without explicit request.
- When in doubt, follow patterns in existing controllers/services/tests.
