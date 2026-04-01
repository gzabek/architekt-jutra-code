# Phase 2 Scope Clarifications

## Decisions Made
1. **Chakra UI v3** — Use latest version with simpler dependency structure
2. **@Transactional tests** — Use @Transactional on integration tests for fast auto-rollback
3. **Lombok** — Verify present in pom.xml, add if missing

## Task Characteristics (from gap-analyzer)
- has_reproducible_defect: false
- modifies_existing_code: true (minor — dependency files and entry points)
- creates_new_entities: true (primary)
- involves_data_operations: true (full CRUD for 2 entities)
- ui_heavy: true (4 pages, layout shell, routing, custom theme)

## Scope
- No scope expansion needed — feature spec is comprehensive
- ~40 new files across backend, frontend, and tests
- First domain feature — sets patterns for future features
- Risk: low-medium (volume-driven, not complexity-driven)

## Optional Phases Enabled
- E2E testing: enabled (ui_heavy)
- User documentation: enabled (creates_new_entities + ui_heavy)
