# Implementation Plan: Product Validator Frontend Integration

## Overview

Add on-demand AI validation badges to `ProductDetailPage` via a new `useProductValidation` hook, a `validateProduct` API call, and an `isPluginEnabled` context helper. Five files touched; 9 test scenarios.

**Task characteristics**: ui_heavy, modifies_existing_code  
**Risk level**: low  
**Estimated groups**: 5

---

## Standards Compliance

- TypeScript strict mode — no `any`, explicit types on public interfaces
- Chakra UI (`@chakra-ui/react`) for all UI primitives (`Badge`, `Button`, `Tooltip`, `Flex`)
- Vitest + Testing Library — test files in `src/test/`, `vi.mock()` + `vi.resetAllMocks()` + `renderWithProviders()`
- API calls isolated in `src/api/`; custom hooks in `src/hooks/`
- `useCallback` for memoised context functions; `useState` only in the hook (no `useReducer`/`useEffect`)

---

## Group 1 — API Layer: TypeScript interfaces + `validateProduct`

**File**: `src/main/frontend/src/api/products.ts`

- [ ] 1.1 Write type-level tests (compile-time check via import assertions in a scratch `.test-types.ts` or rely on Group 5 integration tests to catch interface shape errors — note: no dedicated unit test file needed for this group; compilation errors will surface in Group 5 tests)
- [ ] 1.2 Add `ValidationConfidence` union type export:
  ```typescript
  export type ValidationConfidence = 'HIGH' | 'MEDIUM' | 'LOW';
  ```
- [ ] 1.3 Add `CheckResult` interface export:
  ```typescript
  export interface CheckResult {
    valid: boolean;
    suggestion: string;
    confidence: ValidationConfidence;
    explanation: string;
  }
  ```
- [ ] 1.4 Add `ValidationResult` interface export:
  ```typescript
  export interface ValidationResult {
    productId: number;
    categoryValidation: CheckResult;
    descriptionValidation: CheckResult;
    priceValidation: CheckResult;
  }
  ```
- [ ] 1.5 Add `validateProduct` function export using the existing `api` client:
  ```typescript
  export function validateProduct(id: number): Promise<ValidationResult> {
    return api.post(`/products/${id}/validate`, {});
  }
  ```
- [ ] 1.6 Run TypeScript compiler check: `npm run build` (or `npx tsc --noEmit`) to confirm no type errors introduced

---

## Group 2 — Plugin Context: `isPluginEnabled` helper

**File**: `src/main/frontend/src/plugins/PluginContext.tsx`

- [ ] 2.1 Write tests — Group 5 (`pages.test.tsx`) will exercise `isPluginEnabled` indirectly; no separate unit test required. Verify in the existing test harness that mocking `getPlugins` with an enabled plugin causes the Validate button to appear (covered in test scenarios 1 & 2).
- [ ] 2.2 Add `isPluginEnabled` to the `PluginContextValue` interface:
  ```typescript
  isPluginEnabled: (pluginId: string) => boolean;
  ```
  Insert after the existing `getProductDetailInfo` line in the interface.
- [ ] 2.3 Implement `isPluginEnabled` in `PluginProvider` body using `useCallback` (place after `getProductDetailInfo`):
  ```typescript
  const isPluginEnabled = useCallback(
    (pluginId: string) => plugins.some((p) => p.id === pluginId && p.enabled),
    [plugins],
  );
  ```
- [ ] 2.4 Add `isPluginEnabled` to the `value` object in `useMemo` (both the object literal and the dependency array):
  - Object literal: append `isPluginEnabled`
  - Dependency array: append `isPluginEnabled`
- [ ] 2.5 Run `npx tsc --noEmit` — confirm no type errors in PluginContext.tsx

---

## Group 3 — Custom Hook: `useProductValidation`

**File**: `src/main/frontend/src/hooks/useProductValidation.ts` *(new file)*

