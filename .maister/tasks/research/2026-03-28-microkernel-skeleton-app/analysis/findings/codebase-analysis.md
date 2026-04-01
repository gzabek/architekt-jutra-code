# Codebase Analysis Findings

## Research Question
How to build a microkernel architecture skeleton app with Spring Boot backend, React frontend, and containerized PostgreSQL?

## Focus
Understanding the current state of the "aj" project codebase to identify what exists and what needs to be added.

---

## 1. Project Directory Structure

**Source**: Full directory listing of `/Users/kuba/Projects/dna_ai/code/`
**Confidence**: High (100%) -- direct observation

```
code/
├── .claude/settings.local.json
├── .gitattributes
├── .gitignore
├── .maister/
│   ├── docs/
│   │   ├── INDEX.md
│   │   ├── project/
│   │   │   ├── architecture.md
│   │   │   └── tech-stack.md
│   │   └── standards/
│   │       ├── backend/ (api.md, jooq.md, migrations.md, models.md, queries.md)
│   │       ├── frontend/ (accessibility.md, components.md, css.md, responsive.md)
│   │       └── global/ (coding-style.md, commenting.md, conventions.md, error-handling.md, minimal-implementation.md, validation.md)
│   └── tasks/
├── .mvn/wrapper/maven-wrapper.properties
├── CLAUDE.md
├── mvnw
├── mvnw.cmd
├── pom.xml
└── src/
    ├── main/
    │   ├── java/pl/devstyle/aj/
    │   │   └── AjApplication.java
    │   └── resources/
    │       ├── application.properties
    │       ├── db/changelog/          (empty)
    │       ├── static/                (empty)
    │       └── templates/             (empty)
    └── test/java/pl/devstyle/aj/
        ├── AjApplicationTests.java
        ├── TestAjApplication.java
        └── TestcontainersConfiguration.java
```

### Key Observations
- Standard Maven project layout (`src/main/java`, `src/main/resources`, `src/test/java`)
- Base package: `pl.devstyle.aj`
- No sub-packages exist yet -- flat structure with only the application entry point
- Empty directories prepared: `db/changelog/`, `static/`, `templates/`
- No Docker/container configuration files (no `Dockerfile`, no `docker-compose.yml`)
- No frontend code present (no React app, no `package.json`)

---

## 2. pom.xml -- Build Configuration

**Source**: `pom.xml` (Lines 1-101)
**Confidence**: High (100%) -- direct file content

### Project Coordinates
- **GroupId**: `pl.devstyle`
- **ArtifactId**: `aj`
- **Version**: `0.0.1-SNAPSHOT`
- **Java Version**: 25 (Line 30: `<java.version>25</java.version>`)

### Parent POM
- Spring Boot 4.0.5 (`spring-boot-starter-parent`)
- All dependency versions managed by Spring Boot BOM

### Runtime Dependencies (4)
| Dependency | Scope | Purpose |
|---|---|---|
| `spring-boot-starter-data-jpa` | compile | ORM / JPA support |
| `spring-boot-starter-jooq` | compile | Type-safe SQL query builder |
| `spring-boot-starter-liquibase` | compile | Database migrations |
| `spring-boot-starter-webmvc` | compile | REST API / web support |
| `postgresql` | runtime | PostgreSQL JDBC driver |

### Test Dependencies (5)
| Dependency | Scope | Purpose |
|---|---|---|
| `spring-boot-starter-data-jpa-test` | test | JPA test utilities |
| `spring-boot-starter-jooq-test` | test | jOOQ test utilities |
| `spring-boot-starter-liquibase-test` | test | Liquibase test utilities |
| `spring-boot-starter-webmvc-test` | test | Web MVC test utilities (MockMvc) |
| `spring-boot-testcontainers` | test | TestContainers Spring integration |
| `testcontainers-junit-jupiter` | test | JUnit 5 TestContainers |
| `testcontainers-postgresql` | test | PostgreSQL container |

