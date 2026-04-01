# Docker Compose PostgreSQL Setup for Development

## Research Question
How to set up Docker Compose for development PostgreSQL that works alongside the existing TestContainers test infrastructure in a Spring Boot 4.0.5 project?

---

## 1. Docker Compose Configuration for Development PostgreSQL

### Recommended `compose.yml`

**Source**: [Spring Boot Docker Compose Documentation](https://docs.spring.io/spring-boot/how-to/docker-compose.html), [Docker Compose Health Checks Guide](https://docs.docker.com/compose/how-tos/startup-order/)

```yaml
services:
  postgres:
    image: 'postgres:17'
    ports:
      - '5432:5432'
    environment:
      POSTGRES_DB: aj
      POSTGRES_USER: aj
      POSTGRES_PASSWORD: aj
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U aj -d aj"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

volumes:
  postgres-data:
```

**Confidence**: High (95%) -- Standard Docker Compose PostgreSQL pattern used across the industry.

### Key Configuration Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Port mapping | `5432:5432` (fixed) | Predictable for IDE database tools and manual connections |
| Image version | `postgres:17` | Current stable PostgreSQL; pin major version for consistency |
| Volume | Named volume `postgres-data` | Data persists across container restarts; `docker compose down -v` to reset |
| Health check | `pg_isready` | Official PostgreSQL readiness tool; more reliable than SQL queries |
| Credentials | Simple dev values (`aj/aj/aj`) | Skeleton project, no security needed for local dev |

**Source**: [PostgreSQL Docker Best Practices](https://sliplane.io/blog/best-practices-for-postgres-in-docker), [Docker Compose Health Checks](https://last9.io/blog/docker-compose-health-checks/)

### File Location

Place `compose.yml` in the project root (`/Users/kuba/Projects/dna_ai/code/compose.yml`). Spring Boot auto-discovers compose files named `compose.yml`, `compose.yaml`, `docker-compose.yml`, or `docker-compose.yaml` in the working directory.

**Source**: [Spring Boot Dev Services Documentation](https://docs.spring.io/spring-boot/reference/features/dev-services.html) -- "When your application starts, the Docker Compose integration will look for a configuration file in the current working directory."

---

## 2. Spring Boot Docker Compose Integration (`spring-boot-docker-compose`)

### How It Works

Spring Boot 3.1+ (and 4.x) includes the `spring-boot-docker-compose` module that automatically:

1. Discovers `compose.yml` in the working directory at application startup
2. Runs `docker compose up` if services are not already running
3. Creates `ServiceConnection` beans (e.g., `JdbcConnectionDetails`) from detected containers
4. Runs `docker compose stop` when the application shuts down

This means **no `spring.datasource.*` properties are needed** for the dev profile -- Spring Boot auto-configures the datasource from the running container.

**Source**: [Spring Boot Dev Services Documentation](https://docs.spring.io/spring-boot/reference/features/dev-services.html)

### Maven Dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-docker-compose</artifactId>
    <optional>true</optional>
</dependency>
```

**Important**: Use `<optional>true</optional>` (Maven) or `developmentOnly` (Gradle) so it is excluded from the production artifact.

**Confidence**: High (100%) -- Confirmed in Spring Boot 4.0.2 API docs and official documentation.

### Service Connection Auto-Configuration for PostgreSQL

When Spring Boot detects a container with image name matching `postgres`, it automatically creates:
- `JdbcConnectionDetails` bean with the correct JDBC URL, username, and password
- This overrides any `spring.datasource.*` properties

The JDBC URL is constructed as: `jdbc:postgresql://<host>:<mapped-port>/<POSTGRES_DB>`

**Source**: [Spring Boot Docker Compose How-to](https://docs.spring.io/spring-boot/how-to/docker-compose.html) -- Example shows JDBC URL becomes `jdbc:postgresql://127.0.0.1:5432/mydb`

### Key Configuration Properties

| Property | Default | Purpose |
|----------|---------|---------|
| `spring.docker.compose.file` | Auto-discovered | Path to compose file |
| `spring.docker.compose.lifecycle-management` | `start-and-stop` | `none`, `start-only`, `start-and-stop` |
| `spring.docker.compose.skip.in-tests` | `true` | Disables Docker Compose during tests |
| `spring.docker.compose.stop.command` | `stop` | Use `down` to also remove containers |
| `spring.docker.compose.stop.timeout` | - | Timeout for stop command |
| `spring.docker.compose.readiness.tcp.connect-timeout` | - | TCP readiness check timeout |
| `spring.docker.compose.profiles.active` | - | Activate Docker Compose profiles |

**Source**: [Spring Boot Dev Services Documentation](https://docs.spring.io/spring-boot/reference/features/dev-services.html)

### Lifecycle Management Recommendation

For a skeleton/dev project, `start-and-stop` (the default) works well. The database container starts when you run the app and stops when you stop it. Data persists in the named volume, so the next startup is fast.

If you want the database to keep running between app restarts (e.g., to inspect data), use:
```properties
spring.docker.compose.lifecycle-management=start-only
```
Then manually stop with `docker compose stop`.

**Confidence**: High (95%)

---

## 3. Spring Boot Profile Separation: Dev vs Test

### Strategy Overview

The project already uses TestContainers for tests. The key separation is:

| Context | Database Source | Mechanism | Profile |
|---------|---------------|-----------|---------|
| **Tests** (`mvn test`) | TestContainers | `@ServiceConnection` on `PostgreSQLContainer` bean in `TestcontainersConfiguration` | Default test behavior |
| **Dev run** (`mvn spring-boot:run`) | Docker Compose | `spring-boot-docker-compose` module auto-discovers `compose.yml` | Default dev behavior |
| **Dev run via test classpath** (`mvn spring-boot:test-run`) | TestContainers | `TestAjApplication.main()` launches app with `TestcontainersConfiguration` | Existing pattern |

**Source**: [INNOQ Article: Containers for tests and local development](https://www.innoq.com/en/articles/2023/10/spring-boot-testcontainers-and-docker-compose/), [Spring Boot Dev Services](https://docs.spring.io/spring-boot/reference/features/dev-services.html)

### Why This Works Without Conflicts

1. **Dependency scoping prevents overlap**:
   - `spring-boot-docker-compose` is `<optional>true</optional>` in main dependencies -- available at runtime but not in tests
   - `spring-boot-testcontainers` is `<scope>test</scope>` -- only available in test classpath

2. **`skip.in-tests` default**:
   - `spring.docker.compose.skip.in-tests=true` is the default, so Docker Compose integration is automatically disabled during `mvn test`

3. **No explicit profile needed**:
   - No `application-dev.properties` or `application-test.properties` required for database configuration
   - Both mechanisms use `ServiceConnection` / `ConnectionDetails` beans that override `spring.datasource.*`

**Confidence**: High (90%) -- This is the documented approach from Spring Boot. The only caveat is ensuring dependency scopes are correct.

### Existing Project State

The project already has the TestContainers side fully configured:

**File**: `src/test/java/pl/devstyle/aj/TestcontainersConfiguration.java`
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

**File**: `src/test/java/pl/devstyle/aj/TestAjApplication.java`
```java
public class TestAjApplication {
    public static void main(String[] args) {
        SpringApplication.from(AjApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
```

This `TestAjApplication` class allows running the app with TestContainers via `mvn spring-boot:test-run` -- useful when you want a fully ephemeral database without Docker Compose.

### What Needs to Be Added

To complete the Docker Compose dev setup, the project needs:

1. **`compose.yml`** in project root (see Section 1)
2. **`spring-boot-docker-compose` dependency** in `pom.xml` (see Section 2)
3. **No `application.properties` changes needed** for database connection -- auto-configured

### Optional: Explicit Dev Profile Properties

If you want to customize Liquibase or logging behavior for dev, create `src/main/resources/application-dev.properties`:

```properties
# Optional: keep containers running between restarts
spring.docker.compose.lifecycle-management=start-only

# Optional: show SQL for development debugging
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

Then run with: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`

**Confidence**: Medium (75%) -- The dev profile is optional and a matter of preference. The core setup works without it.

---

## 4. Liquibase Auto-Migration on Startup

### How It Works

Spring Boot auto-runs Liquibase migrations on startup when:
1. `spring-boot-starter-liquibase` is on the classpath (already present in `pom.xml`)
2. A database connection is available (provided by Docker Compose or TestContainers)
3. A changelog file exists at `db/changelog/db.changelog-master.yaml` (default path)

The project already has `src/main/resources/db/changelog/` directory (currently empty).

**Source**: Existing project `pom.xml` includes `spring-boot-starter-liquibase`. Spring Boot auto-configuration applies Liquibase when the dependency and datasource are available.

### Configuration

Default Liquibase properties (usually no changes needed for skeleton):

```properties
# Default changelog path (already the convention)
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml

# Optional: drop-first for dev (dangerous, use only if needed)
# spring.liquibase.drop-first=false
```

Both Docker Compose (dev) and TestContainers (test) will trigger Liquibase migrations automatically since they both provide a datasource connection.

**Confidence**: High (95%) -- Standard Spring Boot Liquibase auto-configuration.

---

## 5. Development Workflow

### Option A: Docker Compose (Recommended for regular development)

```bash
# Start the application (auto-starts PostgreSQL via Docker Compose)
mvn spring-boot:run

# Or with dev profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Database is available at localhost:5432 (for IDE/pgAdmin connection)
# Liquibase migrations run automatically on startup
# Stop the app -> container stops (or stays if lifecycle=start-only)

# To fully reset the database:
docker compose down -v
```

### Option B: TestContainers via test-run (Ephemeral, no compose file needed)

```bash
# Uses TestAjApplication.java (already exists)
mvn spring-boot:test-run

# PostgreSQL starts via TestContainers on a random port
# Fully ephemeral -- data gone when app stops
# Port is random, so IDE database tools require checking logs
```

### Running Tests (unchanged)

```bash
# Tests always use TestContainers (existing setup)
mvn test

# Docker Compose is automatically skipped during tests
# (spring.docker.compose.skip.in-tests=true by default)
```

### Connecting from IDE / Database Admin Tools

With Docker Compose (Option A):
- **Host**: `localhost`
- **Port**: `5432`
- **Database**: `aj`
- **Username**: `aj`
- **Password**: `aj`

These are predictable because the compose file uses fixed port mapping (`5432:5432`).

**Confidence**: High (90%)

---

## 6. Summary of Required Changes

### Files to Create

| File | Purpose |
|------|---------|
| `compose.yml` | PostgreSQL service definition for development |

### Files to Modify

| File | Change |
|------|--------|
| `pom.xml` | Add `spring-boot-docker-compose` dependency with `<optional>true</optional>` |

### Files That Need No Changes

| File | Reason |
|------|--------|
| `application.properties` | Docker Compose auto-configures datasource |
| `TestcontainersConfiguration.java` | Already correct, continues to work for tests |
| `TestAjApplication.java` | Already correct, provides alternative dev workflow |

### Minimal `pom.xml` Addition

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-docker-compose</artifactId>
    <optional>true</optional>
</dependency>
```

**Confidence**: High (95%) -- This is the minimal change set. All existing test infrastructure remains untouched.

---

## 7. Considerations and Trade-offs

### Docker Compose vs TestContainers for Dev

| Aspect | Docker Compose | TestContainers (`test-run`) |
|--------|---------------|---------------------------|
| Data persistence | Yes (named volume) | No (ephemeral) |
| Fixed port | Yes (5432) | No (random) |
| IDE DB tools | Easy (predictable host:port) | Harder (check logs for port) |
| Setup overhead | `compose.yml` file needed | Already configured |
| Container lifecycle | Managed by Docker Compose | Managed by JVM |
| Team consistency | Shared `compose.yml` in repo | Code-defined in test sources |

**Recommendation**: Use Docker Compose for regular development (persistent data, predictable port for IDE tools), keep TestContainers for tests (isolated, ephemeral, already working).

### PostgreSQL Version Alignment

Consider pinning the same PostgreSQL major version in both `compose.yml` and `TestcontainersConfiguration.java`:
- `compose.yml`: `image: 'postgres:17'`
- `TestcontainersConfiguration.java`: `DockerImageName.parse("postgres:17")` (currently uses `postgres:latest`)

This prevents subtle behavior differences between dev and test environments.

**Confidence**: Medium (80%) -- Best practice but not strictly required for a skeleton.

### Spring Boot Docker Compose Module Maturity

The `spring-boot-docker-compose` module has been stable since Spring Boot 3.1 (released June 2023) and is confirmed available in Spring Boot 4.0.x. The API for `DockerCompose` class exists in the [Spring Boot 4.0.2 API](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/docker/compose/core/DockerCompose.html).

**Confidence**: High (90%)

---

## Sources

- [Spring Boot Development-time Services Documentation](https://docs.spring.io/spring-boot/reference/features/dev-services.html)
- [Spring Boot Docker Compose How-to](https://docs.spring.io/spring-boot/how-to/docker-compose.html)
- [Docker Compose Support in Spring Boot (Baeldung)](https://www.baeldung.com/docker-compose-support-spring-boot)
- [Spring Boot Docker Compose Support Blog Post](https://spring.io/blog/2023/06/21/docker-compose-support-in-spring-boot-3-1/)
- [INNOQ: Containers for tests and local development with Spring Boot 3.1](https://www.innoq.com/en/articles/2023/10/spring-boot-testcontainers-and-docker-compose/)
- [Docker Compose Health Checks Guide](https://last9.io/blog/docker-compose-health-checks/)
- [Docker Compose Startup Order](https://docs.docker.com/compose/how-tos/startup-order/)
- [PostgreSQL Docker Best Practices](https://sliplane.io/blog/best-practices-for-postgres-in-docker)
- [DockerCompose Spring Boot 4.0.2 API](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/docker/compose/core/DockerCompose.html)
- Existing codebase: `pom.xml`, `TestcontainersConfiguration.java`, `TestAjApplication.java`, `application.properties`
