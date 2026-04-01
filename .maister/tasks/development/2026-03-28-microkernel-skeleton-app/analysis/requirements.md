# Requirements

## Initial Description
Build a sample application showcasing microkernel architecture with Spring Boot backend, React frontend, and containerized PostgreSQL. No authorization. Frontend and features implemented in next step — just a proper skeleton with all technology running.

## Q&A

### From Clarifications (Phase 1)
- **Landing page**: React app calls /api/health and displays the result

### From Scope Clarifications (Phase 2)
- **PostgreSQL version**: 18 (both Docker Compose and TestContainers)
- **Liquibase format**: YAML

### From Requirements Gathering (Phase 5)
- **Code fidelity**: Follow research code samples closely — they've been validated
- **Health UI**: Simple JSON display — just show raw API response, proves connectivity

## Research Context
Research task: `.maister/tasks/research/2026-03-28-microkernel-skeleton-app/`
- Research report: `outputs/research-report.md`
- High-level design: `outputs/high-level-design.md`
- Decision log: `outputs/decision-log.md` (5 ADRs)

## Functional Requirements

### Backend
1. **Docker Compose PostgreSQL**: compose.yml at project root with postgres:18, auto-provisioned via spring-boot-docker-compose
2. **Microkernel core interfaces**: Plugin, PluginDescriptor, ExtensionPoint marker, PluginRegistry component in pl.devstyle.aj.core.plugin
3. **Health endpoint**: GET /api/health returns {"status": "UP"}
4. **SPA forwarding**: Non-API, non-file paths forward to index.html for React Router
5. **Liquibase master changelog**: Empty db.changelog-master.yaml to prevent startup errors

### Frontend
6. **React + Vite + TypeScript**: Scaffolded in src/main/frontend/ via `npm create vite@latest`
7. **Vite config**: outDir to ../resources/static, /api proxy to localhost:8080
8. **Landing page**: Calls /api/health, displays raw JSON response
9. **Maven integration**: frontend-maven-plugin for reproducible builds (Node 22.14.0)

### Modifications to Existing Files
10. **pom.xml**: Add spring-boot-docker-compose (optional), frontend-maven-plugin
11. **.gitignore**: Add frontend artifacts (node_modules/, node/, resources/static/)
12. **TestcontainersConfiguration.java**: Pin to postgres:18

## Scope Boundaries
- **In scope**: All items above (skeleton only)
- **Out of scope**: Auth, business features, production Dockerfile, CI/CD, Lombok, jOOQ codegen, Spring profiles, frontend state management/routing library

## Reusability Opportunities
- Research code samples can be used as-is for most components
- TestContainers @ServiceConnection pattern already established

## Visual Assets
None — text-only interface for the skeleton
