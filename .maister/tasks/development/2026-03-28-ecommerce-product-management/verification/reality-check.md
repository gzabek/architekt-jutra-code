# Reality Check: Ecommerce Product Management (Round 2)

**Date**: 2026-03-28
**Status**: READY
**Round**: 2 (re-assessment after fixes for Round 1 gaps)

---

## 1. Deployment Decision

**GO** -- Both medium-severity gaps identified in Round 1 have been fixed. The combined category+search filter now composes both conditions, and the sort parameter is now accepted and applied server-side. All 51 tests pass. No remaining functional gaps block deployment.

---

## 2. Round 1 Gap Verification

### Gap 1: Combined Category + Search Filter (was Medium -- RESOLVED)

**Round 1 Finding**: `ProductService.findAll()` used if/else-if logic, making `categoryId` take precedence over `search`. Users could not filter by category AND search simultaneously.

**Round 2 Reality**: FIXED. The service now has four-way branching:

- `hasCategory && hasSearch` -> calls `findByCategoryIdAndSearchWithCategory(categoryId, search)`
- `hasCategory` only -> calls `findByCategoryIdWithCategory(categoryId)`
- `hasSearch` only -> calls `findBySearchWithCategory(search)`
- Neither -> calls `findAllWithCategory()`

**Evidence**: `ProductService.java` lines 32-39 show the corrected logic. The combined case is handled first, before the individual filters.

**Repository Support**: `ProductRepository.java` line 29-30 declares a new JPQL query `findByCategoryIdAndSearchWithCategory` that applies both `WHERE p.category.id = :categoryId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))` with `JOIN FETCH p.category`. The query uses bind parameters (no SQL injection risk) and includes the JOIN FETCH for N+1 prevention -- consistent with all other query methods in the repository.

**Verdict**: Genuinely fixed. The if/else-if anti-pattern is gone. Both filter conditions compose correctly.

### Gap 2: Server-Side Sort Not Implemented (was Medium -- RESOLVED)

**Round 1 Finding**: `ProductController` had no `sort` query parameter. Sorting was done client-side only in the `useProducts` hook.

**Round 2 Reality**: FIXED. Sort is now implemented at two levels:

**Backend (server-side)**:
- `ProductController.list()` now accepts `@RequestParam(required = false) String sort` (line 32)
- `ProductService.findAll()` accepts the sort parameter and delegates to `applySorting()` (line 46)
- `applySorting()` (lines 51-78) parses `field,direction` format (e.g., `price,desc`), validates against an allowlist of 4 fields (`name`, `price`, `sku`, `createdAt`), and sorts using typed Comparators
- Invalid sort fields are silently ignored (safe default behavior)
- Case-insensitive comparison for string fields (`name`, `sku`)

**Frontend (passes sort to backend)**:
- `useProducts.ts` line 42: sort parameter is formatted as `${sortField},asc` and passed to the API call
- `api/products.ts` lines 34-38: `ProductSearchParams` interface includes `sort?: string`, and `getProducts()` appends it as a query parameter

**Implementation Note**: The sort is applied in-memory on the Java side (Comparator on the response list) rather than via SQL ORDER BY. This is functionally correct for the current dataset size (no pagination, full dataset loaded). If pagination is added later, sorting would need to move to the database query. For the current MVP scope, this works correctly.

**Verdict**: Genuinely fixed. The `sort` parameter flows end-to-end from frontend through API to backend processing.

### Gap 3: No Combined Filter Test (was Low -- NOT ADDRESSED)

**Round 1 Finding**: No integration test exercises `?category=1&search=term` simultaneously.

**Round 2 Reality**: Still no test. The existing 9 `ProductIntegrationTests` remain unchanged -- `listProducts_filteredByCategory_returnsFilteredResults` and `listProducts_withSearch_returnsCaseInsensitiveMatches` still test these scenarios independently. No new test was added for the combined case, and no test for the sort parameter either.

**Impact**: Low. The code path for combined filtering is straightforward (a single JPQL query) and unlikely to break independently. The sort logic is also simple. However, these are now untested code paths. The 51 tests passing means the fixes did not break anything, but the new functionality is not directly exercised by tests.

**Severity downgrade**: Remains Low. The code is simple enough that the risk of undetected regression is minimal, and the combined query follows the exact same pattern as the three existing query methods.

---

## 3. Test Results (from test-suite-runner, not re-executed)

| Metric | Value |
|--------|-------|
| Total Tests | 51 |
| Passing | 51 |
| Failing | 0 |
| Pass Rate | 100% |

Backend: 44/44 (CategoryIntegrationTests 8, CategoryValidationTests 3, ProductIntegrationTests 9, ProductValidationTests 7, core tests 17)
Frontend: 7/7 (foundation 3, pages 4)

No regressions introduced by the fixes.

---

## 4. Remaining Issues (Non-Blocking)

These items were identified in Round 1 and remain unchanged. None block deployment.

| # | Issue | Severity | Status |
|---|-------|----------|--------|
| 1 | No test for combined category+search | Low | Open (no new test added) |
| 2 | No test for sort parameter | Low | Open (no new test added) |
| 3 | Unsanitized photoUrl in img tags | Low (pre-alpha) | Open (from code review) |
| 4 | Sort is in-memory, not SQL | Info | Acceptable for MVP without pagination |
| 5 | Two unused repository methods | Low | Open (findByCategoryId, findByNameContainingIgnoreCase) |
| 6 | spring.jpa.open-in-view enabled | Low | Open |
| 7 | Hardcoded badge color map | Low | Open |

---

## 5. Functional Completeness

| Capability | Status |
|------------|--------|
| Category CRUD (create, list, get, update, delete) | Working |
| Category delete protection (409 when has products) | Working |
| Category duplicate name detection (409) | Working |
| Product CRUD (create, list, get, update, delete) | Working |
| Product duplicate SKU detection (409) | Working |
| Filter products by category | Working |
| Search products by name | Working |
| Combined category filter + search | Working (fixed in Round 2) |
| Server-side sort by name/price/sku/createdAt | Working (fixed in Round 2) |
| Validation with per-field errors (400) | Working |
| Frontend build and navigation | Working |
| Database migrations | Working |
| N+1 query prevention (JOIN FETCH) | Working |

All spec requirements are now met.

---

## 6. Summary

Round 2 confirms both Round 1 gaps are genuinely fixed:

1. **Combined filter**: The if/else-if anti-pattern in `ProductService.findAll()` was replaced with proper four-way branching. A new JPQL query in `ProductRepository` handles the combined `categoryId + search` case with JOIN FETCH and bind parameters.

2. **Server-side sort**: The `sort` query parameter is now accepted by `ProductController`, parsed and validated in `ProductService.applySorting()`, and passed from the frontend through `useProducts` and `api/products.ts`. Sort uses an allowlist of fields and typed Comparators.

The only remaining observation is that neither fix has a dedicated integration test. The existing 51 tests all pass and no regressions were introduced, but the new code paths (combined filter query, sort parameter processing) are exercised only through code review, not through automated tests. This is low-risk given the simplicity of the changes.

Status upgraded from CONDITIONAL GO to GO.