- [ ] 3.1 Write tests — Group 5 integration tests will exercise the hook through `ProductDetailPage`; no isolated hook unit test needed. Ensure test scenarios 3 (loading state), 4 (success), 8 (API error) adequately cover hook state transitions.
- [ ] 3.2 Create `src/main/frontend/src/hooks/useProductValidation.ts` with the public return interface:
  ```typescript
  import { useState } from 'react';
  import { validateProduct } from '../api/products';
  import type { ValidationResult } from '../api/products';

  interface UseProductValidationReturn {
    validationResult: ValidationResult | null;
    isValidating: boolean;
    validate: () => Promise<void>;
  }
  ```
- [ ] 3.3 Implement `useProductValidation(productId: number)`:
  - `validationResult` starts as `null` via `useState<ValidationResult | null>(null)`
  - `isValidating` starts as `false` via `useState(false)`
  - `validate` function:
    - Sets `isValidating(true)`
    - Calls `validateProduct(productId)`
    - On success: calls `setValidationResult(result)` then `setIsValidating(false)`
    - On rejection: calls `setIsValidating(false)` only — does NOT clear `validationResult` (preserves last good result per FR-10); swallows error silently (no rethrow)
  - Returns `{ validationResult, isValidating, validate }`
- [ ] 3.4 Run `npx tsc --noEmit` — confirm no type errors in the new hook file

---

## Group 4 — UI Layer: `ValidationBadge` + `ProductDetailPage` changes

**File**: `src/main/frontend/src/pages/ProductDetailPage.tsx`

- [ ] 4.1 Write tests — covered comprehensively in Group 5 (`pages.test.tsx`). Scenarios 1–9 cover all UI states. Write those tests before completing this group's implementation steps (see Group 5 step 5.1).
  > **Dependency note**: Complete steps 5.1–5.4 (writing the failing tests) before finalising 4.4–4.9 so the TDD feedback loop works. Steps 4.2–4.3 (imports + hook wiring) can be done independently.
- [ ] 4.2 Add new imports to `ProductDetailPage.tsx`:
  - From `@chakra-ui/react`: add `Badge`, `Button`, `Tooltip` to the existing destructured import
  - Add: `import { useProductValidation } from '../hooks/useProductValidation';`
  - Extend existing products API import: add `type CheckResult, type ValidationResult` (needed for `ValidationBadge` props types — both are used in this file)
- [ ] 4.3 Wire hooks inside `ProductDetailPage` function body (after existing hook calls):
  ```typescript
  const { validationResult, isValidating, validate } = useProductValidation(product.id);
  const { isPluginEnabled } = usePluginContext();
  const validatorEnabled = isPluginEnabled('product-validator');
  ```
  > Note: `isPluginEnabled` must be destructured from the existing `usePluginContext()` call — update that destructure rather than adding a second `usePluginContext()` call.
- [ ] 4.4 Add `badgeColorScheme` pure function (above the component, alongside `formatPrice`):
  ```typescript
  function badgeColorScheme(result: CheckResult): string {
    if (result.suggestion === 'LLM unavailable') return 'gray';
    if (!result.valid) return 'red';
    if (result.confidence === 'HIGH') return 'green';
    return 'orange';
  }
  ```
- [ ] 4.5 Add `badgeLabel` pure function (alongside `badgeColorScheme`):
  ```typescript
  function badgeLabel(result: CheckResult): string {
    if (result.suggestion === 'LLM unavailable') return 'Unavailable';
    return result.valid ? 'Valid' : 'Invalid';
  }
  ```
- [ ] 4.6 Add `ValidationBadge` inline helper component (above `ProductDetailPage`, alongside the pure functions):
  ```typescript
  interface ValidationBadgeProps {
    result: CheckResult;
  }

  function ValidationBadge({ result }: ValidationBadgeProps) {
    const tooltipLabel =
      result.suggestion && result.suggestion !== result.explanation
        ? `${result.explanation} ${result.suggestion}`
        : result.explanation;
    return (
      <Tooltip label={tooltipLabel}>
        <Badge colorScheme={badgeColorScheme(result)}>{badgeLabel(result)}</Badge>
      </Tooltip>
    );
  }
  ```
