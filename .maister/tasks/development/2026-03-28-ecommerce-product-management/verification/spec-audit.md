# Specification Audit: Ecommerce Product Management

**Date**: 2026-03-28
**Auditor**: Specification Auditor (independent)
**Specification**: `.maister/tasks/development/2026-03-28-ecommerce-product-management/implementation/spec.md`
**Compliance Status**: N/A (pre-implementation audit -- no code to verify against)

---

## Audit Type

This is a **pre-implementation specification audit**. The codebase is a pre-alpha scaffold with zero domain code. The audit focuses on specification completeness, clarity, internal consistency, alignment with project standards, and alignment with upstream design artifacts (feature spec, product brief, requirements).

---

## Summary

The specification is comprehensive, well-structured, and largely ready for implementation. It covers all six dimensions of a full-stack feature (data models, API, backend architecture, frontend, database migrations, testing). The spec aligns well with upstream design artifacts and project coding standards.

**Key findings**: 5 items requiring attention before implementation, 4 clarification questions, and 3 minor observations. No critical issues found.

---

## Section 1: Specification Completeness

### 1.1 Coverage of Upstream Requirements

The spec was compared against three upstream sources: the product brief (`outputs/product-brief.md`), the feature spec (`analysis/feature-spec.md`), and the requirements document (`analysis/requirements.md`).

**Finding 1.1.1**: All acceptance criteria from the product brief are covered in the spec.

| Product Brief Acceptance Criteria | Spec Coverage |
|-----------------------------------|---------------|
| Category CRUD with unique name | Core Requirement 1 |
| Product CRUD with all fields | Core Requirement 2 |
| Product list search/filter/sort | Core Requirement 3 |
| Category delete protection (409) | Core Requirement 4 |
| Validation with field errors | Core Requirement 5 |
| Consistent error responses | Core Requirement 6 |
| REST endpoints | Core Requirement 7 |
| Liquibase migrations | Core Requirement 8 |
| React + Chakra UI frontend | Core Requirement 9 |
| Custom theme | Core Requirement 10 |
| Mobile responsive | Core Requirement 11 |
| Integration tests | Core Requirement 12 |

**Verdict**: Complete. No upstream requirements are missing from the spec.

### 1.2 Coverage of Feature Spec Sections

| Feature Spec Section | Spec Coverage | Notes |
|----------------------|---------------|-------|
| Section 1: Data Models | Fully covered in "Technical Approach > Backend > Entity design" | All fields, types, constraints match |
| Section 2: REST API | Fully covered in Core Requirements 1-7 and Technical Approach | All endpoints, status codes, DTOs match |
| Section 3: Backend Architecture | Fully covered in "Technical Approach > Backend" | Package structure, service pattern, controller rules match |
| Section 4: Frontend Architecture | Fully covered in "Technical Approach > Frontend" | All components, hooks, routing match |
| Section 5: Database Migrations | Fully covered in "Technical Approach > Database" | Both changesets specified |
| Section 6: Testing Strategy | Fully covered in "Technical Approach > Testing" | Test counts and cases match |

**Verdict**: Complete alignment with feature spec.

---

## Section 2: Standards Compliance Check

### 2.1 JPA Entity Modeling (`standards/backend/models.md`)

| Standard | Spec Alignment | Status |
|----------|----------------|--------|
| BaseEntity with @MappedSuperclass | Spec line 94: explicitly specified | Aligned |
| SEQUENCE generation, allocationSize=1 | Spec line 94: specified | Aligned |
| Entity-specific @SequenceGenerator at class level | Spec line 96: specified | Aligned |
| LAZY fetch for @ManyToOne | Spec line 98: "fetch=LAZY" | Aligned |
| Business key equals/hashCode | Spec line 97: Category=name, Product=sku | Aligned |
| Lombok: @Getter, @Setter, @NoArgsConstructor | Spec line 97: specified | Aligned |
| No @Data, no @EqualsAndHashCode | Spec line 97: "(no @Data, no @EqualsAndHashCode)" | Aligned |
| @Version for optimistic locking | Spec line 94: "updatedAt (@Version LocalDateTime)" | Aligned |
| No @LastModifiedDate on @Version field | Standard says Hibernate manages @Version | See Finding 2.1.1 |
| Singular entity names, plural table names | Not explicitly stated but implied by feature spec tables | See Finding 2.1.2 |

