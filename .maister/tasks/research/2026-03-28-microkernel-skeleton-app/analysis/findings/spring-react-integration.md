# Spring Boot + React Frontend Integration: Research Findings

## Research Question
How to integrate a React frontend with a Spring Boot 4.0.5 backend in a monorepo Maven setup, covering project structure, development workflow, production build, and API communication?

## Context
- **Existing project**: `aj` - Maven single-module, Spring Boot 4.0.5, Java 25
- **Current state**: No frontend exists yet. Backend has `spring-boot-starter-webmvc`, JPA, jOOQ, Liquibase, TestContainers
- **Goal**: Skeleton app with working React frontend served by Spring Boot

---

## 1. Monorepo Structure Options

Three approaches emerged from research, ordered by increasing complexity.

### Option A: Single Module with Embedded Frontend Directory (RECOMMENDED)

Place the React app inside `src/main/` alongside Java sources.

```
project-root/
├── src/
│   └── main/
│       ├── java/pl/devstyle/aj/    # Backend
│       ├── frontend/                # React + Vite + TypeScript
│       │   ├── src/
│       │   ├── package.json
│       │   ├── vite.config.ts
│       │   └── tsconfig.json
│       └── resources/
│           ├── static/              # Vite build output (git-ignored)
│           └── application.properties
├── pom.xml
└── .gitignore
```

**Pros**:
- Simplest setup, no multi-module overhead
- Single `pom.xml` to manage
- Frontend build integrated via Maven plugin
- Matches existing project structure (single-module)

**Cons**:
- Frontend and backend share a single Maven lifecycle (cannot build separately via Maven)
- Less suitable if frontend team operates independently

**Confidence**: High (95%) -- This is the most documented pattern for single-team monorepo Spring Boot + React projects.

