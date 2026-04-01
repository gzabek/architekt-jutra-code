# Code Review Report (Round 2)

**Date**: 2026-03-28
**Path**: src/main/java/pl/devstyle/aj/{core,category,product}/, src/main/frontend/src/, src/main/resources/db/changelog/, src/test/java/pl/devstyle/aj/{category,product}/
**Scope**: all (quality, security, performance, best practices)
**Status**: Clean (with minor informational items remaining)

## Summary
- **Critical**: 0 issues (was 1 -- resolved)
- **Warnings**: 1 issue (was 8 -- 7 resolved, 1 reduced)
- **Info**: 5 issues (was 6 -- 3 resolved, 2 retained, 2 new minor items from fixes)
- **Files Analyzed**: 39

---

## Fix Verification

### C1. photoUrl XSS -- RESOLVED

The critical XSS issue has been addressed at both layers:
- **Server-side**: `CreateProductRequest.java:14` and `UpdateProductRequest.java:14` now have `@Pattern(regexp = "^https?://.*", message = "Photo URL must start with http:// or https://")` validation on the `photoUrl` field. This prevents storing non-HTTP URLs.
- **Frontend rendering**: `ProductListPage.tsx:260` and `ProductFormPage.tsx:214` now guard image rendering with `isValidImageUrl()` from `utils/url.ts`, which checks for `http://` or `https://` prefix before rendering into `<Image>` or `<img>` tags. Invalid URLs show a `PhotoPlaceholder` SVG instead.
- **Verdict**: Properly fixed. Server-side is the authoritative gate; frontend provides defense-in-depth.

### W1. Cross-module coupling (Product -> Category entity) -- RETAINED AS INFO

The `Product` entity still has a direct `@ManyToOne` relationship to `Category` (Product.java:42-44). This was explicitly noted as an architecture decision acceptable for pre-alpha. The fix round correctly prioritized other items over this refactor. No change needed at this stage.

### W2. CategoryService depends on ProductRepository -- RESOLVED

`CategoryService` no longer imports or injects `ProductRepository`. The delete method (CategoryService.java:52-63) now uses a database-first approach: it attempts `categoryRepository.delete(category)` + `flush()`, catches `DataIntegrityViolationException`, and wraps it in a typed `CategoryHasProductsException`. This is the correct pattern -- it relies on the FK constraint instead of pre-querying across module boundaries.

### W3. No pagination on list endpoints -- RETAINED AS INFO

Pagination was not added. Acceptable for pre-alpha scope. No regression.

### W4. Search input debounce -- RESOLVED

`ProductListPage.tsx:37-55` now implements a 300ms debounce using `useRef<ReturnType<typeof setTimeout>>` with proper cleanup in the useEffect return. The `searchInput` state drives the input field, while the debounced `search` state drives the API call via `useProducts`. This is a clean implementation.

### W5. Duplicate SVG icons between Sidebar and MobileDrawer -- RESOLVED

Icons extracted to `components/shared/Icons.tsx` which exports `ProductsIcon`, `CategoriesIcon`, and `PhotoPlaceholder`. Both `Sidebar.tsx:3` and `MobileDrawer.tsx:14` now import from the shared module. No duplicate icon definitions remain.

### W6. Duplicate formatDate utility -- RESOLVED

Extracted to `utils/format.ts`. Both `CategoryListPage.tsx:14` and `ProductListPage.tsx:18` now import `formatDate` from the shared module. No duplicate definitions remain.

### W7. Hardcoded color values -- PARTIALLY RESOLVED (downgraded to info)

The theme in `theme/index.ts` defines `brand` and `accent` color tokens. Components now use semantic tokens (e.g., `brand.50`, `brand.600`, `brand.700`, `brand.900`, `accent.500`) in several places -- table headers, sidebar backgrounds, badge references. However, slate/gray hex values (`#0F172A`, `#64748B`, `#E2E8F0`, `#94A3B8`, `#334155`, `#1E293B`, `#F1F5F9`, `#F8FAFC`) are still used as raw literals throughout page components and the Header. These Tailwind Slate palette values should ideally be registered as theme tokens (`slate.50` through `slate.900`) for maintainability, but the inconsistency is cosmetic and low-risk.

### W8. Missing @Transactional(readOnly = true) -- RESOLVED

- `CategoryService.findAll()` (line 20): `@Transactional(readOnly = true)` -- present
- `CategoryService.findById()` (line 28): `@Transactional(readOnly = true)` -- present
- `ProductService.findAll()` (line 25): `@Transactional(readOnly = true)` -- present
- `ProductService.findById()` (line 80): `@Transactional(readOnly = true)` -- present

All read-only service methods are now correctly annotated.

### W9 (implicit). Combined filter + sort on backend -- NEW FIX VERIFIED

The `ProductService.findAll()` method (lines 26-48) now accepts `categoryId`, `search`, and `sort` parameters. The repository has four query variants covering all category+search combinations (`findAllWithCategory`, `findByCategoryIdWithCategory`, `findBySearchWithCategory`, `findByCategoryIdAndSearchWithCategory`). Server-side sorting is implemented via `applySorting()` with a whitelist of allowed fields (line 23). The `useProducts` hook passes `sort` through to the API (line 42). This is a well-structured approach.

### Catch-all exception handler -- VERIFIED

`GlobalExceptionHandler.java:78-89` has a catch-all `@ExceptionHandler(Exception.class)` that logs the full exception with `log.error("Unexpected error", ex)` and returns a generic 500 response without leaking internals. Correct pattern.