**Finding 2.1.1 -- Severity: Low**
**@Version field management**: The models.md standard (line 109) explicitly states "Don't use @LastModifiedDate on the @Version field -- Hibernate manages it." The spec correctly uses @Version on updatedAt without @LastModifiedDate. However, the spec also says @EnableJpaAuditing is needed for @CreatedDate. This is correct -- JPA Auditing is needed for @CreatedDate but @Version is managed by Hibernate independently. The spec is consistent here.
**Status**: No issue.

**Finding 2.1.2 -- Severity: Low**
**Table naming convention**: The spec does not explicitly state `@Table(name = "categories")` and `@Table(name = "products")` on entities, but the migration changesets name the tables "categories" and "products" (plural), and the entity classes are "Category" and "Product" (singular). This aligns with the standard. However, the spec should be more explicit about `@Table` annotations to prevent ambiguity during implementation.
**Recommendation**: The spec's migration section is clear enough to guide the implementer. No change required but implementer should note that `@Table(name = "...")` annotations are expected per models.md.

### 2.2 Lombok in models.md vs Spec

**Finding 2.2.1 -- Severity: Medium**
**Lombok annotations mismatch with standard**: The models.md standard (line 296) shows `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` on entities. The spec (line 97) specifies only `@Getter, @Setter, @NoArgsConstructor`. The spec explicitly says "no @Data, no @EqualsAndHashCode" which aligns with the standard, but omits `@AllArgsConstructor` and `@Builder`.

**Assessment**: The spec is correct to omit `@AllArgsConstructor` and `@Builder` -- the models.md shows these as a project-specific example pattern, not a mandatory requirement. The minimal implementation standard (`standards/global/minimal-implementation.md`) would favor including only what is needed. For entities with manual equals/hashCode (business key), `@Builder` adds no significant value in the CRUD context. The spec's choice is defensible.

**Recommendation**: No change needed. The spec correctly applies minimal implementation principle.

### 2.3 API Design (`standards/backend/api.md`)

| Standard | Spec Alignment | Status |
|----------|----------------|--------|
| RESTful principles | Full CRUD with proper HTTP methods | Aligned |
| Plural nouns for resources | /api/categories, /api/products | Aligned |
| Query parameters for filtering/sorting | ?category, ?search, ?sort | Aligned |
| Proper status codes | 200, 201, 204, 400, 404, 409 all specified | Aligned |
| Versioning | Not addressed | See Finding 2.3.1 |

**Finding 2.3.1 -- Severity: Low**
**API versioning not addressed**: The api.md standard says "Implement versioning (URL path or headers) to manage breaking changes." The spec uses unversioned paths (`/api/categories`, `/api/products`). Given this is pre-alpha with no consumers, versioning is premature. The product brief confirms this is a tech showcase with single admin user.
**Recommendation**: Acceptable omission for MVP. Should be addressed before any external consumers exist.

### 2.4 Database Migrations (`standards/backend/migrations.md`)

| Standard | Spec Alignment | Status |
|----------|----------------|--------|
| Reversible migrations | Spec line 129: "Both include rollback instructions" | Aligned |
| Small and focused changes | Two changesets, one per table | Aligned |
| Separate schema and data | Schema only, no data migrations | Aligned |
| Descriptive names | "001-create-categories-table", "002-create-products-table" | Aligned |

**Verdict**: Full alignment with migration standards.

---

## Section 3: Internal Consistency Check

### 3.1 Cross-Section Consistency

**Finding 3.1.1 -- Severity: Medium**
**Product description max length inconsistency**: The spec's Core Requirement 2 (line 17) says "optional description" without specifying max length. The Technical Approach (entity design section) does not repeat field lengths. However, the feature spec Section 1 (line 43) specifies "max 2000 chars" for Product description. The spec's DTO section in the feature spec (line 108) specifies "@Size(max=2000)" on CreateProductRequest.description.

The implementation spec itself (spec.md) does not explicitly repeat all field lengths in its Core Requirements or Technical Approach sections -- it relies on the feature spec for these details.

