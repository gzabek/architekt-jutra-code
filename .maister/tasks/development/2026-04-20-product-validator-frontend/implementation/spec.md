# Specification: Product Validator Frontend Integration

## 1. Overview and Goals

Integrate the `product-validator` plugin into the Product Detail page so users can trigger on-demand AI validation of a product's category, description, and price. Validation results are displayed as colour-coded Chakra `Badge` components with tooltip details, inline next to each respective field.

The feature must degrade gracefully: it is hidden when the plugin is disabled or absent, and shows nothing when the API call fails.

---

## 2. Scope

### In scope
- `POST /api/products/{productId}/validate` API call and TypeScript interfaces
- `useProductValidation` custom hook encapsulating validation state and trigger
- `isPluginEnabled(id)` helper on `PluginContext`
- Inline validation badges next to Category, Description, and Price fields on `ProductDetailPage`
- A "Validate" button visible only when the `product-validator` plugin is enabled
- Loading state on the button while the call is in flight
- Grey "Unavailable" badge when the backend returns `confidence: 'LOW'` with `suggestion: "LLM unavailable"`
- Unit / integration tests for the new behaviour

### Out of scope
- Reading or falling back to `product.pluginData["product-validator"]` (error policy = show nothing)
- Any backend changes (the endpoint is already implemented)
- Validation on ProductListPage or any other page
- Persisting validation results across page refreshes (in-memory only)

---

## 3. Functional Requirements

### 3.1 Plugin Guard
**FR-1** The "Validate" button and all validation badges MUST NOT render when the `product-validator` plugin is not present in the plugin list or is disabled (`enabled: false`).  
**FR-2** The guard is evaluated reactively: if plugins load after the product, the button appears once plugin data is available.

### 3.2 Validate Button
**FR-3** A "Validate" button is rendered in the Details tab panel, positioned after the product fields section (below Price/SKU/Category/Description rows), visible only when FR-1 is satisfied.  
**FR-4** Clicking "Validate" calls `POST /api/products/{productId}/validate`. The button shows a loading indicator and is disabled while the request is in flight.  
**FR-5** The button returns to its normal state when the request completes (success or error).

### 3.3 Validation Badges
**FR-6** After a successful response, a `Badge` is rendered inline next to:
  - the **Category** field label/value row
  - the **Description** field label/value row (only when description is present)
  - the **Price** field label/value row

**FR-7** Badge colour is determined by the `CheckResult` for that field:

| Condition | Colour | Label |
|---|---|---|
| `valid: true` and `confidence: 'HIGH'` | `green` | Valid |
| `valid: true` and `confidence: 'MEDIUM'` | `orange` | Valid |
| `valid: true` and `confidence: 'LOW'` | `orange` | Valid |
| `valid: false` and confidence any | `red` | Invalid |
| LLM unavailable (see FR-8) | `gray` | Unavailable |

**FR-8** LLM unavailability is detected when `suggestion === "LLM unavailable"` (the sentinel value the backend returns via `CheckResult.unavailable()`). In that case the badge is grey with label "Unavailable" regardless of other fields.

**FR-9** Each badge has a Chakra `Tooltip` that shows `explanation` text (and `suggestion` text if non-empty and different from the explanation) on hover.

### 3.4 Error Fallback
**FR-10** If the API call throws (network error, 4xx, 5xx), all badges that may have been shown from a previous successful run remain visible. The button simply becomes clickable again. No error message is shown. (No fallback to `pluginData`.)

### 3.5 Initial State
**FR-11** On page load, no badges are shown. Badges only appear after the user explicitly clicks "Validate".

---

## 4. Technical Design

### 4.1 New TypeScript Interfaces — `src/api/products.ts`

Add the following exports:

```typescript
export type ValidationConfidence = 'HIGH' | 'MEDIUM' | 'LOW';

export interface CheckResult {
  valid: boolean;
  suggestion: string;
  confidence: ValidationConfidence;
  explanation: string;
}

export interface ValidationResult {
  productId: number;
  categoryValidation: CheckResult;
  descriptionValidation: CheckResult;
  priceValidation: CheckResult;
}

export function validateProduct(id: number): Promise<ValidationResult> {
  return api.post(`/products/${id}/validate`, {});
}
```

### 4.2 `isPluginEnabled` helper — `src/plugins/PluginContext.tsx`

Add to the `PluginContextValue` interface:

```typescript
isPluginEnabled: (pluginId: string) => boolean;
```

Implement in `PluginProvider` using `useCallback`:

```typescript
const isPluginEnabled = useCallback(
  (pluginId: string) => plugins.some((p) => p.id === pluginId && p.enabled),
  [plugins],
);
```

Include in the `value` object.

### 4.3 `useProductValidation` hook — `src/hooks/useProductValidation.ts`

**Public API:**

```typescript
interface UseProductValidationReturn {
  validationResult: ValidationResult | null;
  isValidating: boolean;
  validate: () => Promise<void>;
}

export function useProductValidation(productId: number): UseProductValidationReturn
```

**Implementation rules:**
- `validationResult` starts as `null`; is set to the resolved `ValidationResult` on success; is NOT cleared on error (preserves last good result per FR-10).
- `isValidating` is `true` only while the request is in flight.
- `validate` calls `validateProduct(productId)`. On rejection it silently swallows the error (no rethrow, no state change to `validationResult`).
- The hook uses `useState` only — no `useReducer` or `useEffect` needed.

### 4.4 Component changes — `src/pages/ProductDetailPage.tsx`