### Build Plugins
- Only `spring-boot-maven-plugin` configured (Line 94-97)
- No jOOQ code generation plugin
- No Liquibase Maven plugin
- No frontend-maven-plugin (for React build integration)
- No Lombok dependency

### Notable Gaps in pom.xml
- **No Lombok**: The backend standards document (`models.md`) references Lombok annotations (`@Getter`, `@Setter`, `@NoArgsConstructor`) but Lombok is not in dependencies
- **No jOOQ codegen plugin**: jOOQ is included as a dependency but the code generation Maven plugin is not configured
- **No frontend build integration**: No `frontend-maven-plugin` or similar for building React frontend
- **Empty metadata**: `<name/>`, `<description/>`, `<url/>`, `<license/>`, `<developer/>`, `<scm/>` are all empty placeholders

---

## 3. Application Properties

**Source**: `src/main/resources/application.properties` (Line 1)
**Confidence**: High (100%) -- direct file content

```properties
spring.application.name=aj
```

### Observations
- Only the application name is configured
- No database connection properties (datasource URL, username, password)
- No Liquibase configuration (changelog path)
- No Spring profiles defined
- No server port configured (will default to 8080)
- No JPA/Hibernate properties (ddl-auto, dialect, etc.)
- No jOOQ configuration

### Implication
The application cannot start in production mode without database configuration. It will only work via TestContainers in the test profile (which auto-configures the datasource via `@ServiceConnection`).

---

## 4. Java Source Files

### 4.1 AjApplication.java (Main Entry Point)

**Source**: `src/main/java/pl/devstyle/aj/AjApplication.java` (Lines 1-13)
**Confidence**: High (100%)

```java
package pl.devstyle.aj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AjApplication {
    public static void main(String[] args) {
        SpringApplication.run(AjApplication.class, args);
    }
}
```

- Standard Spring Boot bootstrap class
- `@SpringBootApplication` enables auto-configuration, component scanning within `pl.devstyle.aj.**`
- No custom configuration, no bean definitions, no profile activation

### 4.2 AjApplicationTests.java (Integration Test)

**Source**: `src/test/java/pl/devstyle/aj/AjApplicationTests.java` (Lines 1-15)
**Confidence**: High (100%)