**Assessment**: This is not a true inconsistency but a delegation pattern. The spec says to follow the feature spec for field-level details. However, an implementer reading only the spec.md might miss the Product description max length (2000) vs Category description max length (500).

**Recommendation**: The spec's DTO section in the feature spec is the authoritative source. No change required to spec.md as long as the feature spec is treated as a companion document.

**Finding 3.1.2 -- Severity: Low**
**EntityNotFoundException naming**: The spec (line 104) says "Throws EntityNotFoundException (custom, not JPA's) for 404 cases." The error handling section (line 121) says "Handles: EntityNotFoundException -> 404." This is internally consistent. However, the spec does not specify which package this custom exception belongs to. Given the package structure (core/error/), it would logically live in `pl.devstyle.aj.core.error`.

**Recommendation**: Minor gap. The implementer can infer the location from the package structure diagram. No change needed.

**Finding 3.1.3 -- Severity: Low**
**Product count in category list**: The spec's "Out of Scope" section (line 205) says "Product count in category list response (UI can show it if backend provides it, but not a hard requirement)." Yet the Visual Design section (line 42) says "Category list table: ... product count column." The feature spec (Section 4, line 249) also lists "product count" in the CategoryListPage description.

**Assessment**: The spec is saying the backend need not provide a dedicated product count field, but the UI mockup shows it. This creates an implementation question: will the frontend need to count products per category from the product list, or will the backend provide it?

**Recommendation**: See Clarification Question 1 below.

### 3.2 Spec vs Out-of-Scope Boundary

The out-of-scope section is clear and well-defined. Cross-checked against all requirements:

| Out of Scope Item | Consistent with Requirements? |
|-------------------|-------------------------------|
| No cart/checkout | Yes -- product brief confirms admin tool only |
| No authentication | Yes -- product brief confirms single admin |
| No pagination | Yes -- MVP simple lists |
| No file upload | Yes -- photo is URL reference |
| No dark mode | Yes -- light mode only |
| No soft delete | Yes -- hard delete only |
| No jOOQ | Yes -- JPA only for this feature |
| No @WebMvcTest | Yes -- optional, lower priority |
| Product count optional | Partially inconsistent -- see Finding 3.1.3 |

---

## Section 4: Ambiguities and Clarification Needed

### Clarification Question 1: Product Count in Category List

**Context**: The spec's UI mockup description (line 42) shows "product count column" in the category list table. The feature spec's CategoryListPage description (Section 4) also includes product count. However, the Out of Scope section (line 205) says "Product count in category list response (UI can show it if backend provides it, but not a hard requirement)."

**Question**: Should the implementation include a product count in the CategoryResponse or as a separate endpoint? Options:
- (a) Add a `productCount` field to CategoryResponse (requires a COUNT query or @Formula)
- (b) Frontend fetches all products and counts client-side (wasteful for large catalogs)
- (c) Skip the product count column entirely in the UI
- (d) Add a separate endpoint like GET /api/categories/{id}/product-count

**Impact**: This affects both backend (DTO design, query complexity) and frontend (table columns). Since the spec explicitly says "not a hard requirement," option (c) is the safest for MVP.

### Clarification Question 2: Sort Parameter Format

**Context**: The spec (Core Requirement 3, line 18) says "sort by name/price/sku/createdAt." The feature spec (Section 2, line 85) specifies the format as `sort={field},{direction}` (e.g., `sort=name,asc`).

**Question**: What is the default sort when no sort parameter is provided? Options:
- (a) Sort by createdAt descending (newest first) -- common default
- (b) Sort by name ascending -- alphabetical
- (c) No guaranteed order (database default)

**Impact**: Minor UX impact. The spec does not specify a default sort order.

### Clarification Question 3: Validation Error Response Format

**Context**: The spec (line 121) says MethodArgumentNotValidException -> 400 "with field errors." The ErrorResponse record (line 119-120) has fields: status, error, message, timestamp. The feature spec (Section 2, line 144) says "400 with field errors."