- [ ] 4.7 Wrap the **Price** value `<Text>` in a `<Flex align="center" gap="8px">` and add conditional badge:
  ```tsx
  <Flex align="center" gap="8px">
    <Text fontSize="20px" fontWeight="700" color="brand.700">
      {formatPrice(product.price)}
    </Text>
    {validationResult && (
      <ValidationBadge result={validationResult.priceValidation} />
    )}
  </Flex>
  ```
- [ ] 4.8 Wrap the **Category** value `<Text>` in a `<Flex align="center" gap="8px">` and add conditional badge:
  ```tsx
  <Flex align="center" gap="8px">
    <Text color="#334155">{product.category.name}</Text>
    {validationResult && (
      <ValidationBadge result={validationResult.categoryValidation} />
    )}
  </Flex>
  ```
- [ ] 4.9 Wrap the **Description** value `<Text>` (inside the `product.description &&` conditional block) in a `<Flex align="center" gap="8px">` and add conditional badge:
  ```tsx
  <Flex align="center" gap="8px">
    <Text color="#334155">{product.description}</Text>
    {validationResult && (
      <ValidationBadge result={validationResult.descriptionValidation} />
    )}
  </Flex>
  ```
- [ ] 4.10 Add the **Validate button** after the closing `</Flex>` of the `direction="column" gap="12px"` field list (before `</Box>` that closes `flex="1"`):
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
- [ ] 4.11 Run `npx tsc --noEmit` — confirm no type errors in `ProductDetailPage.tsx`

---

## Group 5 — Tests: `ProductDetailPage` validation scenarios

**File**: `src/main/frontend/src/test/pages.test.tsx`

- [ ] 5.1 Add `validateProduct` to the existing `vi.mock('../api/products', ...)` factory (line 36–42). The mock factory must export all existing functions **plus** `validateProduct`:
  ```typescript
  vi.mock("../api/products", () => ({
    getProducts: vi.fn(),
    getProduct: vi.fn(),
    createProduct: vi.fn(),
    updateProduct: vi.fn(),
    deleteProduct: vi.fn(),
    validateProduct: vi.fn(),   // ← add this line
  }));
  ```
- [ ] 5.2 Add a `mockPlugin` fixture for an enabled `product-validator` plugin (place with other mock data, after `mockProducts`):
  ```typescript
  import type { PluginResponse } from '../api/plugins';

  const mockProductValidatorPlugin: PluginResponse = {
    id: 'product-validator',
    name: 'Product Validator',
    url: 'http://localhost:9001',
    enabled: true,
    extensionPoints: [],
    version: '1.0.0',
  };
  ```
  > Check the `PluginResponse` interface in `src/api/plugins.ts` and match the required fields exactly.
- [ ] 5.3 Add a `mockValidationResult` fixture (full HIGH/valid result):
  ```typescript
  import type { ValidationResult } from '../api/products';

  const mockValidationResult: ValidationResult = {
    productId: 1,
    categoryValidation:    { valid: true,  confidence: 'HIGH',   suggestion: '',                  explanation: 'Category matches product' },
    descriptionValidation: { valid: true,  confidence: 'HIGH',   suggestion: '',                  explanation: 'Description is accurate' },
    priceValidation:       { valid: true,  confidence: 'HIGH',   suggestion: '',                  explanation: 'Price is reasonable' },
  };
  ```
- [ ] 5.4 Add a `describe("ProductDetailPage validation", ...)` block at the end of the file with the 9 test scenarios below. Each test uses `renderWithProviders(<ProductDetailPage />, "/products/1")` and awaits the product name to confirm the page loaded.
- [ ] 5.5 **Scenario 1 — plugin disabled**: `getPlugins` returns `[]` → "Validate" button not in document:
  ```typescript
  it('does not render Validate button when product-validator plugin is disabled', async () => {
    vi.mocked(pluginsApi.getPlugins).mockResolvedValue([]);
    const { ProductDetailPage } = await import('../pages/ProductDetailPage');
    renderWithProviders(<ProductDetailPage />, '/products/1');
    await screen.findByText('Wireless Headphones Pro');
    expect(screen.queryByRole('button', { name: /validate/i })).not.toBeInTheDocument();
  });
  ```
