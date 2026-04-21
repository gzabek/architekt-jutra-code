# How to Write a Backend Plugin in the aj Application

**Research Question**: How do you write a backend plugin in the aj application?
**Date**: 2026-04-19
**Confidence**: High

---

## 1. Executive Summary

The aj application uses a **REST-based, out-of-process plugin architecture**. Plugins are not Java libraries compiled into the monolith — they are **separate applications** (typically Next.js) that register themselves with aj by calling a single HTTP endpoint with a `manifest.json` file. The host exposes a stable REST API for three purposes: (1) reading/writing namespaced JSONB data on existing Product entities, (2) managing plugin-owned objects in a shared `plugin_objects` table, and (3) declaring UI extension points (iframe slots) via the manifest. The only way a plugin adds Java code to the monolith is if it needs entirely new domain entities in the host database — in that case, a new domain package under `pl.devstyle.aj.<domain>/` is added directly to the monolith source tree and Spring auto-discovers it. All other plugin logic lives outside the monolith.

---

## 2. Architecture Overview

### Track A: Frontend-only Plugin (No Custom Server)

```
Browser
  └── Host UI (React)
        └── <iframe src="http://localhost:3001/some-path" />
              └── Plugin App (React/Vite)
                    └── thisPlugin SDK
                          ├── thisPlugin.getData(productId)       → GET  /api/plugins/{id}/products/{pid}/data
                          ├── thisPlugin.setData(productId, data) → PUT  /api/plugins/{id}/products/{pid}/data
                          ├── thisPlugin.objects.save(...)        → PUT  /api/plugins/{id}/objects/{type}/{oid}
                          └── thisPlugin.objects.list(...)        → GET  /api/plugins/{id}/objects/{type}
```

Best for: UI enrichment, per-product metadata, simple CRUD on plugin-owned objects. No server needed. The `box-size` plugin is the reference example.

### Track B: Plugin with a Custom Backend Server (Full Stack)

```
Browser
  └── Host UI (React)
        └── <iframe src="http://localhost:3001/some-path" />
              └── Plugin App (React/Next.js)
                    ├── Pages (React) — uses thisPlugin SDK
                    └── API Routes (Next.js server)
                          └── server-sdk.ts
                                └── aj REST API  (/api/plugins/..., /api/products/..., etc.)
```

Best for: AI features, external integrations (third-party APIs, file processing), business logic that must not run in the browser. The `warehouse` plugin is the reference example.

### Track C: New Domain in the aj Monolith (Java)

```
src/main/java/pl/devstyle/aj/
  └── <yourdomain>/
        ├── YourDomain.java              (JPA Entity)
        ├── YourDomainController.java    (@RestController)
        ├── YourDomainService.java       (@Service)
        ├── YourDomainRepository.java    (JpaRepository)
        ├── CreateYourDomainRequest.java (record + validation)
        ├── UpdateYourDomainRequest.java (record + validation)
        └── YourDomainResponse.java      (record with from() factory)
```

Best for: when a plugin requires first-class relational entities in the host database — not just JSONB blobs. Spring Boot auto-discovers everything under `pl.devstyle.aj` due to `@SpringBootApplication` on `AjApplication`.

---

## 3. Step-by-Step: Creating a Frontend Plugin

This uses the `warehouse` plugin as template (Track A or B starting point).

### Step 1: Copy the Template

```bash
cp -r plugins/warehouse plugins/my-plugin
cd plugins/my-plugin
npm install
```

### Step 2: Write the Manifest

Edit `manifest.json`. The `id` must match `^[a-zA-Z0-9_-]+$`:

```json
{
  "id": "my-plugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "url": "http://localhost:3001",
  "description": "What my plugin does",
  "extensionPoints": [
    {
      "type": "menu.main",
      "label": "My Plugin",
      "icon": "Box",
      "path": "/",
      "priority": 10
    },
    {
      "type": "product.detail.tabs",
      "label": "My Tab",
      "path": "/product-tab",
      "priority": 5
    }
  ]
}
```

### Step 3: Update Routes

In `src/main.tsx`, declare routes matching the `path` values in your extension points:

```tsx
import { createBrowserRouter } from 'react-router-dom'
import MyMainPage from './pages/MyMainPage'
import MyProductTab from './pages/MyProductTab'

const router = createBrowserRouter([
  { path: '/', element: <MyMainPage /> },
  { path: '/product-tab', element: <MyProductTab /> },
])
```

### Step 4: Use the Plugin SDK

In any React component:

```tsx
import { usePlugin } from '@aj/plugin-sdk'

function MyProductTab({ productId }: { productId: string }) {
  const { thisPlugin } = usePlugin()

  // Read/write plugin-scoped data on a product
  const data = await thisPlugin.getData(productId)
  await thisPlugin.setData(productId, { notes: 'hello' })

  // Manage plugin objects
  const items = await thisPlugin.objects.list('item')
  await thisPlugin.objects.save('item', 'item-001', { name: 'Widget', qty: 5 })
  await thisPlugin.objects.delete('item', 'item-001')
}
```

### Step 5: Register the Plugin with aj

```bash
curl -X PUT http://localhost:8080/api/plugins/my-plugin/manifest \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin-jwt>" \
  -d @manifest.json
```

This requires `PERMISSION_PLUGIN_MANAGEMENT` (admin role).

### Step 6: Start the Dev Server

```bash
npm run dev
# Plugin served at http://localhost:3001
```

The host app will now render your plugin in the declared extension point slots.

---

## 4. Step-by-Step: Creating a Plugin with a Backend Server (Next.js)

### Step 1–3: Same as Frontend Plugin

Follow steps 1–3 above. If using Next.js, your project structure will have both `pages/` and `pages/api/`.

### Step 2: Use server-sdk.ts in API Routes

The `server-sdk.ts` utility (located in `plugins/` directory) extracts the user's JWT from the incoming request and forwards it to aj API calls:

```typescript
// pages/api/my-endpoint.ts
import { createServerSDK } from '../../server-sdk'

export default async function handler(req, res) {
  const sdk = createServerSDK('my-plugin', undefined, req)

  // Now make authenticated calls to aj on behalf of the user
  const token = await sdk.hostApp.getToken()

  // Call aj REST API directly
  const response = await fetch(
    `http://localhost:8080/api/plugins/my-plugin/objects/item`,
    {
      headers: { Authorization: `Bearer ${token}` }
    }
  )
  const items = await response.json()

  // Call external third-party API (browser can't do this due to CORS/secrets)
  const externalData = await fetch('https://api.example.com/data', {
    headers: { 'X-API-Key': process.env.EXTERNAL_API_KEY }
  })

  res.json({ items, externalData: await externalData.json() })
}
```

### Step 3: Call Your API Route from the Frontend

```tsx
// In your React page
const result = await fetch('/api/my-endpoint', {
  headers: { Authorization: `Bearer ${await thisPlugin.sdk.hostApp.getToken()}` }
})
```

### Step 4: Register and Run

Same as steps 5–6 of the frontend plugin flow.

---

## 5. Step-by-Step: Adding a New Domain to aj (Java)

Use this when a plugin needs new relational entities in the host database — not just JSONB blobs.

### Step 1: Create the Database Migration

File: `src/main/resources/db/changelog/2026/NNN-create-my-domain.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: NNN-create-my-domain
      author: you
      changes:
        - createSequence:
            sequenceName: my_domain_seq
            startValue: 1
            incrementBy: 1
        - createTable:
            tableName: my_domains
            columns:
              - column:
                  name: id
                  type: BIGINT
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: name
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: TIMESTAMP
              - column:
                  name: updated_at
                  type: TIMESTAMP
      rollback:
        - dropTable:
            tableName: my_domains
        - dropSequence:
            sequenceName: my_domain_seq
```

### Step 2: Create the JPA Entity

`src/main/java/pl/devstyle/aj/mydomain/MyDomain.java`:

```java
package pl.devstyle.aj.mydomain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.devstyle.aj.core.BaseEntity;

@Entity
@Table(name = "my_domains")
@SequenceGenerator(name = "base_seq", sequenceName = "my_domain_seq", allocationSize = 1)
@Getter
@Setter
@NoArgsConstructor
public class MyDomain extends BaseEntity {