**Question**: How should field-level validation errors be represented? The ErrorResponse record has a single `message` field. For validation errors with multiple field failures, options include:
- (a) Concatenate all field errors into the `message` string
- (b) Add a `fieldErrors: Map<String, String>` or `fieldErrors: List<FieldError>` to ErrorResponse
- (c) Return a different response shape for validation errors

**Impact**: Frontend needs to know the error response shape to display per-field validation messages. Option (b) is the most common Spring Boot pattern and would require extending ErrorResponse or creating a ValidationErrorResponse variant.

### Clarification Question 4: Category-Product Relationship Direction

**Context**: The spec (line 98) says "no bidirectional mapping" for Product->Category. The CategoryService needs to check if products reference a category before deletion (409 logic, line 105). Without a bidirectional `products` collection on Category, the service must query ProductRepository to check for associated products.

**Question**: This is not an ambiguity per se -- the unidirectional design is correct and the spec is consistent. However, it implies CategoryService will depend on ProductRepository (or ProductService). Is this cross-domain dependency acceptable?

**Impact**: Package coupling. CategoryService in the `category/` package would need to import from `product/` package. This is a minor architectural concern for a two-entity MVP but worth noting for pattern precedent.

---

## Section 5: Specification Gaps

### Finding 5.1 -- Severity: Medium
**Missing: JPA Auditing Configuration class location**

**Spec Reference**: Spec line 69 says "@EnableJpaAuditing not configured anywhere; required for @CreatedDate to work." Spec line 88 says package structure under `core/` but does not list a JpaConfig.java or similar.

**Evidence**: The package structure diagram (spec lines 88-91) shows:
```
core/
  BaseEntity.java
  error/
    GlobalExceptionHandler.java
    ErrorResponse.java
```

No @Configuration class for JPA auditing is listed.

**Gap**: The spec mentions the need for JPA auditing configuration but does not specify where the @Configuration class lives or what it should be named.

**Recommendation**: Add to the package structure: `core/JpaAuditingConfig.java` (or similar) containing `@Configuration @EnableJpaAuditing`. This is a one-line class but should be documented for completeness.

### Finding 5.2 -- Severity: Medium
**Missing: Validation error response shape for field errors**

**Spec Reference**: Spec line 121 says "MethodArgumentNotValidException -> 400" and the ErrorResponse record has status/error/message/timestamp. The feature spec Section 2 line 144 says "400 with field errors."

**Evidence**: The ErrorResponse record (feature spec lines 136-139) has no field for per-field validation errors. Spring Boot's MethodArgumentNotValidException contains a BindingResult with field-level errors. A single `message` field cannot adequately represent multiple field failures for frontend consumption.

**Gap**: The spec does not define how multiple field-level validation errors are communicated to the frontend. This is important because the frontend form pages need to display per-field error messages.

**Recommendation**: Either extend ErrorResponse with an optional `fieldErrors` map, or document that the `message` field will contain a concatenated summary. The former is the more common Spring Boot pattern. See Clarification Question 3.

### Finding 5.3 -- Severity: Low
**Missing: Frontend error handling and loading states**

**Spec Reference**: Spec line 141 says custom hooks return `{ data, loading, error, refetch, create, update, remove }`. The pages section (lines 147-151) describes table and form layouts but does not describe error states or loading indicators.

**Evidence**: No mention of toast notifications, inline error banners, loading spinners, or retry mechanisms in the spec or feature spec.

**Gap**: The spec describes the happy path UI but not error/loading UX. What happens when an API call fails? What loading indicator is shown?

**Recommendation**: Minor gap for MVP. The implementer can use standard Chakra UI Spinner and Toast components. However, documenting the expected UX for API errors (toast notification? inline banner?) would improve implementation consistency.

### Finding 5.4 -- Severity: Low
**Missing: Frontend form validation UX**

**Spec Reference**: Core Requirement 5 (line 21) says "return 400 with field errors for invalid input." The frontend pages section describes form layouts but not how validation errors are displayed.

**Evidence**: No mention of whether validation is client-side (before submit), server-side only (after submit), or both. No description of error message placement (below fields? toast? alert banner?).

**Gap**: The backend validation is well-specified. The frontend validation UX is not.

