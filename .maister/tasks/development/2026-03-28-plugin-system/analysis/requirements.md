# Requirements

## Initial Description
Implement the plugin system as described in plugin-system-design.md. Iframe-isolated web apps communicating via postMessage RPC, with REST APIs for plugin registry, product plugin data, and custom objects. Frontend iframe hosting, message handling, extension point rendering, and plugin SDK.

## Q&A

### User Journey
- Admins upload manifests via `PUT /api/plugins/{pluginId}/manifest`
- Plugin menu items appear in sidebar automatically
- Product detail tabs and list filters render automatically for enabled plugins
- No plugin management UI for now

### Demo Plugin
- Include a demo Warehouse plugin (minimal standalone React app at localhost:3001)
- Demonstrates menu.main, product.detail.tabs, and product.list.filters extension points
- Validates end-to-end: manifest upload → sidebar item → product detail tab → product list filter

### Visual Reference
- Follow existing Chakra UI patterns (match ProductListPage, CategoryFormPage style)
- No external mockups or wireframes

## Scope Decisions (from Phase 2)
1. **ProductDetailPage**: Create new read-only view with plugin tabs
2. **PluginDescriptor PK**: String PK (pluginId) — no BaseEntity inheritance
3. **JSONB filtering**: Server-side via JPA Specification
4. **SDK build**: Multi-entry in main vite.config.ts
5. **Old plugin files**: Delete and recreate

## Functional Requirements

### Backend
1. Plugin manifest upload (PUT) creating/updating plugin registry entries
2. Plugin registry CRUD (GET list, GET single, DELETE)
3. Product plugin data CRUD (GET/PUT/DELETE per plugin per product)
4. Plugin objects CRUD (GET list, GET single, PUT, DELETE per plugin per type)
5. Product entity gets pluginData JSONB column
6. Server-side JSONB filtering on product list endpoint
7. 3 Liquibase migrations (plugins table, pluginData column, plugin_objects table)
8. Validate pluginId exists and is enabled on all plugin-scoped endpoints

### Frontend
1. PluginFrame component (iframe with sandbox, context via window.name)
2. PluginMessageHandler (postMessage router matching event.source to known iframes)
3. Dynamic sidebar menu items from menu.main extension point
4. ProductDetailPage (new) with plugin tabs from product.detail.tabs
5. Product list filter iframes from product.list.filters
6. Plugin iframe registry (Map<iframe, plugin>)
7. Route for full-page plugin views (/plugins/:pluginId/*)

### Plugin SDK
1. TypeScript source in frontend project
2. Built as IIFE via Vite library mode, served at /assets/plugin-sdk.js
3. hostApp facade: getProducts, getProduct, getPlugins, fetch proxy
4. thisPlugin facade: getData, setData, removeData, objects.list/get/save/delete
5. Context parsing from window.name
6. Request/response correlation with "aj.plugin." prefix + UUID

### Demo Warehouse Plugin
1. Standalone React app (separate from host)
2. Registers via manifest with menu.main, product.detail.tabs, product.list.filters
3. Shows warehouse stock info on product detail tab
4. Provides "In Stock" filter on product list

## Reusability Opportunities
- BaseEntity pattern for PluginObject entity
- ProductController/Service pattern for all new controllers
- api/client.ts fetch wrapper for new frontend API modules
- useProducts hook pattern for usePlugins hook
- GlobalExceptionHandler already handles EntityNotFoundException

## Scope Boundaries
- **In scope**: All backend APIs, frontend hosting, SDK, demo plugin, extension points
- **Out of scope**: Authentication, plugin-to-plugin communication, server-side plugin code, marketplace, custom object schema validation
