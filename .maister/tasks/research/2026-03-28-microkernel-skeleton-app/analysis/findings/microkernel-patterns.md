# Microkernel Architecture Patterns for Spring Boot Skeleton

## Research Focus
Microkernel architecture in Spring Boot/Java: core interfaces, plugin registry patterns, SPI, extension points, and minimal skeleton design.

---

## 1. Microkernel Architecture Core Concepts

### 1.1 Pattern Definition

The microkernel architecture (also called plug-in architecture) separates a minimal core system from optional plug-in modules that extend behavior. The architecture consists of four elements:

1. **Core System** -- minimal functionality required to make the system operational
2. **Plug-in Modules** -- independent, specialized behavior extensions
3. **Plug-in Interface** -- describes how the core system and plug-ins interact
4. **Registry** -- tracks which plug-ins are available and how to access them

**Source**: [Software Architecture Patterns -- Microkernel Architecture (Priyal Walpita, Medium)](https://priyalwalpita.medium.com/software-architecture-patterns-microkernel-architecture-97cee200264e)

**Confidence**: High (100%) -- well-established architectural pattern documented across multiple authoritative sources.

### 1.2 Core System Responsibilities

The core system should handle only:
- Plugin registration management
- Plugin lifecycle management (start, stop, replace)
- Communication between plugins
- Dynamic plugin replacement

Everything else is delegated to plugins.

**Source**: [What Is Microkernel Architecture Design? (Alibaba Cloud)](https://www.alibabacloud.com/blog/what-is-microkernel-architecture-design_597605)

### 1.3 Plugin Registry

The registry can range from simple to complex:
- **Simple**: Internal `HashMap<String, PluginReference>` mapping plugin names to instances
- **Complex**: External registry/discovery tool with metadata (name, location, data contract, contract format)

The registry is updated when plugins are added or removed.

**Source**: [Microkernel Architecture Design Pattern (DEV Community)](https://dev.to/kishalay_pandey_d5d0cae01f00/microkernel-architecture-design-pattern-n79)

**Evidence** (Java example from the DEV Community article):
```java
Map<String, String> pluginRegistry = new HashMap<>();
static {
    pluginRegistry.put(HEADER, "ValidationHeaderPlugin");
    pluginRegistry.put(SQL, "ValidationSqlPlugin");
}
```

### 1.4 Extension Points and Data Contracts

Extension points define where custom code can be invoked. Data contracts govern information exchange between core and plugins. When third-party modules use incompatible contracts, the Adapter pattern bridges the gap.

**Key design principle**: Plugins should be independent of each other -- no inter-plugin dependencies.

**Confidence**: High (100%) -- consistent across all sources.

---

## 2. Plugin Framework Options for Java/Spring Boot

### 2.1 Approach Comparison

| Approach | Complexity | Runtime Loading | Spring Integration | External Deps | Skeleton Suitability |
|----------|-----------|----------------|-------------------|---------------|---------------------|
| Spring DI + Strategy/Registry | Very Low | No (compile-time) | Native | None | Best |
| Java SPI (ServiceLoader) | Low | Classpath-based | Manual | None | Good |
| Spring Plugin (spring-plugin-core) | Low-Medium | No (compile-time) | Native | 1 library | Good |
| JPMS (module-info.java) | Medium | Module-path | Partial | None | Premature |
| PF4J | Medium-High | Yes (JAR/ZIP) | Via pf4j-spring | 1-2 libraries | Over-engineered |
| OSGi | High | Yes (bundles) | Complex | Many | Over-engineered |

### 2.2 Spring DI + Strategy/Registry Pattern (Recommended for Skeleton)

This approach uses Spring's native dependency injection to implement the plugin pattern with zero additional dependencies. Any Spring `@Component` implementing a plugin interface is automatically discovered and collected.

**How it works**:
1. Define a plugin interface with a `supports(delimiter)` method (or `getPluginId()`)
2. Implement plugins as Spring `@Component` beans
3. Inject `List<PluginInterface>` to collect all implementations automatically
4. Build a registry using `@PostConstruct` to index plugins by ID

**Source**: [Strategy + Registry Pattern in Spring Boot (Medium)](https://medium.com/@venkatsai0398/building-a-dynamic-rules-engine-in-spring-boot-with-the-strategy-registry-pattern-c8bafacc1031)

**Evidence** (core pattern):
```java
public interface Plugin<S> {
    String getPluginId();
    boolean supports(S delimiter);
}

@Component
public class PluginRegistry<T extends Plugin<S>, S> {
    private final Map<String, T> pluginMap = new HashMap<>();

    @Autowired
    private List<T> plugins;

    @PostConstruct
    public void init() {
        for (T plugin : plugins) {
            pluginMap.put(plugin.getPluginId(), plugin);
        }
    }

    public Optional<T> getPlugin(String pluginId) {
        return Optional.ofNullable(pluginMap.get(pluginId));
    }

    public List<T> getPluginsFor(S delimiter) {
        return plugins.stream()
            .filter(p -> p.supports(delimiter))
            .toList();
    }
}
```

**Advantages for skeleton**:
- Zero additional dependencies
- Pure Spring idiom -- aligns with existing project
- Compile-time safety
- Can be evolved to PF4J or JPMS later without changing plugin interfaces
- Satisfies the project's "minimal implementation" standard

**Disadvantages**:
- No runtime plugin loading (plugins must be on classpath at startup)
- No classloader isolation between plugins

**Confidence**: High (95%) -- this is the most pragmatic approach for a skeleton. The pattern is well-documented and widely used.

### 2.3 Java SPI (ServiceLoader)

Java's built-in `ServiceLoader` implements the Service Provider Interface pattern. Providers are discovered via `META-INF/services/` files.

**How it works**:
1. Define a service interface
2. Providers implement the interface
3. Register in `META-INF/services/com.example.MyServiceInterface` (one implementation class name per line)
4. Load at runtime: `ServiceLoader.load(MyServiceInterface.class)`

**Source**: [Java Service Provider Interface (Baeldung)](https://www.baeldung.com/java-spi); [Rediscovering Java ServiceLoader (Frankel)](https://blog.frankel.ch/rediscovering-java-serviceloader/)

**Key characteristics**:
- Built into the JDK since Java 6
- Framework-agnostic -- no Spring dependency in plugin interfaces
- Lazy loading of implementations
- Works with JPMS via `provides...with` directives in module-info.java

**Spring Boot integration concern**: Beans obtained via ServiceLoader do not automatically participate in Spring's lifecycle (no `@Autowired` injection, no `@PostConstruct`, etc.). They must be manually registered in the BeanFactory.

**Source**: [Spring Boot Java Util ServiceLoader Example (GitHub)](https://github.com/pivotal-chicago/spring-boot-java-util-service-loader)

**Skeleton suitability**: Good as a secondary option. Useful if plugins need to be framework-agnostic (e.g., shared between Spring and non-Spring contexts). Adds complexity for little benefit in a pure Spring Boot application.

**Confidence**: High (90%)

### 2.4 Spring Plugin (spring-plugin-core)

An official Spring project providing a lightweight plugin abstraction with `Plugin<S>` interface, `PluginRegistry<T,S>`, and `@EnablePluginRegistries`.

**Maven coordinates**: `org.springframework.plugin:spring-plugin-core:3.0.0`

**Source**: [spring-projects/spring-plugin (GitHub)](https://github.com/spring-projects/spring-plugin)

**Core API**:
```java
public interface Plugin<S> {
    boolean supports(S delimiter);
}

// Usage:
@Configuration
@EnablePluginRegistries(MyPlugin.class)
class AppConfig {}

// Injection:
@Autowired
PluginRegistry<MyPlugin, MyDelimiter> registry;

// Lookup:
Optional<MyPlugin> plugin = registry.getPluginFor(delimiter);
```

**Registry types**:
- `SimplePluginRegistry` -- basic collection and lookup
- `OrderAwarePluginRegistry` -- respects `@Order` and `Ordered` interface

**Advantages**: Official Spring project, minimal footprint, type-safe registry.
**Disadvantages**: Adds a dependency; the custom approach (2.2) achieves the same with zero deps; project has limited recent activity.

**Confidence**: Medium-High (80%) -- good option but adds a dependency for something achievable natively.

### 2.5 PF4J (Plugin Framework for Java)

A lightweight (~100KB) open-source plugin framework providing full plugin lifecycle management with classloader isolation.

**Source**: [PF4J Official Site](https://pf4j.org/); [pf4j-spring (GitHub)](https://github.com/pf4j/pf4j-spring)

**Core concepts**:
- `Plugin` -- base class, loaded in separate classloader
- `ExtensionPoint` -- marker interface for extension points
- `Extension` -- annotation marking an implementation of an extension point
- `PluginManager` -- orchestrates loading, starting, stopping

**Spring integration** via `pf4j-spring`:
```xml
<dependency>
    <groupId>org.pf4j</groupId>
    <artifactId>pf4j-spring</artifactId>
    <version>0.4.0</version>
</dependency>
```

```java
@Configuration
public class PluginConfig {
    @Bean
    public SpringPluginManager pluginManager() {
        return new SpringPluginManager();
    }
}
```

Extensions auto-register as Spring beans via `ExtensionsInjector`.

**Plugin structure** (three artifacts):
1. **Shared interfaces** -- published by application owner
2. **Container** -- main application, scans plugin directory
3. **Plugin** -- packaged as ZIP/JAR, deployed to plugins/ directory

**Source**: [PF4J Spring Tutorial (ralemy)](https://ralemy.github.io/pf4j-spring-tutorial/)

**Skeleton suitability**: Over-engineered for a skeleton. PF4J is the right choice when you need runtime plugin loading, classloader isolation, and hot deployment. The skeleton has none of these requirements yet.

**Confidence**: High (95%) -- PF4J is well-proven but premature for skeleton phase.

### 2.6 JPMS (Java Platform Module System)

Java's native module system (since Java 9) provides strong encapsulation and explicit module dependencies via `module-info.java`.

**Source**: [JPMS GeeksforGeeks](https://www.geeksforgeeks.org/java/jpms-java-platform-module-system/); [How to Use Java Modules with Spring Boot (DZone)](https://dzone.com/articles/how-to-use-java-modules-to-build-a-spring-boot-app)

**Plugin support via `provides...with`**:
```java
module pl.devstyle.aj.core {
    exports pl.devstyle.aj.plugin.api;
    uses pl.devstyle.aj.plugin.api.AjPlugin;
}

module pl.devstyle.aj.myplugin {
    requires pl.devstyle.aj.core;
    provides pl.devstyle.aj.plugin.api.AjPlugin
        with pl.devstyle.aj.myplugin.MyPluginImpl;
}
```

**Spring Boot compatibility concern**: Spring relies heavily on reflection. Modules must use `opens` directives for packages Spring needs to access. This adds friction and configuration overhead.

**Skeleton suitability**: Premature. JPMS adds significant build complexity (multi-module Maven, module-info.java files, opens directives for Spring). Worth considering when the project has multiple modules and needs strong encapsulation boundaries. Java 25 supports it fully, so it can be adopted later.

**Confidence**: Medium (70%) -- technically sound but high friction with Spring Boot.

---

## 3. Recommended Skeleton Architecture

### 3.1 Approach: Spring DI with Plugin Interfaces

Given the project constraints (skeleton phase, no actual plugins yet, minimal-implementation standard, Java 25 + Spring Boot 4.0.5), the recommended approach is:

**Use Spring's native DI as the plugin framework. Define interfaces now, choose a heavier framework (PF4J, JPMS) later when runtime loading is actually needed.**

This aligns with the project's "No Future Stubs" and "No Speculative Abstractions" standards -- but defining plugin interfaces IS the immediate need since the architecture is explicitly microkernel.

### 3.2 Minimal Core Interfaces

The skeleton needs exactly three interfaces and one registry class:

```java
// 1. Extension Point marker
public interface ExtensionPoint {
    // Marker interface -- all extension point interfaces extend this
}

// 2. Plugin descriptor
public interface PluginDescriptor {
    String getPluginId();
    String getName();
    String getVersion();
}

// 3. Plugin lifecycle (optional for skeleton, but defines the contract)
public interface Plugin extends PluginDescriptor {
    default void onStart() {}
    default void onStop() {}
}

// 4. Plugin Registry (concrete, Spring-managed)
@Component
public class PluginRegistry {
    private final List<Plugin> plugins;

    public PluginRegistry(List<Plugin> plugins) {
        this.plugins = plugins;
    }

    public List<Plugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    public Optional<Plugin> findById(String pluginId) {
        return plugins.stream()
            .filter(p -> p.getPluginId().equals(pluginId))
            .findFirst();
    }

    public <T extends ExtensionPoint> List<T> getExtensions(Class<T> type) {
        // Discover extensions via Spring context or ServiceLoader
    }
}
```

**Confidence**: High (90%) -- this is the minimal viable microkernel structure that satisfies the architecture goals without violating the minimal-implementation standard.

### 3.3 Tension with Minimal-Implementation Standard

The project standard says "No Future Stubs" and "No Speculative Abstractions." However, the project is explicitly described as a "plugin-based microkernel platform" -- the plugin interfaces ARE the core architecture, not speculative abstractions. The distinction:

- **Speculative**: Creating a `NotificationPlugin` interface because you might need notifications someday
- **Architectural**: Creating `Plugin`, `ExtensionPoint`, and `PluginRegistry` because the microkernel pattern requires them as foundational elements

The skeleton should define ONLY the core plugin infrastructure interfaces, not any domain-specific extension points.

**Confidence**: High (90%)

### 3.4 Recommended Package Structure

```
pl.devstyle.aj
+-- AjApplication.java                    (bootstrap)
+-- core/
|   +-- plugin/
|   |   +-- Plugin.java                   (plugin lifecycle interface)
|   |   +-- PluginDescriptor.java         (plugin metadata interface)
|   |   +-- PluginRegistry.java           (registry component)
|   |   +-- ExtensionPoint.java           (extension point marker)
|   +-- config/
|       +-- CoreConfig.java               (Spring configuration)
+-- api/
|   +-- health/
|       +-- HealthController.java         (minimal vertical slice)
```

This structure:
- Keeps plugin infrastructure in `core.plugin` -- clearly the microkernel
- Separates it from domain code (which will live in plugins later)
- Aligns with the planned package structure from `architecture.md`

**Source**: Existing project documentation at `.maister/docs/project/architecture.md` lines 62-71.

**Confidence**: High (85%)

---

## 4. Evolution Path

### Phase 1 (Skeleton -- Current)
- Spring DI-based plugin discovery
- Interfaces: `Plugin`, `ExtensionPoint`, `PluginRegistry`
- Plugins are Spring `@Component` beans on the classpath

### Phase 2 (First Plugins)
- Implement concrete extension points (e.g., `DataImportExtensionPoint`)
- Plugins are still classpath-based Spring components
- Add `@Order` support for plugin priority

### Phase 3 (Plugin Isolation -- If Needed)
- Migrate to PF4J for classloader isolation and runtime loading
- OR migrate to JPMS modules for strong encapsulation
- Plugin interfaces remain unchanged -- only the loading mechanism changes

This evolution path means the skeleton's interfaces won't need to change when the plugin framework is eventually selected.

**Confidence**: Medium-High (80%) -- depends on future requirements.

---

## 5. Comparison Summary

### For the "aj" Skeleton Specifically

| Criterion | Spring DI | Java SPI | Spring Plugin | PF4J | JPMS |
|-----------|-----------|----------|---------------|------|------|
| Zero new dependencies | Yes | Yes | No | No | Yes |
| Spring-native | Yes | No | Yes | Via adapter | Partial |
| Minimal code | ~4 files | ~4 files + META-INF | ~3 files + dep | ~6 files + deps | ~6 files + module-info |
| Runtime loading | No | Classpath | No | Yes | Module-path |
| Classloader isolation | No | No | No | Yes | Yes |
| Evolution to PF4J | Easy | Easy | Easy | N/A | Medium |
| Aligns with minimal-impl standard | Best | Good | Good | No | No |

**Recommendation**: Spring DI + Strategy/Registry pattern for the skeleton. It provides the core microkernel abstractions (Plugin, ExtensionPoint, PluginRegistry) with zero additional dependencies, pure Spring idioms, and a clear evolution path to PF4J or JPMS when runtime loading becomes a real requirement.

---

## Sources

### Architecture Pattern References
- [Microkernel Architecture Design Pattern (DEV Community)](https://dev.to/kishalay_pandey_d5d0cae01f00/microkernel-architecture-design-pattern-n79)
- [Software Architecture Patterns -- Microkernel (Priyal Walpita)](https://priyalwalpita.medium.com/software-architecture-patterns-microkernel-architecture-97cee200264e)
- [What Is Microkernel Architecture Design? (Alibaba Cloud)](https://www.alibabacloud.com/blog/what-is-microkernel-architecture-design_597605)
- [Software Architecture Patterns, Ch. 3 (O'Reilly, Mark Richards)](https://www.oreilly.com/library/view/software-architecture-patterns/9781491971437/ch03.html)

### Framework Documentation
- [PF4J Official Site](https://pf4j.org/)
- [PF4J-Spring Integration (GitHub)](https://github.com/pf4j/pf4j-spring)
- [PF4J Spring Tutorial (ralemy)](https://ralemy.github.io/pf4j-spring-tutorial/)
- [Spring Plugin (GitHub, spring-projects)](https://github.com/spring-projects/spring-plugin)

### Java SPI / ServiceLoader
- [Java Service Provider Interface (Baeldung)](https://www.baeldung.com/java-spi)
- [Rediscovering Java ServiceLoader (Frankel)](https://blog.frankel.ch/rediscovering-java-serviceloader/)
- [Spring Boot Java Util ServiceLoader Example (GitHub)](https://github.com/pivotal-chicago/spring-boot-java-util-service-loader)
- [SpringBoot SPI Plugin Architecture (GitHub)](https://github.com/scandinave/springboot-spi)

### JPMS
- [JPMS Java Platform Module System (GeeksforGeeks)](https://www.geeksforgeeks.org/java/jpms-java-platform-module-system/)
- [How to Use Java Modules with Spring Boot (DZone)](https://dzone.com/articles/how-to-use-java-modules-to-build-a-spring-boot-app)

### Spring Patterns
- [Strategy + Registry Pattern in Spring Boot (Medium)](https://medium.com/@venkatsai0398/building-a-dynamic-rules-engine-in-spring-boot-with-the-strategy-registry-pattern-c8bafacc1031)
- [Strategy Design Pattern with Spring Plugin (Medium)](https://medium.com/javarevisited/the-strategy-design-pattern-with-spring-plugin-e99021c8f6eb)
- [Spring Boot Plugin Development (Epimorphics)](https://www.epimorphics.com/spring-boot-plugin-development/)

### Project Documentation
- `.maister/docs/project/architecture.md` -- current architecture and planned package structure
- `.maister/docs/project/tech-stack.md` -- technology choices and pending decisions
- `.maister/docs/standards/global/minimal-implementation.md` -- minimal implementation standard