```java
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AjApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

- Smoke test: verifies Spring context loads successfully
- Imports TestContainers configuration for database provisioning
- Uses `@SpringBootTest` (full application context)

### 4.3 TestAjApplication.java (Development Runner)

**Source**: `src/test/java/pl/devstyle/aj/TestAjApplication.java` (Lines 1-11)
**Confidence**: High (100%)

```java
public class TestAjApplication {
    public static void main(String[] args) {
        SpringApplication.from(AjApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
```

- Allows running the application locally with a TestContainers PostgreSQL database
- Uses `SpringApplication.from(...).with(...)` pattern (Spring Boot 3.1+ feature)
- Convenient for local development without a standalone PostgreSQL installation

### 4.4 TestcontainersConfiguration.java

**Source**: `src/test/java/pl/devstyle/aj/TestcontainersConfiguration.java` (Lines 1-18)
**Confidence**: High (100%)

```java
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
    }
}
```

- Defines a PostgreSQL TestContainer as a Spring bean
- Uses `@ServiceConnection` for automatic datasource configuration (Spring Boot 3.1+ feature)
- Uses `postgres:latest` image -- **not pinned to a specific version** (potential reproducibility concern)
- `proxyBeanMethods = false` for lightweight configuration

---

## 5. Liquibase Changelog

**Source**: `src/main/resources/db/changelog/` (directory listing)
**Confidence**: High (100%)

- Directory exists but is **completely empty**
- No master changelog file (e.g., `db.changelog-master.yaml`)
- No migration files
- Liquibase is in dependencies but has nothing to execute
- This means the application will need a changelog before any database schema can be created

---

## 6. Maven Wrapper

**Source**: `.mvn/wrapper/maven-wrapper.properties` (Lines 1-3)
**Confidence**: High (100%)

```properties
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.14/apache-maven-3.9.14-bin.zip
```

- Maven Wrapper version: 3.3.4
- Maven distribution: 3.9.14
- Distribution type: `only-script` (no bundled JAR, downloads on first use)
- `mvnw` (Unix) and `mvnw.cmd` (Windows) scripts present at project root

---

## 7. Docker / Container Configuration

**Source**: Full directory listing
**Confidence**: High (100%)

- **No Dockerfile** exists
- **No docker-compose.yml** exists
- **No .dockerignore** exists
- TestContainers is the only container technology in use (test scope only)
- No production containerization strategy is in place

---

## 8. .gitignore

**Source**: `.gitignore` (Lines 1-34)
**Confidence**: High (100%)

Covers:
- `HELP.md`, `target/` (Maven build output)
- `.mvn/wrapper/maven-wrapper.jar`
- IDE files: STS (`.apt_generated`, `.classpath`, etc.), IntelliJ IDEA (`.idea`, `*.iws`, `*.iml`, `*.ipr`), NetBeans (`/nbproject/private/`, etc.)
- VS Code (`.vscode/`)
- Preserves `src/main/**/target/` and `src/test/**/target/` (negation rules)

### Not Covered (potential gaps)
- No `.env` file exclusion
- No `node_modules/` exclusion (will be needed when React frontend is added)
- No `dist/` or `build/` exclusion for frontend assets
- No OS files exclusion (`.DS_Store` is present in repo -- visible in directory listing)

---

## 9. Additional Configuration Files

### .gitattributes

**Source**: `.gitattributes` (Lines 1-2)
**Confidence**: High (100%)

```
/mvnw text eol=lf
*.cmd text eol=crlf
```

Ensures consistent line endings for Maven wrapper scripts across operating systems.

---

## Summary of Current State

### What Exists
1. Spring Boot 4.0.5 application with Java 25
2. Maven build with wrapper (3.9.14)
3. Dependencies for JPA, jOOQ, Liquibase, WebMVC
4. PostgreSQL driver (runtime scope)
5. Full TestContainers setup for integration testing
6. Test runner for local development with containerized PostgreSQL
7. Empty Liquibase changelog directory (prepared but unused)
8. Empty static resources and templates directories
9. Comprehensive project documentation in `.maister/docs/`

### What Is Missing (Gaps)
| Gap | Priority | Notes |
|---|---|---|
| Database configuration (`application.properties`) | High | No datasource URL, credentials, or Liquibase config |
| Liquibase master changelog | High | Directory exists but empty |
| Docker Compose for local dev PostgreSQL | Medium | Currently relying solely on TestContainers |
| Dockerfile for production | Medium | No containerization strategy |
| React frontend | High | No frontend code, no `package.json`, no build integration |
| Frontend Maven plugin | Medium | Needed to integrate React build into Maven lifecycle |
| Lombok dependency | Low | Referenced in standards but not in pom.xml |
| jOOQ code generation plugin | Medium | jOOQ dependency present but no codegen configured |
| Spring profiles | Medium | No `application-dev.properties`, `application-prod.properties` |
| Package sub-structure | High | Only root package with `AjApplication.java` |
| `.gitignore` for frontend | Low | No `node_modules/`, `.env` exclusions |
| Pinned PostgreSQL image version | Low | `postgres:latest` in TestContainers -- should pin for reproducibility |
| CI/CD pipeline | Low | Not configured |
| Lombok dependency | Low | Standards reference it, pom.xml lacks it |

### Architecture Decisions Documented but Not Implemented
- **JPA vs jOOQ split**: Both included, decision pending on which handles what
- **Plugin framework**: PF4J, OSGi, JPMS researched but none selected
- **Multi-tenant strategy**: Not determined
- **Planned package structure**: `core/`, `plugin/`, `api/`, `domain/`, `config/` -- none created yet

---

*Gathered: 2026-03-28*
*Sources: Direct codebase file reading and directory analysis*
*Overall Confidence: High (100%) -- all findings based on direct file observation*
