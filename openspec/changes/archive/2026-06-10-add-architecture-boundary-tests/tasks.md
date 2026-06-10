## 1. Test Dependency And Baseline

- [x] 1.1 Add a test-scoped architecture testing dependency if the project does not already have one
- [x] 1.2 Create an architecture test class under `src/test/java` for production package dependency rules
- [x] 1.3 Identify and document any transitional exceptions required for the current codebase

## 2. Boundary Rules

- [x] 2.1 Add a rule preventing controllers from depending directly on repositories or `Neo4jClient`
- [x] 2.2 Add a rule preventing domain classes from depending on controllers, services, DTOs, repositories, or workflow packages
- [x] 2.3 Add a rule preventing feature/application code from depending on controllers
- [x] 2.4 Add an allowlisted rule for direct `Neo4jClient` usage

## 3. Verification

- [x] 3.1 Run the new architecture tests and adjust only explicit documented exceptions
- [x] 3.2 Run `./mvnw test`
- [x] 3.3 Document the boundary rules in the test names or comments so future failures are actionable
