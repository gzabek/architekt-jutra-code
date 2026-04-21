# Spec Audit: Product Validator Frontend Integration

**Verdict: PASS**

Audited spec: `implementation/spec.md`  
Auditor: maister-spec-auditor  
Date: 2026-04-20

---

## Executive Summary

The specification is complete, internally consistent, and implementation-ready. All five key constraints are satisfied. The spec precisely defines the plugin guard, badge colour logic, graceful degradation scenarios, TypeScript interfaces, and test coverage. No blocking issues were found. Two minor observations are noted below for awareness but do not require spec changes.

---

## Constraint Verification

### 1. Graceful Degradation

**Result: PASS**

Section 4.5 provides an explicit degradation table covering every required scenario:

| Scenario | Specified? |
|---|---|
| Plugin absent or disabled | Yes — button and badges hidden (FR-1) |
| API call in flight | Yes — button disabled with spinner (FR-4) |
| API call succeeds | Yes — badges appear, button re-enabled (FR-6) |
| API call fails (any error) | Yes — button re-enabled, prior badges preserved (FR-10) |
| LLM unavailable (`suggestion === "LLM unavailable"`) | Yes — grey "Unavailable" badge (FR-8) |

FR-10 explicitly states that `validationResult` is not cleared on error, which preserves prior badges. The hook spec (section 4.3) reinforces this: "is NOT cleared on error (preserves last good result per FR-10)."

The only edge not explicitly called out is a 401/403 response, but the spec intentionally treats all API errors identically ("network error, 4xx, 5xx"), which covers it.

### 2. TypeScript Interfaces vs. Backend Contract

**Result: PASS**

Section 4.1 specifies the four exported types: `ValidationConfidence`, `CheckResult`, `ValidationResult`, and `validateProduct`. The field names match the documented backend Java types (`categoryValidation`, `descriptionValidation`, `priceValidation`, all `CheckResult`; `productId: number`). The sentinel value `"LLM unavailable"` originates from `CheckResult.unavailable()` on the backend — correctly propagated in FR-8 and the `badgeColorScheme` implementation.

One minor observation: the `validateProduct` function uses `api.post(…, {})` with an empty body. Whether the backend `POST /api/products/{productId}/validate` expects an empty body or no body is not documented, but this is a trivial implementation detail, not a spec gap.

### 3. Test Coverage vs. Acceptance Criteria

**Result: PASS**

Section 6 lists 9 test scenarios. Mapping to acceptance criteria:

| AC | Test Scenario | Covered? |
|---|---|---|
| AC-1 (plugin disabled) | Scenario 1 | Yes |
| AC-2 (loading state) | Scenario 3 | Yes |
| AC-3 (all HIGH valid → 3 green badges) | Scenario 4 | Yes |
| AC-4 (invalid → red) | Scenario 5 | Yes |
| AC-5 (MEDIUM/LOW → orange) | Scenario 6 | Yes |
| AC-6 (LLM unavailable → grey) | Scenario 7 | Yes |
| AC-7 (tooltip shows explanation) | Not listed as a separate scenario | Minor gap |
| AC-8 (API error → no new badges) | Scenario 8 | Yes |
| AC-9 (initial state, no badges) | Scenario 2 | Yes |
| AC-10 (no description → no badge) | Scenario 9 | Yes |

**Minor observation**: AC-7 (tooltip content) does not have a dedicated test scenario. It is possible to verify tooltip text within scenario 4 (all HIGH valid), but it is not explicitly called out. This is a low-risk gap — tooltip behaviour is purely presentational and easy to verify incidentally — but could be added to scenario 4 for completeness.

### 4. Badge Colour-Coding Logic

**Result: PASS**

The logic is specified in three consistent, mutually reinforcing places:

- FR-7 table (functional requirements)
- Section 4.4.3 `badgeColorScheme` implementation code
- Section 4.5 degradation table

The evaluation order in `badgeColorScheme` is unambiguous:
1. LLM unavailable sentinel → `gray`
2. `valid === false` → `red`
3. `confidence === 'HIGH'` → `green`
4. Fallthrough → `orange` (covers MEDIUM and LOW, both valid)

This matches the FR-7 table exactly. There is no ambiguous overlap between cases.

### 5. Plugin Guard Logic

**Result: PASS**

The guard is fully specified:

- FR-1: Button and badges must NOT render when `product-validator` is absent or `enabled: false`.
- FR-2: Guard is reactive — evaluated from live plugin list, so it responds to late-loading plugin data.
- Section 4.2 defines `isPluginEnabled` using `plugins.some((p) => p.id === pluginId && p.enabled)` — absent plugin returns `false` (array `.some` on an empty or non-matching list).
- Section 4.4.2 shows the concrete usage: `const validatorEnabled = isPluginEnabled('product-validator')`.
- Section 4.4.5 shows the JSX guard: `{validatorEnabled && (…)}`.
- AC-1 and test scenario 1 confirm observable behaviour when plugin is disabled/absent.

The "absent" case (plugin not in list at all) is correctly handled by `plugins.some` returning `false` — this is explicit in the implementation code.

---

## Additional Observations (Non-Blocking)

1. **Tooltip test coverage** (see constraint 3 above): AC-7 lacks a dedicated test scenario. Low risk but worth adding to one of the existing scenarios.

2. **Description badge conditional (FR-6 vs FR-9)**: FR-6 states the description badge is rendered "only when description is present." The implementation snippet (section 4.4.4) shows `{validationResult && <ValidationBadge result={validationResult.descriptionValidation} />}` without a `product.description` guard. The spec prose in FR-6 says the badge only appears when description is present, and AC-10 confirms it. Implementors should ensure the description field row itself is conditionally rendered (which hides the badge implicitly) OR add an explicit guard on `product.description` in the badge JSX. This is a minor implementation clarity note, not a spec defect — the intent is clear from FR-6 and AC-10.

---

## Overall Verdict

**PASS** — The specification is complete and implementation-ready. The two observations are minor and do not block implementation. The badge logic, plugin guard, graceful degradation, TypeScript contracts, and test mapping are all precise and internally consistent.
