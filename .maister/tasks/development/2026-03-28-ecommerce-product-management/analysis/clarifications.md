# Phase 1 Clarifications

## Design Spec
- **Decision**: Follow product design feature spec exactly as written
- Hybrid feature packages (product/, category/, core/)
- Flat API, Chakra UI + React Router, Deep Teal + Amber theme
- Hard delete, no pagination

## Validation
- **Decision**: Add spring-boot-starter-validation to pom.xml
- Required for @Valid, @NotBlank, @Size, @Positive on request DTOs

## Lombok
- **Decision**: Use Lombok (@Getter, @Setter, @NoArgsConstructor on entities)
- Lombok already in pom.xml and configured in user's IDE
