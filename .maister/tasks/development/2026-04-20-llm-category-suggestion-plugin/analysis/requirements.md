# Requirements: category-suggester Plugin

## Initial Description
Build a backend plugin for the aj platform that integrates with LLM via LiteLLM and BAML.
The plugin suggests whether a product's assigned category is correct.

## Scope Decisions (from user)
- **Categories to LLM**: Pass all available categories (full list)
- **Evaluation mode**: Always re-evaluate on every call (no caching) — same as ai-description pattern
- **Confidence display**: Green (≥0.7) / Amber (0.5–0.69) / Red (<0.5) badge

## Technical Architecture
- Plugin type: Track B (Next.js with server-side API route)
- Template: follows ai-description plugin exactly
- BAML function: SuggestCategoryCorrectness
- LiteLLM proxy: localhost:4000 (shared with ai-description)
- Storage: plugin_objects (objectType="suggestion", objectId=productId.toString())
- Entity binding: entityType=PRODUCT, entityId=productId

## Functional Requirements
1. Plugin registers with aj as "category-suggester"
2. Tab appears on every product detail page
3. On tab load, frontend calls POST /api/suggest-category with productId
4. API route:
   a. Fetches full product (name, description, sku, category.name, category.description) from host
   b. Fetches all categories (name, description) from host
   c. Calls SuggestCategoryCorrectness BAML function
   d. Returns JSON: { isCorrect, confidence, reasoning, suggestedCategoryName? }
5. Frontend displays:
   - isCorrect: green checkmark or red X
   - confidence badge: green ≥0.7, amber 0.5-0.69, red <0.5
   - reasoning text (1–3 sentences from LLM)
   - If not correct: suggested alternative category name

## BAML Function Signature
Input:
  - productName: string
  - productDescription: string
  - currentCategoryName: string
  - currentCategoryDescription: string
  - availableCategories: CategoryInfo[] (list of {name, description})

Output (CategorySuggestion class):
  - isCorrect: bool
  - confidence: float (0.0–1.0)
  - reasoning: string
  - suggestedCategoryName: string? (optional, only when isCorrect=false)

## Non-Functional Requirements
- Plugin is purely additive — zero changes to existing aj Java code
- Follows ai-description file structure exactly
- No UI beyond the product detail tab (no menu.main entry)
- Error handling: show error message in tab if LLM call fails

## Scope Boundaries
### In scope
- BAML definition file
- LiteLLM client config (reuse ai-description pattern)
- Next.js API route
- React tab component
- manifest.json
- package.json + tsconfig

### Out of scope
- Java/Spring Boot changes
- New Liquibase migrations
- Caching/persisting suggestions to plugin_objects
- Admin dashboard
- Batch evaluation of all products