**Recommendation**: Since Chakra UI forms support `isInvalid` and `errorMessage` props, the implementer can follow standard patterns. However, the spec should clarify: (a) Is client-side validation required (HTML5 required/maxLength attributes)? (b) How are server-side 400 errors displayed (per-field or summary)?

### Finding 5.5 -- Severity: Low
**Missing: Photo URL preview behavior**

**Spec Reference**: Spec line 41 says "full-width photo URL with preview placeholder." Feature spec Section 4 line 248 says "photo URL with preview placeholder."

**Evidence**: No specification of what the preview placeholder looks like, when the preview loads (on blur? on change? debounced?), or what happens if the URL is invalid/returns 404.

**Gap**: Minor UX detail not specified.

**Recommendation**: Implementer can use a simple `<img>` tag with onError fallback to a placeholder icon. No spec change needed for MVP.

---

## Section 6: Dependency and Feasibility Check

### 6.1 Backend Dependencies

| Dependency | Currently in pom.xml? | Spec Requires? | Status |
|------------|----------------------|----------------|--------|
| spring-boot-starter-data-jpa | Yes (line 36) | Yes | OK |
| spring-boot-starter-webmvc | Yes (line 48) | Yes | OK |
| spring-boot-starter-liquibase | Yes (line 44) | Yes | OK |
| spring-boot-starter-validation | **No** | **Yes** (spec line 167) | Must add |
| org.projectlombok:lombok | **No** | **Yes** (spec line 168) | Must add |
| postgresql | Yes (line 58) | Yes | OK |
| spring-boot-testcontainers | Yes (line 84) | Yes | OK |

**Finding 6.1.1 -- Severity: High**
**Lombok dependency requires annotation processor configuration**: The spec (line 168) says to add `org.projectlombok:lombok` to pom.xml. However, Lombok requires annotation processor configuration in the Maven compiler plugin (or `<scope>provided</scope>` with `<annotationProcessorPaths>`). The spec does not mention this configuration.

**Evidence**: Current pom.xml (lines 97-136) has no `maven-compiler-plugin` configuration. Spring Boot's parent POM manages the compiler plugin but Lombok annotation processing must be explicitly configured.

**Recommendation**: The spec should mention that Lombok needs `<scope>provided</scope>` and annotation processor configuration. In Spring Boot 4.x with Java 25, Lombok compatibility should be verified. Note: Spring Boot's parent POM may handle this automatically if Lombok is added as a dependency with the correct scope. The implementer should verify.

### 6.2 Frontend Dependencies

| Dependency | Currently in package.json? | Spec Requires? | Status |
|------------|---------------------------|----------------|--------|
| react | Yes (19.2.4) | Yes | OK |
| react-dom | Yes (19.2.4) | Yes | OK |
| @chakra-ui/react | **No** | **Yes** (spec line 134) | Must add |
| react-router-dom | **No** | **Yes** (spec line 134) | Must add |

**Finding 6.2.1 -- Severity: Medium**
**Chakra UI v3 dependency chain unclear**: The spec says to add `@chakra-ui/react` (v3). Chakra UI v3 has a different dependency model than v2. The requirements doc (line 19) confirms "Chakra UI v3 (latest, simpler deps)." However, Chakra UI v3 may require additional peer dependencies (e.g., `@emotion/react`, `@ark-ui/react`, or other packages depending on the exact v3 release).

**Evidence**: The spec only lists `@chakra-ui/react` as the dependency to add. No sub-dependencies or peer dependencies are mentioned.

**Recommendation**: The implementer should check the Chakra UI v3 installation guide at implementation time. The spec is correct to not pin exact sub-dependencies (they change between minor releases), but should note that additional peer dependencies may be needed.

### 6.3 Existing Code Compatibility

| Existing Component | Spec Interaction | Risk |
|-------------------|------------------|------|
| HealthController | No changes needed | None |
| SpaForwardController | Already forwards SPA routes -- React Router routes will work | None |
| TestcontainersConfiguration | Reused by new integration tests | None |
| IntegrationTests | No changes needed | None |
| AjApplication | No changes needed | None |
| db.changelog-master.yaml | Add changesets (currently empty) | None |
| main.tsx | Restructure with providers | Low -- complete rewrite of 10-line file |
| App.tsx | Replace or remove | Low -- placeholder code |