    @Column(nullable = false)
    private String name;

    // Business-key equals/hashCode (never use @Data on JPA entities)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MyDomain other)) return false;
        return name != null && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name == null ? 0 : name.hashCode();
    }
}
```

**Rules**:
- Extend `BaseEntity` — provides `id` (Long, SEQUENCE), `createdAt`, `updatedAt`
- Override `sequenceName` with `@SequenceGenerator` at class level
- Never `@Data` or `@EqualsAndHashCode` — implement based on business key
- All relationships `fetch = FetchType.LAZY`
- `@Enumerated(EnumType.STRING)` always

### Step 3: Create the Repository

```java
package pl.devstyle.aj.mydomain;

import org.springframework.data.jpa.repository.JpaRepository;

interface MyDomainRepository extends JpaRepository<MyDomain, Long> {
    // Spring Data generates: findAll, findById, save, deleteById, existsById
}
```

### Step 4: Create Request/Response Records

```java
package pl.devstyle.aj.mydomain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateMyDomainRequest(
    @NotBlank @Size(max = 255) String name
) {}

record UpdateMyDomainRequest(
    @NotBlank @Size(max = 255) String name
) {}

record MyDomainResponse(Long id, String name) {
    static MyDomainResponse from(MyDomain entity) {
        return new MyDomainResponse(entity.getId(), entity.getName());
    }
}
```

### Step 5: Create the Service

```java
package pl.devstyle.aj.mydomain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.core.error.EntityNotFoundException;

@Service
@RequiredArgsConstructor
class MyDomainService {

    private final MyDomainRepository repository;

    @Transactional
    MyDomainResponse create(CreateMyDomainRequest request) {
        var entity = new MyDomain();
        entity.setName(request.name());
        return MyDomainResponse.from(repository.save(entity));
    }

    MyDomainResponse findById(Long id) {
        return repository.findById(id)
            .map(MyDomainResponse::from)
            .orElseThrow(() -> new EntityNotFoundException("MyDomain", id));
    }

    List<MyDomainResponse> findAll() {
        return repository.findAll().stream()
            .map(MyDomainResponse::from)
            .toList();
    }

    @Transactional
    MyDomainResponse update(Long id, UpdateMyDomainRequest request) {
        var entity = repository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("MyDomain", id));
        entity.setName(request.name());
        return MyDomainResponse.from(entity);
    }

    @Transactional
    void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("MyDomain", id);
        }
        repository.deleteById(id);
    }
}
```

**Error handling rules**:
- Throw `EntityNotFoundException` (not `RuntimeException`) for missing entities
- `GlobalExceptionHandler` in `core/error/` translates this to `404`
- Use `@Valid` on controller request bodies

### Step 6: Create the Controller

```java
package pl.devstyle.aj.mydomain;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/my-domains")
@RequiredArgsConstructor
class MyDomainController {

    private final MyDomainService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MyDomainResponse create(@Valid @RequestBody CreateMyDomainRequest request) {
        return service.create(request);
    }