- [ ] 5.6 **Scenario 2 — plugin enabled, pre-click**: `getPlugins` returns `[mockProductValidatorPlugin]` → "Validate" button present, no badges yet:
  ```typescript
  it('renders Validate button when product-validator plugin is enabled', async () => {
    vi.mocked(pluginsApi.getPlugins).mockResolvedValue([mockProductValidatorPlugin]);
    const { ProductDetailPage } = await import('../pages/ProductDetailPage');
    renderWithProviders(<ProductDetailPage />, '/products/1');
    expect(await screen.findByRole('button', { name: /validate/i })).toBeInTheDocument();
    expect(screen.queryByText('Valid')).not.toBeInTheDocument();
  });
  ```
- [ ] 5.7 **Scenario 3 — loading state**: `validateProduct` returns a never-resolving promise → button disabled with "Validating…" text:
  ```typescript
  it('disables button with loading text while validation is in flight', async () => {
    vi.mocked(pluginsApi.getPlugins).mockResolvedValue([mockProductValidatorPlugin]);
    vi.mocked(productsApi.validateProduct).mockReturnValue(new Promise(() => {}));
    const { ProductDetailPage } = await import('../pages/ProductDetailPage');
    renderWithProviders(<ProductDetailPage />, '/products/1');
    const btn = await screen.findByRole('button', { name: /validate/i });
    await userEvent.click(btn);
    expect(screen.getByText(/validating/i)).toBeInTheDocument();
  });
  ```
  > Add `import userEvent from '@testing-library/user-event';` at the top of the file if not already present.
- [ ] 5.8 **Scenario 4 — all HIGH valid**: `validateProduct` resolves with `mockValidationResult` → three green "Valid" badges:
  ```typescript
  it('shows three green Valid badges after successful HIGH confidence validation', async () => {
    vi.mocked(pluginsApi.getPlugins).mockResolvedValue([mockProductValidatorPlugin]);
    vi.mocked(productsApi.validateProduct).mockResolvedValue(mockValidationResult);
    const { ProductDetailPage } = await import('../pages/ProductDetailPage');
    renderWithProviders(<ProductDetailPage />, '/products/1');
    await userEvent.click(await screen.findByRole('button', { name: /validate/i }));
    expect(await screen.findAllByText('Valid')).toHaveLength(3);
  });
  ```
- [ ] 5.9 **Scenario 5 — one field invalid**: override `categoryValidation` to `valid: false` → one "Invalid" badge, two "Valid" badges:
  ```typescript
  it('shows red Invalid badge when a field has valid: false', async () => {
    vi.mocked(pluginsApi.getPlugins).mockResolvedValue([mockProductValidatorPlugin]);
    vi.mocked(productsApi.validateProduct).mockResolvedValue({
      ...mockValidationResult,
      categoryValidation: { valid: false, confidence: 'HIGH', suggestion: 'Wrong category', explanation: 'Does not match' },
    });
    const { ProductDetailPage } = await import('../pages/ProductDetailPage');
    renderWithProviders(<ProductDetailPage />, '/products/1');
    await userEvent.click(await screen.findByRole('button', { name: /validate/i }));
    expect(await screen.findByText('Invalid')).toBeInTheDocument();
  });
  ```
- [ ] 5.10 **Scenario 6 — MEDIUM confidence**: override one field with `confidence: 'MEDIUM'` → badge renders (color tested via `colorScheme` attribute check or by verifying the badge is present; exact colour asserted via `data-colorscheme` or prose — use `getByText('Valid')` and confirm badge element):
  ```typescript
  it('renders Valid badge for MEDIUM confidence result', async () => {
    vi.mocked(pluginsApi.getPlugins).mockResolvedValue([mockProductValidatorPlugin]);
    vi.mocked(productsApi.validateProduct).mockResolvedValue({
      ...mockValidationResult,
      priceValidation: { valid: true, confidence: 'MEDIUM', suggestion: '', explanation: 'Price is acceptable' },
    });
    const { ProductDetailPage } = await import('../pages/ProductDetailPage');
    renderWithProviders(<ProductDetailPage />, '/products/1');
    await userEvent.click(await screen.findByRole('button', { name: /validate/i }));
    expect(await screen.findAllByText('Valid')).toHaveLength(3);
  });
  ```
