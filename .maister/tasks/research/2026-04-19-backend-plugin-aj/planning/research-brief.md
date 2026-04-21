# Research Brief

## Research Question

How do you write a backend plugin in the `aj` application?

## Research Type

**Technical** — explores codebase implementation, plugin framework architecture, and patterns required to create a new backend plugin in the aj Spring Boot microkernel platform.

## Scope

### Included
- Plugin core framework (`core/plugin/` package): descriptors, plugin objects, data layers
- How plugins are registered and loaded
- Any existing plugins as concrete examples
- Spring Boot integration patterns for plugins
- Database/persistence conventions for plugins
- REST API conventions exposed by plugins
- jOOQ integration for plugins (if any)

### Excluded
- Frontend plugin SDK (`plugin-sdk/`, `plugins/` under frontend)
- Performance optimization of plugins
- Plugin deployment/packaging

## Constraints
- Java 25 + Spring Boot 4.0.5 codebase
- Microkernel pattern
- Must follow project code style from AGENTS.md

## Success Criteria
- Understand the complete plugin registration lifecycle
- Know which classes/interfaces a new plugin must implement or extend
- Know the naming and packaging conventions
- Be able to write a step-by-step guide for creating a new backend plugin
- Have at least one concrete existing plugin as a reference example

## Research Methodology

**Iterative Deepening (Technical)**:
1. Broad discovery — find all plugin-related files
2. Structural analysis — understand plugin core package organization
3. Implementation reading — read key classes in detail
4. Integration mapping — understand how plugins integrate with Spring Boot and domain packages

## Gathering Strategy

4 parallel gatherers:
1. **codebase-plugin-core** — Deep dive into `core/plugin/` package and related classes
2. **codebase-existing-plugins** — Find and read any existing plugin examples
3. **codebase-domain-integration** — How domain packages interact with the plugin framework
4. **docs-configuration** — Configuration files, AGENTS.md, any plugin documentation
