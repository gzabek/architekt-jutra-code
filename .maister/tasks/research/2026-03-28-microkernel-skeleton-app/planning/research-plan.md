# Research Plan: Microkernel Skeleton Application

## Research Overview

### Research Question
How to build a microkernel architecture skeleton app with Spring Boot backend, React frontend, and containerized PostgreSQL — all technology integrated and running, no auth, no business features?

### Research Type
**Mixed** — Technical (codebase structure, Spring Boot + React integration, Docker setup) combined with Literature (microkernel architecture patterns, monorepo best practices, frontend-backend integration conventions).

### Scope and Boundaries
**In scope**: Spring Boot 4.0.5 backend skeleton, React frontend setup, Docker Compose for PostgreSQL, microkernel core interfaces, Maven build integration, development workflow.

**Out of scope**: Auth, business features/plugins, production deployment, CI/CD, frontend feature implementation.

**Constraints**: Must use Java 25, Spring Boot 4.0.5, Maven. Must build on existing `pl.devstyle.aj` project structure.

### Sub-Questions
1. How should a Spring Boot + React monorepo be structured with Maven?
2. What is the recommended way to integrate a React frontend build into a Spring Boot project (embedded vs separate, proxy vs bundled)?
3. How to set up Docker Compose for development PostgreSQL that works with both app runtime and TestContainers tests?
4. What does a microkernel core look like in Spring Boot — core interfaces, plugin registry, extension points — without implementing actual plugins?
5. What is the minimal end-to-end vertical slice (health endpoint -> DB ping) to prove all layers work?
6. What development workflow allows running backend + frontend + database together during development?

---

## Methodology

### Primary Approach
Multi-strategy research combining:
1. **Codebase analysis** — understand existing project structure, dependencies, and configuration to determine integration points
2. **Literature/best practices research** — gather proven patterns for Spring Boot + React integration, microkernel architecture in Spring, and Docker Compose development setups
3. **Configuration analysis** — examine pom.xml, application.properties, and test infrastructure to understand what is already wired up

### Fallback Strategies
- If Spring Boot 4.0.5 specific documentation is scarce, fall back to Spring Boot 3.x patterns (API is largely compatible)
- If microkernel patterns for Spring are limited, research PF4J integration patterns and Java module system approaches
- If React + Spring Boot monorepo patterns are conflicting, gather multiple approaches for comparison

### Analysis Framework
For each sub-question, gather evidence across three dimensions:
1. **Current state** — what exists in the codebase today
2. **Target pattern** — what the best practice or recommended approach looks like
3. **Integration path** — how to get from current state to target with minimal changes

---

## Data Sources

See `planning/sources.md` for the detailed source manifest.

**Summary by type**:
- Codebase: 5 source files + pom.xml + application.properties + 3 test files
- Project documentation: 2 architecture/tech docs + 6 backend/frontend standards
- Configuration: pom.xml, application.properties, .gitignore, mvnw
- External: Spring Boot docs, React integration guides, Docker Compose references, microkernel pattern resources

---

## Research Phases

### Phase 1: Broad Discovery
- Catalog all existing files and their roles in the project
- Map current dependency graph from pom.xml
- Identify what infrastructure is already configured (TestContainers, Liquibase, JPA, jOOQ)
- Scan project standards for relevant constraints (API design, models, migrations, frontend components)

### Phase 2: Targeted Reading
- Read existing source files to understand bootstrap and test configuration
- Read project architecture and tech stack docs for planned directions
- Research Spring Boot + React integration patterns (frontend-maven-plugin, separate builds, dev proxy)
- Research Docker Compose patterns for PostgreSQL with Spring Boot

### Phase 3: Deep Dive
- Investigate microkernel core interface patterns in Java/Spring (plugin registry, extension points, service provider interfaces)
- Investigate Maven multi-module vs single-module with embedded frontend
- Investigate Spring Boot dev profiles and Docker Compose integration
- Investigate React project scaffolding (Vite vs Create React App vs Next.js for SPA)

### Phase 4: Verification
- Cross-reference findings against existing project constraints (Java 25, Spring Boot 4.0.5)
- Validate that proposed structure aligns with project standards (minimal implementation, conventions)
- Ensure Docker Compose setup does not conflict with TestContainers test infrastructure
- Confirm all proposed dependencies are compatible with Spring Boot 4.0.5 BOM

---

## Gathering Strategy

### Instances: 4

| # | Category ID | Focus Area | Tools | Output Prefix |
|---|------------|------------|-------|---------------|
| 1 | codebase-analysis | Existing project structure, dependencies, configuration, test setup — understand current state completely | Glob, Grep, Read | codebase |
| 2 | spring-react-integration | Spring Boot + React monorepo patterns, Maven frontend integration, dev proxy setup, build pipeline | WebSearch, WebFetch, Read | spring-react |
| 3 | microkernel-patterns | Microkernel architecture in Spring Boot/Java, core interfaces, plugin registry patterns, SPI, extension points | WebSearch, WebFetch, Read | microkernel |
| 4 | docker-postgres-setup | Docker Compose for dev PostgreSQL, Spring Boot Docker Compose support, coexistence with TestContainers, dev profiles | WebSearch, WebFetch, Read | docker-postgres |

### Rationale
Four gatherers aligned to the four distinct knowledge domains of the research question. The codebase-analysis gatherer establishes the baseline (what exists). The remaining three each target a specific integration concern that requires external research: React frontend integration, microkernel architecture patterns, and containerized database setup. These domains have minimal overlap and can be investigated independently. A fifth gatherer for "project structure best practices" was considered but folded into spring-react-integration since monorepo structure is tightly coupled with how React integrates into the Maven build.

---

## Success Criteria

1. **Monorepo structure defined** — Clear recommendation for how backend and frontend coexist in the repository, with Maven build integration path
2. **React integration pattern selected** — Specific approach chosen (embedded vs proxy vs separate) with rationale and trade-offs documented
3. **Docker Compose configuration designed** — PostgreSQL container setup that works for development and does not conflict with TestContainers
4. **Microkernel core interfaces sketched** — Plugin registry, extension point, and core service interfaces identified with Spring-idiomatic patterns
5. **End-to-end vertical slice defined** — Minimal health check or ping endpoint that proves backend -> database connectivity
6. **Development workflow documented** — Step-by-step description of how to start and run all components during development
7. **All findings backed by evidence** — Each recommendation references either existing codebase patterns, official documentation, or established community practices

---

## Expected Outputs

1. **Research findings** — One findings file per gatherer instance (`codebase-*.md`, `spring-react-*.md`, `microkernel-*.md`, `docker-postgres-*.md`)
2. **Synthesized research report** — Consolidated findings addressing all sub-questions with recommendations
3. **Structural recommendation** — Proposed directory layout and module structure
4. **Technology decisions** — Specific tool/library choices with rationale (Vite vs CRA, frontend-maven-plugin vs exec-maven-plugin, etc.)