**Sources**:
- [Bundling React (Vite) with Spring Boot - Jessy](https://www.jessym.com/articles/bundling-react-vite-with-spring-boot) -- Uses `src/main/client/` directory
- [spring-react-vite-template on GitHub](https://github.com/seanpolid/spring-react-vite-template) -- Uses `frontend/` at project root
- [Including React in Spring Boot Maven build - Geoff Bourne](https://medium.com/@itzgeoff/including-react-in-your-spring-boot-maven-build-ae3b8f8826e) -- Uses `src/main/app/`

**Naming convention note**: Various sources use `client/`, `frontend/`, `app/`, `webapp/`. For this project, `src/main/frontend/` is recommended as it is the most self-documenting name.

### Option B: Maven Multi-Module (Two Modules)

```
project-root/
├── pom.xml (parent, packaging=pom)
├── backend/
│   ├── pom.xml
│   └── src/main/java/...
└── frontend/
    ├── pom.xml
    ├── src/
    ├── package.json
    └── vite.config.ts
```

The frontend module builds a JAR containing only static resources. The backend module declares a dependency on the frontend module, pulling in the static files.

**Pros**:
- Clean separation of concerns
- Frontend and backend can be built/tested independently
- Better for larger teams with separate frontend/backend developers
- Frontend module can be reused across multiple backends

**Cons**:
- More complex Maven configuration (parent POM, two child POMs)
- Requires restructuring the existing project (move everything into `backend/` subdirectory)
- Overkill for a skeleton project with a small team

**Confidence**: High (90%) -- Well-documented pattern, but adds unnecessary complexity for this project.

**Sources**:
- [springboot-react-multimodule on GitHub](https://github.com/JohnnyBDude/springboot-react-multimodule)
- [spring-boot-react-maven-starter on GitHub](https://github.com/xebia-os/spring-boot-react-maven-starter)
- [Multi-module projects and Packaging frontend-backend together](https://ashokgurudayal.hashnode.dev/multi-module-projects-and-packaging-frontend-backend-together)

### Option C: Separate Repositories / Independent Build

Frontend and backend live in separate repos or directories with completely independent build pipelines. Communication is purely via REST API. Frontend deployed separately (e.g., CDN, nginx).

**Pros**:
- Maximum independence
- Frontend can use any build system without Maven integration
- Independent deployment

**Cons**:
- Not a monorepo (contradicts requirements)
- CORS complexity in all environments
- Two deployment targets to manage
- Harder to keep in sync for a skeleton project

**Confidence**: High (90%) -- Valid pattern but explicitly out of scope for this task.

### Recommendation

**Option A (Single Module with Embedded Frontend)** is the best fit because:
1. The project is already a single Maven module -- no restructuring needed
2. This is a skeleton app -- simplicity is paramount
3. Single team developing both frontend and backend
4. Aligns with the project's "minimal implementation" standard

---

## 2. Frontend Build Tool: Vite (Not CRA)

### Decision: Use Vite with React + TypeScript

**Create React App (CRA) is deprecated** as of early 2025. The React team officially recommends against using it for new projects. All modern sources recommend Vite as the default React build tool.

**Scaffolding command**:
```bash
npm create vite@latest frontend -- --template react-ts
```

This generates a React 19 + TypeScript + Vite project with:
- `vite.config.ts` -- Build configuration
- `tsconfig.json` -- TypeScript configuration
- `index.html` -- Entry point at root (not in `public/`)
- `src/` -- Application source code

**Key Vite advantages over CRA**:
- 40x faster builds (uses esbuild/SWC for compilation)
- Native ES module serving in development (no bundling during dev)
- Hot Module Replacement (HMR) that works reliably
- Active maintenance and ecosystem support
- First-class TypeScript support

**Confidence**: High (100%) -- Industry consensus. CRA is deprecated, Vite is the standard.

**Sources**:
- [Vite Getting Started](https://vite.dev/guide/)
- [Complete Guide to Setting Up React with TypeScript and Vite (2026)](https://medium.com/@robinviktorsson/complete-guide-to-setting-up-react-with-typescript-and-vite-2025-468f6556aaf2)
- [How to Set Up a Production-Ready React Project with TypeScript and Vite](https://oneuptime.com/blog/post/2026-01-08-react-typescript-vite-production-setup/view)

---

## 3. Maven Build Integration

Two plugins can integrate the frontend build into Maven. Comparison:

### frontend-maven-plugin (RECOMMENDED)

**GitHub**: [eirslett/frontend-maven-plugin](https://github.com/eirslett/frontend-maven-plugin)

Downloads and installs Node.js and npm locally to the project. No global Node installation required. The build machine only needs a JDK + Maven.

```xml
<plugin>
    <groupId>com.github.eirslett</groupId>
    <artifactId>frontend-maven-plugin</artifactId>
    <version>1.15.1</version>
    <configuration>
        <workingDirectory>src/main/frontend</workingDirectory>
        <nodeVersion>v22.14.0</nodeVersion>
    </configuration>
    <executions>
        <execution>
            <id>install-node-and-npm</id>
            <goals>
                <goal>install-node-and-npm</goal>
            </goals>
            <phase>generate-resources</phase>
        </execution>
        <execution>
            <id>npm-install</id>
            <goals>
                <goal>npm</goal>
            </goals>
            <phase>generate-resources</phase>
            <configuration>
                <arguments>install</arguments>
            </configuration>
        </execution>
        <execution>
            <id>npm-build</id>
            <goals>
                <goal>npm</goal>
            </goals>
            <phase>generate-resources</phase>
            <configuration>
                <arguments>run build</arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Pros**:
- Self-contained: downloads Node/npm locally (into `node/` directory in working directory)
- Reproducible builds: pinned Node version in `pom.xml`
- Works on CI without Node pre-installed
- Widely used, well-maintained
- Supports npm, yarn, and pnpm

**Cons**:
- Downloads Node on first build (one-time cost)
- Adds `node/` directory to project (must be git-ignored)

### exec-maven-plugin (Alternative)

Executes arbitrary commands (like `npm install`, `npm run build`). Requires Node.js to be pre-installed on the system.

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.0</version>
    <executions>
        <execution>
            <id>npm-install</id>
            <phase>generate-sources</phase>
            <goals><goal>exec</goal></goals>
            <configuration>
                <workingDirectory>src/main/frontend</workingDirectory>
                <executable>npm</executable>
                <arguments><argument>install</argument></arguments>
            </configuration>
        </execution>
        <execution>
            <id>npm-build</id>
            <phase>generate-sources</phase>
            <goals><goal>exec</goal></goals>
            <configuration>
                <workingDirectory>src/main/frontend</workingDirectory>
                <executable>npm</executable>
                <arguments>
                    <argument>run</argument>
                    <argument>build</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Pros**:
- Lighter -- no Node download
- Simpler configuration
- Uses system Node (which developer likely has already)

**Cons**:
- Requires Node.js pre-installed globally
- Node version not pinned by Maven (could differ between developers/CI)
- Less reproducible

### Recommendation

**frontend-maven-plugin** is recommended for this project because:
1. Reproducible builds with pinned Node version
2. Works on any machine with just JDK + Maven (mvnw)
3. Aligns with project's use of Maven Wrapper for reproducibility
4. Industry standard for Spring Boot + frontend integration

**Confidence**: High (90%) -- frontend-maven-plugin is the dominant choice in Spring Boot + React projects.

**Sources**:
- [frontend-maven-plugin GitHub](https://github.com/eirslett/frontend-maven-plugin)
- [Bundling React (Vite) with Spring Boot](https://www.jessym.com/articles/bundling-react-vite-with-spring-boot) -- Uses exec-maven-plugin
- [Including React in Spring Boot Maven build](https://medium.com/@itzgeoff/including-react-in-your-spring-boot-maven-build-ae3b8f8826e) -- Uses frontend-maven-plugin
- [Run React Frontend and Spring Boot on Same Port](https://dev.to/arpan_banerjee7/run-react-frontend-and-springboot-backend-on-the-same-port-and-package-them-as-a-single-artifact-14pa) -- Uses frontend-maven-plugin

---

## 4. Vite Configuration for Spring Boot Output

### vite.config.ts

Configure Vite to output directly to Spring Boot's static resources directory:

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../resources/static',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

**Key settings**:
- `build.outDir`: Points to `src/main/resources/static/` (relative to `src/main/frontend/`)
- `build.emptyOutDir`: Cleans the output directory before each build
- `server.proxy`: Forwards `/api/*` requests to Spring Boot during development

**Build output**:
```
src/main/resources/static/
├── index.html
├── assets/
│   ├── index-[hash].js
│   └── index-[hash].css
└── vite.svg (or other static assets)
```

Spring Boot automatically serves everything from `classpath:/static/` at the root path `/`.

**Important**: Add `src/main/resources/static/` to `.gitignore` since these are generated files.

**Confidence**: High (95%) -- Consistent across all sources. The `outDir` approach avoids the need for a separate `maven-resources-plugin` copy step.

**Sources**:
- [Bundling React (Vite) with Spring Boot](https://www.jessym.com/articles/bundling-react-vite-with-spring-boot)
- [spring-react-vite-template on GitHub](https://github.com/seanpolid/spring-react-vite-template)
- [Vite Backend Integration Guide](https://vite.dev/guide/backend-integration)

---

## 5. Development Workflow

### Two-Server Development (RECOMMENDED)

During development, run frontend and backend as separate processes:

**Terminal 1 -- Spring Boot backend (port 8080)**:
```bash
./mvnw spring-boot:run
```

**Terminal 2 -- Vite dev server (port 5173)**:
```bash
cd src/main/frontend
npm run dev
```

Developer opens `http://localhost:5173` in the browser. Vite serves the React app with instant HMR. API calls to `/api/*` are proxied to `http://localhost:8080` via Vite's built-in proxy.

**Benefits**:
- Instant React HMR (sub-second feedback)
- Full Spring Boot DevTools support on backend
- No CORS issues (proxy handles it)
- Standard development experience for both frontend and backend developers

### Alternative: Vite Watch Mode + Spring Boot

```bash
# Terminal 1: Vite watches and rebuilds to static/
cd src/main/frontend && npm run watch

# Terminal 2: Spring Boot with DevTools auto-restart
./mvnw spring-boot:run
```

The `watch` script in `package.json`:
```json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "watch": "vite build --watch --emptyOutDir"
  }
}
```

Developer opens `http://localhost:8080`. Spring Boot serves the rebuilt static files. Requires manual browser refresh after frontend changes.

**Pros**: Single URL, no proxy needed
**Cons**: No HMR, requires browser refresh, slower feedback loop

### Recommendation

**Two-server development with Vite proxy** is the recommended approach. It provides the best developer experience with near-instant frontend feedback.

**Confidence**: High (95%) -- Industry standard development workflow.

**Sources**:
- [Vite Dev Server Proxy Configuration](https://vite.dev/guide/backend-integration)
- [Bundling React (Vite) with Spring Boot](https://www.jessym.com/articles/bundling-react-vite-with-spring-boot)
- [spring-react-vite-template on GitHub](https://github.com/seanpolid/spring-react-vite-template)

---

## 6. Production Build

### Single JAR Artifact

Running `./mvnw package` produces a single executable JAR containing both backend and frontend:

1. **generate-resources phase**: frontend-maven-plugin installs Node, runs `npm install`, runs `npm run build`
2. **Vite build**: Outputs optimized assets to `src/main/resources/static/`
3. **compile phase**: Java sources compiled
4. **package phase**: Everything bundled into a single JAR

```bash
# Build
./mvnw clean package

# Run
java -jar target/aj-0.0.1-SNAPSHOT.jar
```

The application serves both the API and the frontend on port 8080.

**Confidence**: High (95%) -- This is the standard approach, well-documented across all sources.

---

## 7. SPA Routing (Forwarding to index.html)

React Router handles client-side routing. When a user navigates to `/dashboard` and refreshes, Spring Boot must serve `index.html` (not a 404).

### Recommended Approach: Controller-Based Forwarding

```java
@Controller
public class SpaForwardController {

    @GetMapping(value = "/{path:[^\\.]*}")
    public String forwardSingle() {
        return "forward:/index.html";
    }

    @GetMapping(value = "/**/{path:[^\\.]*}")
    public String forwardNested() {
        return "forward:/index.html";
    }
}
```

**Pattern explanation**: `[^\\.]*` matches any path segment that does NOT contain a dot. This ensures:
- `/dashboard` -> forwards to `index.html` (React Router handles it)
- `/settings/profile` -> forwards to `index.html`
- `/assets/index-abc123.js` -> served as static file (contains a dot)
- `/api/users` -> handled by `@RestController` (takes precedence over `@Controller`)

**Important**: `@RestController` mappings take precedence over `@Controller` mappings, so `/api/**` endpoints will work correctly without additional exclusion patterns.

### Alternative: WebMvcConfigurer with PathResourceResolver

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = location.createRelative(resourcePath);
                        return resource.exists() && resource.isReadable()
                                ? resource
                                : new ClassPathResource("/static/index.html");
                    }
                });
    }
}
```

This approach tries to serve the requested resource; if it does not exist, it falls back to `index.html`.

### Recommendation

The **controller-based approach** is simpler and more explicit. It is easier to understand and debug. The `WebMvcConfigurer` approach is more flexible but adds unnecessary complexity for a skeleton.

**Confidence**: High (90%) -- Both approaches are well-documented. Controller approach is simpler.

**Sources**:
- [How to Use Spring Boot to Serve Client-Side Routing Apps](https://medium.com/@AlexanderObregon/how-to-use-spring-boot-to-serve-client-side-routing-apps-like-react-or-vue-a460f5f1ee1e)
- [Bundling React (Vite) with Spring Boot](https://www.jessym.com/articles/bundling-react-vite-with-spring-boot)
- [Spring Boot SPA wildcard resource serve (GitHub Gist)](https://gist.github.com/sidola/1d97959eeb2b7e3b8567b79c6f40dd1b)
- [How to Configure Spring Catch-All Route for React SPA](https://www.codegenes.net/blog/spring-catch-all-route-for-index-html/)

---

## 8. API Communication and CORS

### API Prefix Convention

All backend REST endpoints should be under `/api/`:

```java
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
```

This convention:
- Cleanly separates API routes from static file serving
- Simplifies SPA forwarding rules (anything not `/api/**` goes to `index.html`)
- Enables straightforward proxy configuration in Vite (`/api` -> `localhost:8080`)
- Standard convention across Spring Boot + SPA projects

### CORS Configuration

**Development**: Not needed when using Vite proxy (requests appear same-origin)

**Production**: Not needed when frontend is bundled in the same JAR (same origin)

**If needed** (e.g., separate deployment or direct API access):

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
```

Use Spring profiles to restrict CORS to development only:

```java
@Configuration
@Profile("dev")
public class DevCorsConfig implements WebMvcConfigurer {
    // CORS config only active in dev profile
}
```

### Recommendation

For this skeleton project, **CORS configuration is unnecessary** because:
1. Development: Vite proxy handles cross-origin requests
2. Production: Same-origin (single JAR)

If needed later, add it behind a `@Profile("dev")` annotation.

**Confidence**: High (95%)

**Sources**:
- [Handling CORS in Spring Boot React Application](https://www.javaguides.net/2024/05/handling-cors-in-spring-boot-react-js-application.html)
- [Solving CORS Challenges in Spring Boot + React with Nginx](https://medium.com/@sridhar.jammalamadaka/solving-cors-challenges-in-a-spring-boot-react-setup-with-nginx-e9ab50137260)
- [How to Configure CORS in Spring Boot](https://oneuptime.com/blog/post/2025-12-22-configure-cors-spring-boot/view)

---

## 9. Files to Add to .gitignore

```gitignore
# Frontend build output (generated by Vite)
src/main/resources/static/

# Node.js installed by frontend-maven-plugin
src/main/frontend/node/
src/main/frontend/node_modules/
```

**Confidence**: High (100%) -- Generated files should not be committed.

---

## 10. Complete Recommended Structure

```
aj/
├── pom.xml                              # Maven build (with frontend-maven-plugin)
├── mvnw / mvnw.cmd                     # Maven wrapper
├── .gitignore                           # Updated with frontend ignores
├── src/
│   ├── main/
│   │   ├── java/pl/devstyle/aj/
│   │   │   ├── AjApplication.java       # Spring Boot entry point (existing)
│   │   │   ├── config/
│   │   │   │   └── WebConfig.java       # (if needed for SPA routing)
│   │   │   └── api/
│   │   │       └── HealthController.java # /api/health endpoint
│   │   ├── frontend/                    # React + Vite + TypeScript
│   │   │   ├── package.json
│   │   │   ├── vite.config.ts
│   │   │   ├── tsconfig.json
│   │   │   ├── index.html
│   │   │   └── src/
│   │   │       ├── main.tsx
│   │   │       ├── App.tsx
│   │   │       └── App.css
│   │   └── resources/
│   │       ├── static/                  # Vite build output (git-ignored)
│   │       ├── application.properties
│   │       └── db/changelog/            # Liquibase (existing)
│   └── test/
│       └── java/pl/devstyle/aj/        # Tests (existing)
└── .maister/                            # Project docs (existing)
```

---

## Summary of Recommendations

| Decision | Recommendation | Confidence |
|----------|---------------|------------|
| Monorepo structure | Single module, frontend in `src/main/frontend/` | 95% |
| Frontend tooling | Vite + React 19 + TypeScript | 100% |
| Maven integration | `frontend-maven-plugin` | 90% |
| Build output | Vite `outDir` directly to `src/main/resources/static/` | 95% |
| Dev workflow | Two servers: Vite (5173) + Spring Boot (8080) with Vite proxy | 95% |
| Production build | Single JAR via `./mvnw package` | 95% |
| SPA routing | Controller-based `forward:/index.html` | 90% |
| API prefix | `/api/*` for all REST endpoints | 95% |
| CORS | Not needed (proxy in dev, same-origin in prod) | 95% |

## Gaps and Uncertainties

1. **frontend-maven-plugin compatibility with Spring Boot 4.0.5**: No specific Spring Boot 4.x issues found, but the plugin operates independently of Spring Boot version (it only runs npm commands). Low risk.

2. **Node.js version for frontend-maven-plugin**: The latest LTS Node.js version should be used. As of March 2026, Node 22.x LTS is recommended. The exact latest patch version should be verified at build time.

3. **Vite 6.x vs 5.x**: The `npm create vite@latest` command will pull the current version. As of early 2026, Vite 6.x is current. Configuration syntax is stable between 5.x and 6.x for the features used here.

4. **React 19 features**: React 19 is the current stable version. The skeleton does not need to use any React 19-specific features (Server Components, Actions, etc.) since this is a client-side SPA.

5. **Spring Boot 4.0.5 static content changes**: Spring Boot 4.x (based on Spring Framework 7) may have minor changes to static content serving defaults. The `classpath:/static/` convention remains the same based on available documentation.