- [ ] 5.11 **Scenario 7 — LLM unavailable**: override one field with `suggestion === "LLM unavailable"` → "Unavailable" badge present:
  ```typescript
  it('shows grey Unavailable badge when LLM is unavailable', async () => {
    vi.mocked(pluginsApi.getPlugins).mockResolvedValue([mockProductValidatorPlugin]);
    vi.mocked(productsApi.validateProduct).mockResolvedValue({
      ...mockValidationResult,
      categoryValidation: { valid: false, confidence: 'LOW', suggestion: 'LLM unavailable', explanation: 'Service offline' },
    });
    const { ProductDetailPage } = await import('../pages/ProductDetailPage');
    renderWithProviders(<ProductDetailPage />, '/products/1');
    await userEvent.click(await screen.findByRole('button', { name: /validate/i }));
    expect(await screen.findByText('Unavailable')).toBeInTheDocument();
  });
  ```
- [ ] 5.12 **Scenario 8 — API error**: `validateProduct` rejects → no badges, button re-enabled:
  ```typescript
  it('shows no badges and re-enables button when API call fails', async () => {
    vi.mocked(pluginsApi.getPlugins).mockResolvedValue([mockProductValidatorPlugin]);
    vi.mocked(productsApi.validateProduct).mockRejectedValue(new Error('Network error'));
    const { ProductDetailPage } = await import('../pages/ProductDetailPage');
    renderWithProviders(<ProductDetailPage />, '/products/1');
    const btn = await screen.findByRole('button', { name: /validate/i });
    await userEvent.click(btn);
    expect(screen.queryByText('Valid')).not.toBeInTheDocument();
    expect(screen.queryByText('Invalid')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /validate/i })).not.toBeDisabled();
  });
  ```
- [ ] 5.13 **Scenario 9 — no description**: use `mockProducts[1]` (which has `description: null`) — verify no description badge after validation:
  ```typescript
  it('does not render description badge when product has no description', async () => {
    vi.mocked(pluginsApi.getPlugins).mockResolvedValue([mockProductValidatorPlugin]);
    vi.mocked(productsApi.getProduct).mockResolvedValue({ ...mockProducts[0]!, description: null });
    vi.mocked(productsApi.validateProduct).mockResolvedValue(mockValidationResult);
    const { ProductDetailPage } = await import('../pages/ProductDetailPage');
    renderWithProviders(<ProductDetailPage />, '/products/1');
    await userEvent.click(await screen.findByRole('button', { name: /validate/i }));
    // 2 badges: category + price; no description badge
    expect(await screen.findAllByText('Valid')).toHaveLength(2);
  });
  ```
- [ ] 5.14 Run only the new `describe` block to confirm all 9 scenarios pass:
  ```bash
  npx vitest run --reporter=verbose src/test/pages.test.tsx
  ```
- [ ] 5.15 Run the full test suite to confirm no regressions:
  ```bash
  npm test
  ```

---

## Execution Order & Dependencies

```
Group 1 (API types + validateProduct)
  └─► Group 2 (isPluginEnabled — independent, can run in parallel with Group 1)
        └─► Group 3 (hook — depends on Group 1 for validateProduct import)
              └─► Group 4 (UI — depends on Groups 1, 2, 3)
                    └─► Group 5 (tests — write failing tests after Group 4 step 4.3,
                                  then implement 4.4–4.10 to make them pass)
```

Groups 1 and 2 may be implemented in parallel. Group 3 requires Group 1 to compile. Group 4 requires Groups 1, 2, and 3. Group 5 test stubs can be written after Group 4 step 4.3 (imports/hook wiring) to drive TDD for the remaining Group 4 steps.
