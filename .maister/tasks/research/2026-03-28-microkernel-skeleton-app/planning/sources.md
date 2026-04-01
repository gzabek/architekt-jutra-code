# Research Sources

## Codebase Sources

### Key Files
- `pom.xml` — Maven build configuration, all dependencies, Java version, Spring Boot parent
- `src/main/java/pl/devstyle/aj/AjApplication.java` — Application entry point, Spring Boot bootstrap
- `src/main/resources/application.properties` — Application configuration (currently minimal)
- `src/test/java/pl/devstyle/aj/AjApplicationTests.java` — Context loading test
- `src/test/java/pl/devstyle/aj/TestAjApplication.java` — Test runner configuration
- `src/test/java/pl/devstyle/aj/TestcontainersConfiguration.java` — PostgreSQL TestContainers setup (reference for DB config patterns)

### Configuration Files
- `.gitignore` — Existing ignore patterns (relevant for adding frontend artifacts)
- `mvnw` / `mvnw.cmd` — Maven wrapper (confirms Maven-based build)
- `.mvn/` — Maven wrapper configuration directory

### Directories
- `src/main/java/pl/devstyle/aj/` — Main source root (currently only AjApplication.java)
- `src/main/resources/` — Resources root (application.properties)
- `src/main/resources/db/changelog/` — Liquibase migrations (empty, but directory exists)
- `src/test/java/pl/devstyle/aj/` — Test source root

## Documentation Sources

### Project Documentation
- `.maister/docs/project/architecture.md` — Current architecture description, planned package structure, microkernel pattern goals
- `.maister/docs/project/tech-stack.md` — Technology choices, dependency versions, pending decisions (JPA vs jOOQ, plugin framework)

### Backend Standards
- `.maister/docs/standards/backend/api.md` — REST API design conventions
- `.maister/docs/standards/backend/models.md` — JPA entity modeling standards
- `.maister/docs/standards/backend/queries.md` — Database query standards
- `.maister/docs/standards/backend/jooq.md` — jOOQ query standards
- `.maister/docs/standards/backend/migrations.md` — Database migration standards

### Frontend Standards
- `.maister/docs/standards/frontend/components.md` — Component design standards
- `.maister/docs/standards/frontend/css.md` — CSS methodology standards
- `.maister/docs/standards/frontend/accessibility.md` — Accessibility requirements
- `.maister/docs/standards/frontend/responsive.md` — Responsive design approach

### Global Standards
- `.maister/docs/standards/global/minimal-implementation.md` — Build only what is needed (critical for skeleton scope)
- `.maister/docs/standards/global/conventions.md` — File structure, documentation, version control conventions
- `.maister/docs/standards/global/coding-style.md` — Naming and formatting conventions

## External Sources

### Spring Boot + React Integration
- Spring Boot official documentation: serving static content, frontend integration
- `frontend-maven-plugin` documentation — Maven plugin for Node/npm builds
- Vite documentation — Modern React build tooling
- Spring Boot DevTools + React dev server proxy configuration patterns

### Microkernel Architecture
- PF4J (Plugin Framework for Java) documentation and Spring Boot integration guides
- Java ServiceLoader / SPI pattern documentation
- Spring plugin patterns (Spring Plugin project, custom `@Component` scanning)
- Microkernel architecture pattern references (software architecture literature)

### Docker + PostgreSQL
- Spring Boot Docker Compose support documentation (spring-boot-docker-compose module)
- Docker Compose PostgreSQL service configuration reference
- TestContainers + Docker Compose coexistence patterns
- Spring Boot profiles for dev vs test database configuration

### Build Tooling
- Maven multi-module project structure patterns
- Maven exec-maven-plugin / frontend-maven-plugin for frontend build integration
- Spring Boot Maven plugin packaging options