**4.4.1 Imports to add:**
- `Badge`, `Button`, `Tooltip` from `@chakra-ui/react`
- `useProductValidation` from `../hooks/useProductValidation`
- `isPluginEnabled` from `usePluginContext()`

**4.4.2 Hook usage:**
```typescript
const { validationResult, isValidating, validate } = useProductValidation(product.id);
const validatorEnabled = isPluginEnabled('product-validator');
```

**4.4.3 `ValidationBadge` — inline helper component (co-located in the file):**

```typescript
interface ValidationBadgeProps {
  result: CheckResult;
}

function ValidationBadge({ result }: ValidationBadgeProps) { ... }
```

Badge colour mapping:

```typescript
function badgeColorScheme(result: CheckResult): string {
  if (result.suggestion === 'LLM unavailable') return 'gray';
  if (!result.valid) return 'red';
  if (result.confidence === 'HIGH') return 'green';
  return 'orange'; // MEDIUM or LOW valid
}

function badgeLabel(result: CheckResult): string {
  if (result.suggestion === 'LLM unavailable') return 'Unavailable';
  return result.valid ? 'Valid' : 'Invalid';
}
```

Tooltip content: `result.explanation`, optionally appended with `result.suggestion` if it differs from `explanation` and is non-empty.

**4.4.4 Field row layout:**  
Each field (`Price`, `Category`, `Description`) row becomes a `Flex` row with `align="center"` and `gap="8px"`. The badge is rendered to the right of the value text, conditionally on `validationResult !== null`.

Example for Category:
```tsx
<Flex align="center" gap="8px">
  <Text color="#334155">{product.category.name}</Text>
  {validationResult && (
    <ValidationBadge result={validationResult.categoryValidation} />
  )}
</Flex>
```

**4.4.5 Validate button placement:**  
After the `<Flex direction="column" gap="12px">` field list, add:

```tsx
{validatorEnabled && (
  <Box mt="16px">
    <Button
      size="sm"
      onClick={() => void validate()}
      isLoading={isValidating}
      loadingText="Validating…"
    >
      Validate
    </Button>
  </Box>
)}
```

### 4.5 Graceful Degradation Rules

| Scenario | Behaviour |
|---|---|
| `product-validator` plugin absent or disabled | Button hidden, no badges rendered |
| API call in flight | Button disabled with spinner and "Validating…" text |
| API call succeeds | Badges appear inline; button re-enabled |
| API call fails (any error) | Button re-enabled; previously shown badges remain (or none if first call) |
| `CheckResult` has `suggestion === "LLM unavailable"` | Grey "Unavailable" badge with tooltip explanation |

---

## 5. Acceptance Criteria

**AC-1** Given the `product-validator` plugin is disabled/absent, no "Validate" button or badges are visible on the product detail page.

**AC-2** Given the plugin is enabled and the user clicks "Validate", the button becomes disabled with a spinner until the API responds.

**AC-3** Given the API returns a successful `ValidationResult` with all `valid: true, confidence: 'HIGH'`, three green "Valid" badges appear next to Category, Description, and Price.

**AC-4** Given a field's `CheckResult` has `valid: false`, the corresponding badge is red with label "Invalid".

**AC-5** Given a field's `CheckResult` has `confidence: 'MEDIUM'` or `'LOW'` and `valid: true`, the badge is orange with label "Valid".

**AC-6** Given a field's `CheckResult` has `suggestion === "LLM unavailable"`, the badge is grey with label "Unavailable".

**AC-7** Hovering over any badge shows a Chakra Tooltip with the `explanation` text.

**AC-8** Given the API call fails, the button returns to its normal state and no new badges appear (existing badges from a prior successful call remain).

**AC-9** On initial page load (before "Validate" is clicked), no badges are rendered regardless of plugin state.

**AC-10** Given the product has no description, no badge is rendered for the description field even after successful validation (the description row is not shown).

---

## 6. Files to Create / Modify

### CREATE
| File | Purpose |
|---|---|
| `src/main/frontend/src/hooks/useProductValidation.ts` | Custom hook encapsulating validation state and API call |

### MODIFY
| File | Change |
|---|---|
| `src/main/frontend/src/api/products.ts` | Add `ValidationConfidence`, `CheckResult`, `ValidationResult` interfaces and `validateProduct` function |
| `src/main/frontend/src/plugins/PluginContext.tsx` | Add `isPluginEnabled(id: string): boolean` to context interface and implementation |
| `src/main/frontend/src/pages/ProductDetailPage.tsx` | Add `ValidationBadge` helper, wire `useProductValidation` hook, add "Validate" button and inline badges |
| `src/main/frontend/src/test/pages.test.tsx` | Add `validateProduct` to the `vi.mock('../api/products', ...)` factory; add `describe` block for ProductDetailPage validation scenarios covering AC-1 through AC-10 |

### Test scenarios to add in `pages.test.tsx`

1. **Plugin disabled** → Validate button not rendered.
2. **Plugin enabled, pre-click** → Validate button rendered, no badges present.
3. **Click Validate → loading** → button disabled, spinner visible.
4. **Successful validation, all HIGH valid** → three green "Valid" badges present.
5. **Successful validation, one field invalid** → corresponding badge is red "Invalid".
6. **Successful validation, MEDIUM confidence** → badge is orange "Valid".
7. **LLM unavailable result** → grey "Unavailable" badge.
8. **API error on validate** → no badges rendered, button re-enabled.
9. **No description on product** → no description badge after validation.
