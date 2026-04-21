# Codebase Analysis Report

**Task**: Change frontend code so it uses the product validator plugin. For a given product, call the `productValidationController` REST API and display labels based on the result of category, description and price validation. The frontend should still work if there is no data from the product validator plugin.

**Date**: 2026-04-20  
**Risk Level**: Low  
**Complexity**: Low–Medium

---

## Summary

The task is a focused native host-app UI enhancement. The backend already exposes a `POST /api/products/{productId}/validate` endpoint with a fully-defined contract. The frontend does not yet call this endpoint, display validation labels, or have any product-detail UI tests.

The implementation is entirely self-contained: one new custom hook, a small API client addition, and in-place label rendering inside `ProductDetailPage`. No architectural changes are needed. Graceful degradation is straightforward because the plugin enablement check and API error handling can both suppress the labels without breaking existing functionality.

---

## Architecture

### Frontend stack
- **React + TypeScript** (strict mode, Vite build)
- **Chakra UI** for UI primitives
- **Vitest + Testing Library** for tests
- Custom `api` client wrapping `api.get` / `api.post` calls against the `/api` base URL
- Plugin system: `PluginContext` exposes extension-point helpers (`getProductDetailTabs`, `getProductDetailInfo`, etc.) and provides access to the loaded plugin list

### Plugin integration model
The codebase supports two plugin surface types:
1. **Iframe plugins** — rendered via `PluginFrame` inside tabs or info panels
2. **Native host-app features** — host app calls the plugin's REST API directly and renders results inline

The `product-validator` plugin has **no registered iframe** in `src/main/frontend/src/plugins/`. The task therefore falls into category 2: a direct REST call from `ProductDetailPage`, with results rendered as inline labels. This is confirmed by the instruction "call `productValidationController` REST API and display labels".

### Extension points (for reference, not used by this task)
- `PRODUCT_DETAIL_INFO = "product.detail.info"`
- `PRODUCT_DETAIL_TABS = "product.detail.tabs"`

---

## Key Files

| File | Action | Reason |
|------|--------|--------|
| `src/api/products.ts` | Modify | Add `validateProduct(id)` function and TypeScript interfaces (`CheckResult`, `ValidationResult`) |
| `src/hooks/useProductValidation.ts` | Create | Custom hook: calls `validateProduct`, handles loading/error state, exposes per-field validation results |
| `src/pages/ProductDetailPage.tsx` | Modify | Call the hook, render validation badges inline next to Category, Description, and Price fields |
| `src/plugin-sdk/PluginContext.tsx` | Modify (minor) | Expose a helper (e.g. `isPluginEnabled(id)`) so the page can guard validation calls behind plugin presence |
| `src/test/ProductDetailPage.test.tsx` | Create / Modify | Add tests: basic render, validation labels shown, validation API unavailable (graceful degradation) |

---

## Backend Contract

### Endpoint
```
POST /api/products/{productId}/validate
```

### Response: `ValidationResult`
```typescript
interface ValidationResult {
  productId: number;
  categoryValidation:    CheckResult;
  descriptionValidation: CheckResult;
  priceValidation:       CheckResult;
}

interface CheckResult {
  valid:       boolean;
  suggestion:  string;
  confidence:  'HIGH' | 'MEDIUM' | 'LOW';
  explanation: string;
}
```

### Unavailability sentinel
When the LLM backing the validator is unreachable the backend returns a `CheckResult` with:
- `valid: false`
- `suggestion: "LLM unavailable"`
- `confidence: LOW`
- `explanation: "Could not reach the LLM service."`

The frontend must treat this gracefully (display a neutral/warning state rather than an error).

### Cached result
`ProductResponse.pluginData["product-validator"]` may already contain the last cached validation result. The hook should prefer the live API response but can fall back to this cached value when the plugin is enabled but the call fails — or simply show no labels if neither is available.

---

## Data Flow

```
ProductDetailPage mounts
  │
  ├─ getProduct(id)          → GET /api/products/:id
  │    └─ product.pluginData["product-validator"]  (cached, optional)
  │
  └─ useProductValidation(productId, isPluginEnabled)
       │
       ├─ [plugin not enabled] → returns null, no labels rendered
       │
       ├─ validateProduct(id)  → POST /api/products/:id/validate
       │    ├─ success         → ValidationResult → render badges
       │    └─ error / timeout → graceful degradation, no labels / fallback to cached
       │
       └─ ValidationResult
            ├─ categoryValidation    → badge next to Category field
            ├─ descriptionValidation → badge next to Description field
            └─ priceValidation       → badge next to Price field
```

---

## Graceful Degradation Strategy

The frontend must remain fully functional when:
1. The `product-validator` plugin is not installed / not enabled
2. The `POST /validate` call returns a network error or non-2xx status
3. The LLM is unavailable (sentinel `CheckResult` from backend)

Recommended guard order in `useProductValidation`:
1. Check `isPluginEnabled("product-validator")` — if false, return `null` immediately
2. Wrap the API call in try/catch — on error, return `null`
3. Caller in `ProductDetailPage` renders validation labels **only** when the hook returns a non-null result

---

## Risk Assessment

| Area | Risk | Notes |
|------|------|-------|
| Backend contract stability | Low | Endpoint and types are fully defined and already implemented |
| Plugin enablement check | Low | `PluginContext` already holds the plugin list; minor helper needed |
| Graceful degradation | Low | Simple null-check pattern; no shared state mutation |
| Existing page regressions | Low | Labels are additive; existing fields are not restructured |
| Test coverage gap | Medium | `ProductDetailPage` currently has no tests; new tests must be created from scratch |

**Overall risk: Low** — no database migrations, no API contract changes, no shared-component modifications.

---

## Integration Points

- `api/products.ts` — existing module; `validateProduct` follows the same pattern as `getProduct`
- `PluginContext` — already used by `ProductDetailPage` for tab/info panel rendering; minimal addition needed
- Chakra UI — validation badges should use existing Chakra `Badge` or `Tag` components with semantic colors (`green` / `red` / `orange` for HIGH/MEDIUM/LOW confidence)

---

## Test Strategy

| Test case | Type |
|-----------|------|
| ProductDetailPage renders product fields (baseline) | Unit |
| Validation labels shown when plugin is enabled and API succeeds | Unit |
| Validation labels hidden when plugin is disabled | Unit |
| Validation labels hidden / page intact when API call fails | Unit |
| LLM-unavailable sentinel produces neutral label (not hard error) | Unit |

Mock target: `src/api/products.ts` — both `getProduct` and `validateProduct` should be mockable via `vi.mock()`.

---

## Files Explored

- `src/api/products.ts`
- `src/pages/ProductDetailPage.tsx`
- `src/plugin-sdk/PluginContext.tsx`
- `src/plugins/` (scanned — no product-validator iframe plugin present)
- `src/test/` (scanned — no existing ProductDetailPage test)
