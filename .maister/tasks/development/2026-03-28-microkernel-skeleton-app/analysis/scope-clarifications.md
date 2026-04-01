# Scope Clarifications

## Decisions from Gap Analysis

### PostgreSQL Version
**Decision**: Pin to PostgreSQL 18 (both Docker Compose and TestContainers)
**Source**: User preference (overrides research recommendation of postgres:17)

### Liquibase Changelog Format
**Decision**: YAML format
**Source**: Research default, confirmed by user