    @GetMapping
    List<MyDomainResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    MyDomainResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    MyDomainResponse update(@PathVariable Long id,
                             @Valid @RequestBody UpdateMyDomainRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
```

**Spring auto-discovers everything** — no registration needed. `@SpringBootApplication` on `AjApplication` scans the entire `pl.devstyle.aj` package tree.

### Step 7: Add Integration Tests

File: `src/test/java/pl/devstyle/aj/mydomain/MyDomainIntegrationTests.java`

```java
package pl.devstyle.aj.mydomain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.devstyle.aj.TestcontainersConfiguration;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class MyDomainIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MyDomainRepository repository;

    @Test
    void createMyDomain_returns201WithResponse() throws Exception {
        var request = new CreateMyDomainRequest("Test Domain");

        mockMvc.perform(post("/api/my-domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(notNullValue()))
            .andExpect(jsonPath("$.name").value("Test Domain"));
    }

    private MyDomain createAndSave(String name) {
        var entity = new MyDomain();
        entity.setName(name);
        return repository.saveAndFlush(entity);
    }
}
```

Test rules:
- Use real PostgreSQL via TestContainers (`TestcontainersConfiguration`)
- `@Transactional` on the class → auto-rollback after each test
- Class visibility: package-private (no `public`)
- `createAndSave*()` helpers per test class, call `saveAndFlush()`

---

## 6. Storage Mechanisms

### When to Use Plugin Data

Plugin Data stores a JSONB blob namespaced by `pluginId` directly on the `products` table:

```
products.plugin_data = {
  "my-plugin": { "notes": "hello", "score": 42 },
  "other-plugin": { ... }
}
```

**Use when**: The data is semantically "data about a product" from the plugin's perspective. Simple key-value. Only one record per product per plugin.

**SDK calls**:
```typescript
// Read
const data = await thisPlugin.getData(productId)          // GET /api/plugins/{id}/products/{pid}/data

// Write (full replace)
await thisPlugin.setData(productId, { notes: 'hello' })   // PUT /api/plugins/{id}/products/{pid}/data

// Delete
await thisPlugin.removeData(productId)                    // DELETE /api/plugins/{id}/products/{pid}/data
```

**Limitations**: No querying, no filtering, no collections. One blob per product.

---

### When to Use Plugin Objects

Plugin Objects are rows in the `plugin_objects` table. Each row has:
- `pluginId` + `objectType` + `objectId` → unique identity
- `data` → arbitrary JSONB payload
- `entityType` + `entityId` → optional binding to a Product or Category

**Use when**: You need collections of objects, querying/filtering, entity-bound records, or objects that exist independently of products.

**SDK calls**:
```typescript
// Save/update a single object
await thisPlugin.objects.save('location', 'loc-A1', { aisle: 1, shelf: 3 })
// PUT /api/plugins/{id}/objects/location/loc-A1

// List all objects of a type
const locations = await thisPlugin.objects.list('location')
// GET /api/plugins/{id}/objects/location

// List objects bound to a specific entity
const productLocations = await thisPlugin.objects.listByEntity('PRODUCT', productId)
// GET /api/plugins/{id}/objects?entityType=PRODUCT&entityId={pid}

// Save with entity binding
await thisPlugin.objects.save('stock', 'sku-001', { qty: 10 }, {
  entityType: 'PRODUCT',
  entityId: productId
})

// JSONB filtering
const lowStock = await thisPlugin.objects.list('stock', {
  filter: 'data.qty:lt:5'
})
// GET /api/plugins/{id}/objects/stock?filter=data.qty:lt:5
```

**Filter syntax**: `{jsonPath}:{operator}` or `{jsonPath}:{operator}:{value}`

| Operator | Meaning | Example |
|----------|---------|---------|
| `eq` | Equals | `data.status:eq:active` |
| `gt` | Greater than | `data.qty:gt:0` |
| `lt` | Less than | `data.qty:lt:10` |
| `exists` | Field exists | `data.notes:exists` |
| `bool` | Boolean true | `data.active:bool` |

---

## 7. Extension Points Reference

Extension points are declared in `manifest.json` under `"extensionPoints"`. The host frontend renders plugin iframes at the declared slots.

### `menu.main` — Sidebar Navigation Item

Renders a full-page iframe in the main content area. Appears in the sidebar navigation.

```json
{
  "type": "menu.main",
  "label": "Warehouse",
  "icon": "Warehouse",
  "path": "/",
  "priority": 10
}
```

- `icon`: Lucide icon name
- `path`: path within the plugin app (relative to plugin `url`)
- `priority`: lower = higher position in sidebar

### `product.detail.tabs` — Product Detail Tab

Renders an iframe as a tab on the product detail page. The host passes `productId` as a query parameter.

```json
{
  "type": "product.detail.tabs",
  "label": "Box Size",
  "path": "/product-tab",
  "priority": 5
}
```

### `product.list.filters` — Product List Filter

Adds a native (non-iframe) filter control to the product list page. The host renders the filter UI itself — no iframe involved.

```json
{
  "type": "product.list.filters",
  "label": "In Stock",
  "filterKey": "warehouse_status",
  "filterType": "select",
  "priority": 3
}
```

- `filterKey`: query parameter key sent to `/api/products`
- `filterType`: filter control type (`select`, `text`, etc.)

### `product.detail.info` — Product Detail Info Section

Renders an inline info section within the product detail page.

```json
{
  "type": "product.detail.info",
  "label": "Warehouse Info",
  "path": "/product-info",
  "priority": 1
}
```

---

## 8. Security and Authentication

### Plugin Registration (Admin Only)

Registering, updating, deleting, and toggling plugins requires `PERMISSION_PLUGIN_MANAGEMENT`. This is an admin-only operation.

```bash
# Register (requires admin JWT)
curl -X PUT http://localhost:8080/api/plugins/my-plugin/manifest \
  -H "Authorization: Bearer <admin-jwt>" \
  -H "Content-Type: application/json" \
  -d @manifest.json

# Toggle enabled/disabled
curl -X PATCH http://localhost:8080/api/plugins/my-plugin/enabled \
  -H "Authorization: Bearer <admin-jwt>" \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}'
```

### Plugin Data/Objects Access

Reading requires `PERMISSION_READ`. Writing requires `PERMISSION_EDIT`. Both are standard user-level permissions.

### JWT Forwarding in Plugin UIs

The host app provides a token to the plugin iframe via the SDK:

```typescript
// In plugin frontend
const token = await thisPlugin.sdk.hostApp.getToken()

// Check user permissions
const payload = JSON.parse(atob(token.split('.')[1]))
const permissions: string[] = payload.permissions ?? []
const canEdit = permissions.includes('EDIT')
```

### JWT Forwarding in Plugin Server Routes (Next.js)

```typescript
// pages/api/my-endpoint.ts
import { createServerSDK } from '../../server-sdk'

export default async function handler(req, res) {
  // server-sdk extracts the Bearer token from req.headers.authorization
  // and forwards it on all outbound calls to aj
  const sdk = createServerSDK('my-plugin', undefined, req)
  const token = await sdk.hostApp.getToken()

  const response = await fetch('http://localhost:8080/api/products', {
    headers: { Authorization: `Bearer ${token}` }
  })
  // The call succeeds if the user has PERMISSION_READ on products
}
```

### Security Gateway in the Monolith

Every plugin data/objects service call checks `findEnabledOrThrow(pluginId)` first. If the plugin is disabled or not registered, all operations return `404`. This prevents orphaned data access after a plugin is unregistered.

---

## 9. Testing

### Testing Plugin REST API (Integration Tests)

The test suite uses TestContainers with real PostgreSQL. All plugin tests are in:

```
src/test/java/pl/devstyle/aj/core/plugin/
├── PluginRegistryIntegrationTests.java      — manifest upload/update/delete/toggle
├── PluginDataAndObjectsIntegrationTests.java — plugin data on products, CRUD objects
├── PluginObjectApiAndFilterTests.java        — entity binding + JSONB filters
├── PluginObjectGapTests.java                 — all filter operators, error cases
├── PluginObjectEntityBindingTests.java       — entity binding isolation
├── PluginGapTests.java                       — product pluginFilter query param
└── PluginDatabaseTests.java                  — JSONB round-trips, unique constraint
```

Use these as canonical usage examples for the plugin REST API.

### Running Plugin Tests

```bash
# Run all plugin tests
./mvnw test -Dtest="PluginRegistryIntegrationTests,PluginDataAndObjectsIntegrationTests,PluginObjectApiAndFilterTests"

# Run a single test class
./mvnw test -Dtest=PluginObjectGapTests

# Run a single test method
./mvnw test -Dtest=PluginObjectGapTests#filterByJsonPath_returns200WithMatchingObjects
```

### Testing a New Domain Package

Follow the test pattern from existing domains. Two test classes per domain:
- `<Domain>IntegrationTests` — CRUD happy paths
- `<Domain>ValidationTests` — error cases, edge cases

Template:

```java
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class MyDomainIntegrationTests {
    // Tests here — @Transactional auto-rolls back each test
    // No manual cleanup needed
    // Use saveAndFlush() for setup data
    // Use mockMvc + jsonPath() for HTTP assertions
    // Use AssertJ only for non-HTTP assertions
}
```

---

## 10. Reference

### Key File Locations

| File | Purpose |
|------|---------|
| `src/main/java/pl/devstyle/aj/core/plugin/` | Plugin framework Java code |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginDescriptor.java` | Plugin registration entity |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginObject.java` | Plugin objects entity |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginDescriptorService.java` | Registration service |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectService.java` | Objects CRUD service |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginDataService.java` | Product data service |
| `src/main/java/pl/devstyle/aj/core/plugin/DbPluginObjectQueryService.java` | jOOQ JSONB filter queries |
| `src/main/java/pl/devstyle/aj/core/BaseEntity.java` | Base entity with id/createdAt/updatedAt |
| `src/main/java/pl/devstyle/aj/AjApplication.java` | Spring Boot entry point (`@SpringBootApplication`) |
| `src/main/resources/db/changelog/` | Liquibase migrations |
| `plugins/warehouse/` | Full-featured reference plugin |
| `plugins/box-size/` | Minimal reference plugin |
| `plugins/CLAUDE.md` | Official plugin developer guide |
| `src/main/frontend/src/plugin-sdk/` | Frontend plugin SDK source |

### API Endpoints Table

| Method | Path | Purpose | Permission |
|--------|------|---------|------------|
| `PUT` | `/api/plugins/{pluginId}/manifest` | Register/update plugin | PLUGIN_MANAGEMENT |
| `GET` | `/api/plugins` | List enabled plugins | READ |
| `GET` | `/api/plugins/{pluginId}` | Get plugin + extension points | READ |
| `DELETE` | `/api/plugins/{pluginId}` | Unregister plugin | PLUGIN_MANAGEMENT |
| `PATCH` | `/api/plugins/{pluginId}/enabled` | Toggle enabled | PLUGIN_MANAGEMENT |
| `GET` | `/api/plugins/{pluginId}/products/{productId}/data` | Read plugin data on product | READ |
| `PUT` | `/api/plugins/{pluginId}/products/{productId}/data` | Write plugin data on product | EDIT |
| `DELETE` | `/api/plugins/{pluginId}/products/{productId}/data` | Remove plugin data on product | EDIT |
| `GET` | `/api/plugins/{pluginId}/objects/{type}` | List objects (with optional filters) | READ |
| `GET` | `/api/plugins/{pluginId}/objects/{type}/{objectId}` | Get single object | READ |
| `PUT` | `/api/plugins/{pluginId}/objects/{type}/{objectId}` | Save/update object | EDIT |
| `DELETE` | `/api/plugins/{pluginId}/objects/{type}/{objectId}` | Delete object | EDIT |

### Useful Commands

```bash
# Start the database
docker compose up -d

# Start aj backend
./mvnw spring-boot:run

# Build without tests (fast)
./mvnw package -DskipTests

# Register a plugin (admin JWT required)
curl -X PUT http://localhost:8080/api/plugins/my-plugin/manifest \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d @manifest.json

# List registered plugins
curl http://localhost:8080/api/plugins \
  -H "Authorization: Bearer <token>"

# Start plugin dev server (from plugin directory)
npm run dev

# Run plugin-related tests
./mvnw test -Dtest=PluginRegistryIntegrationTests
```

### PluginObject Entity Reference

```
plugin_objects table:
  id           BIGINT PK (plugin_object_seq)
  plugin_id    VARCHAR FK → plugins.id
  object_type  VARCHAR (plugin-defined type slug)
  object_id    VARCHAR (plugin-defined key within type)
  data         JSONB
  entity_type  VARCHAR ('PRODUCT' | 'CATEGORY') — nullable
  entity_id    BIGINT — nullable
  created_at   TIMESTAMP
  updated_at   TIMESTAMP
  UNIQUE (plugin_id, object_type, object_id)
```

### PluginDescriptor Entity Reference

```
plugins table:
  id           VARCHAR PK (user-defined slug, ^[a-zA-Z0-9_-]+$)
  name         VARCHAR
  version      VARCHAR
  url          VARCHAR
  description  VARCHAR
  enabled      BOOLEAN DEFAULT true
  manifest     JSONB (full raw manifest)
  created_at   TIMESTAMP
  updated_at   TIMESTAMP
```