**Verdict**: No compatibility risks. All existing code either stays untouched or is trivially replaced.

---

## Section 7: Specification Quality Assessment

### Strengths

1. **Comprehensive scope**: All six dimensions of a full-stack feature are covered (data, API, backend, frontend, database, testing).
2. **Clear out-of-scope boundaries**: Explicitly lists what is excluded, preventing scope creep.
3. **Standards references**: Links to all relevant coding standards with specific relevance notes.
4. **Reusable components table**: Clear separation of what exists vs what must be created.
5. **Visual design references**: Links to HTML mockups for all four pages.
6. **Test case enumeration**: Specific test cases listed (8 category + 9 product) with expected behaviors.
7. **Success criteria**: 10 measurable success criteria covering end-to-end functionality.
8. **Consistent architecture**: Backend and frontend patterns are consistent and well-matched.

### Weaknesses

1. **Validation error response shape undefined**: See Finding 5.2.
2. **Frontend error/loading UX not specified**: See Findings 5.3 and 5.4.
3. **Lombok configuration details missing**: See Finding 6.1.1.
4. **JPA config class not listed in package structure**: See Finding 5.1.
5. **Product count ambiguity**: See Finding 3.1.3 / Clarification Question 1.

---

## Section 8: Findings Summary

### By Severity

| Severity | Count | Findings |
|----------|-------|----------|
| Critical | 0 | -- |
| High | 1 | 6.1.1 (Lombok annotation processor config) |
| Medium | 3 | 5.1 (JPA config class location), 5.2 (Validation error shape), 6.2.1 (Chakra UI v3 deps) |
| Low | 5 | 2.1.2 (Table naming), 2.3.1 (API versioning), 3.1.2 (Exception package), 5.3 (Error/loading UX), 5.4 (Form validation UX), 5.5 (Photo preview) |

### By Category

| Category | Findings |
|----------|----------|
| Missing detail | 5.1, 5.2, 5.3, 5.4, 5.5 |
| Ambiguous | 3.1.3 (product count) |
| Standards alignment | All aligned (no deviations) |
| Dependency/feasibility | 6.1.1, 6.2.1 |
| Internal consistency | No true inconsistencies |

### Clarification Questions

1. Should product count appear in category list UI? If yes, how is it provided by the backend?
2. What is the default sort order for product list when no sort parameter is specified?
3. What shape should validation error responses take for per-field errors?
4. Is the cross-package dependency (CategoryService -> ProductRepository) acceptable for the delete-protection check?

---

## Section 9: Recommendations

### Before Implementation

1. **Resolve Clarification Question 3** (validation error shape) -- This affects both backend ErrorResponse design and frontend form error display. Recommend adding an optional `fieldErrors: Map<String, List<String>>` field to ErrorResponse, or creating a separate ValidationErrorResponse record.

2. **Add JPA auditing config to package structure** (Finding 5.1) -- One line addition to spec: `core/JpaAuditingConfig.java` in the package diagram.

3. **Note Lombok annotation processor requirement** (Finding 6.1.1) -- Add a note in the "Dependencies to Add" section about annotation processor configuration.

### During Implementation

4. **Verify Chakra UI v3 peer dependencies** (Finding 6.2.1) -- Check installation requirements at npm install time.

5. **Use option (c) for product count** -- Skip the product count column in the initial implementation per the Out of Scope note. Can be added later without API changes.

6. **Default sort by createdAt DESC** (Clarification Question 2) -- This is the most common default for admin lists.

### Implementation Readiness

The specification is **ready for implementation** with the above minor clarifications. No blocking issues exist. The high-severity finding (Lombok config) is a configuration detail the implementer can resolve during the dependency setup step.

---

## Verdict

The specification is well-crafted, comprehensive, and internally consistent. It aligns with all relevant project standards and upstream design artifacts. The gaps identified are minor (mostly missing UX details and configuration specifics) and do not block implementation.

**Recommendation**: Proceed to implementation planning. Resolve the validation error shape question (Clarification Question 3) first, as it has the broadest architectural impact. All other items can be resolved during implementation.
