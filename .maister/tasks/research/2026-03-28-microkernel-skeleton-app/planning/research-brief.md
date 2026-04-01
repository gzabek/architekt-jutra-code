# Research Brief: Microkernel Skeleton Application

## Research Question
How to build a microkernel architecture skeleton app with Spring Boot backend, React frontend, and containerized PostgreSQL — all technology integrated and running, no auth, no business features?

## Research Type
**Mixed** — combines technical investigation (how to structure the project, integrate technologies) with literature research (best practices for microkernel architecture, monorepo structure, frontend-backend integration patterns).

## Context
The project **aj** already has a Spring Boot 4.0.5 scaffold with:
- Java 25
- Spring Data JPA + jOOQ (dual)
- Liquibase for migrations
- PostgreSQL driver
- TestContainers for integration tests
- Maven build

What's missing:
- React frontend (not yet added)
- Docker Compose for development PostgreSQL
- Working end-to-end skeleton (controller → service → repository → database)
- Frontend-backend integration
- Microkernel pattern implementation (core + plugin interfaces)

## Scope

### Included
- Spring Boot backend skeleton with microkernel pattern
- React frontend setup and integration with backend
- PostgreSQL running in Docker container (dev environment)
- Maven multi-module or monorepo structure
- Development workflow (how to run everything together)
- Minimal working end-to-end example (health check or similar)

### Excluded
- Authorization/authentication
- Business features or actual plugins
- Production deployment configuration
- CI/CD pipeline
- Frontend feature implementation

### Constraints
- Must use existing tech stack: Java 25, Spring Boot 4.0.5, Maven
- Must build on existing project structure
- Frontend and features are next steps — skeleton only

## Success Criteria
1. Clear understanding of how to structure the monorepo (backend + frontend)
2. Best practices for React + Spring Boot integration
3. Docker Compose setup for development PostgreSQL
4. Microkernel core interfaces pattern
5. Recommended project structure
6. Working development workflow description
