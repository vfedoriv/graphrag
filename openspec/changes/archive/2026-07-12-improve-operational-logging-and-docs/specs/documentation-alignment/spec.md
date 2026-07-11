## ADDED Requirements

### Requirement: Documented stack versions are build-backed
The repository SHALL keep documented Java, Spring Boot, Spring AI, and LangChain4j version facts aligned with the Maven build configuration wherever those facts are stated.

#### Scenario: Maven version property changes
- **WHEN** a documented stack version changes in `pom.xml`
- **THEN** README, AGENTS, and CLAUDE do not retain conflicting version claims or stale version-specific links

#### Scenario: Documentation alignment check runs
- **WHEN** the documentation alignment regression check runs
- **THEN** it identifies conflicting shared stack/configuration facts before merge