---

## Remaining Warnings

### W1. NavItem / MobileNavItem still duplicated (reduced scope)

- **Location**: `Sidebar.tsx:11-40` vs `MobileDrawer.tsx:28-58`
- **Category**: Quality
- **Description**: While icons were extracted to a shared module, the `NavItem` and `MobileNavItem` components remain separate with near-identical rendering logic. The only difference is that `MobileNavItem` accepts an `onClick` prop for closing the drawer. A shared `NavItem` component could accept an optional `onClick` and be reused in both contexts.
- **Risk**: Low. Only two navigation items exist. The duplication is small (~30 lines each) and the components are stable.
- **Recommendation**: Extract a shared `NavItem` that accepts optional `onClick`. Low priority.
- **Fixable**: true

---

## Remaining Informational Items

### I1. Error detection by string matching on status code (retained from R1)

- **Location**: `CategoryListPage.tsx:35`, `CategoryFormPage.tsx:55`
- **Category**: Quality
- **Description**: Error handling checks `err.message.includes("409")` instead of using `err instanceof ApiError && err.status === 409`. The `ApiError` class at `api/client.ts:1-13` has a typed `status` field.
- **Suggestion**: Use the structured property for reliability.

### I2. No pagination on list endpoints (retained from R1, downgraded)

- **Location**: `CategoryController.java:28-30`, `ProductController.java:29-32`
- **Category**: Performance
- **Description**: All records returned without pagination. Acceptable for pre-alpha.

### I3. Remaining hardcoded hex color values (from partial W7 fix)

- **Location**: Throughout `Header.tsx`, `CategoryListPage.tsx`, `CategoryFormPage.tsx`, `ProductListPage.tsx`, `ProductFormPage.tsx`
- **Category**: Quality
- **Description**: Slate palette hex values (`#0F172A`, `#64748B`, `#E2E8F0`, `#94A3B8`, `#334155`, `#1E293B`, `#F8FAFC`, `#F1F5F9`) are used as raw strings instead of Chakra theme tokens. The theme defines `brand` and `accent` but not `slate` tokens.
- **Suggestion**: Add slate color tokens to `theme/index.ts` and reference them semantically. Low priority cosmetic improvement.

### I4. photoUrl @Pattern allows null but does not validate empty string explicitly

- **Location**: `CreateProductRequest.java:14`, `UpdateProductRequest.java:14`
- **Category**: Quality
- **Description**: The `@Pattern` annotation on `photoUrl` validates the regex when a value is present. Since `photoUrl` is not annotated with `@NotBlank`, it can be `null` (which correctly bypasses `@Pattern`). However, an empty string `""` would fail the regex and return a validation error, which is actually the desired behavior. The frontend sends `undefined` for empty URLs (ProductFormPage.tsx:78), which serializes to `null` in JSON. This works correctly end-to-end.
- **Verdict**: No issue -- noting for completeness.

### I5. isValidImageUrl accepts http:// in addition to https://

- **Location**: `utils/url.ts:3`
- **Category**: Security (minor)
- **Description**: The frontend `isValidImageUrl` and backend `@Pattern` both accept `http://` URLs. For a production application, restricting to `https://` only would be safer to prevent mixed-content warnings and MITM attacks on image loading. Acceptable for pre-alpha.
- **Suggestion**: Consider restricting to `https://` only when moving toward production.

---

## Metrics

| Metric | Value | Change from R1 |
|--------|-------|-----------------|
| Max function length (backend) | ~28 lines (ProductService.applySorting) | +8 (new sort logic) |
| Max function length (frontend) | ~85 lines (ProductListPage component) | No change |
| Max nesting depth | 3 levels | No change |
| Potential security vulnerabilities | 0 | -1 (photoUrl fixed) |
| N+1 query risks | 0 | No change |
| Code duplication instances | 1 (NavItem/MobileNavItem) | -2 (icons, formatDate extracted) |
| Files analyzed | 39 | No change |
| New shared modules created | 3 (Icons.tsx, format.ts, url.ts) | +3 |

---

## Standards Compliance Summary

| Standard | Compliance | Change from R1 |
|----------|-----------|-----------------|
| BaseEntity pattern | Compliant | -- |
| Business key equals/hashCode | Compliant | -- |
| LAZY fetch default | Compliant | -- |
| No @Data/@EqualsAndHashCode | Compliant | -- |
| Parameterized queries | Compliant | -- |
| N+1 prevention | Compliant | -- |
| RESTful API design | Compliant | -- |
| Cross-module references | Non-compliant (accepted) | -- |
| API versioning | Non-compliant (deferred) | -- |
| Design tokens (CSS) | Partially compliant | Improved (brand/accent used more, slate still hardcoded) |
| Accessibility | Partially compliant | -- |
| Input validation | Compliant | Improved (photoUrl server-side validation added) |
| Error handling | Compliant | Improved (catch-all handler, typed exceptions) |
| Transaction management | Compliant | Improved (readOnly added) |

---

## Prioritized Recommendations

1. **[Warning]** Extract shared `NavItem` component to eliminate remaining Sidebar/MobileDrawer duplication.
2. **[Info]** Use `ApiError.status` property instead of string matching for error detection in `CategoryListPage` and `CategoryFormPage`.
3. **[Info]** Add slate color tokens to `theme/index.ts` and replace hardcoded hex values throughout components.
4. **[Info]** Add pagination support before dataset growth (deferred from R1).
5. **[Info]** Consider restricting photoUrl to `https://` only for production readiness.
