# Android Detekt conventions

Custom Detekt rules for Kotlin and Android projects, covering documentation, null safety, control flow, initialization, visibility, default arguments, and app-owned extensions.

## Requirements

- Detekt **1.23.8**. Detekt 2.x is not supported.
- **Java 21** for the Gradle JVM running Detekt. Your Android application's Java target can be configured separately.

## Installation

Add JitPack to your project's `settings.gradle.kts` repositories:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.Stone-Soup-Tech")
            }
        }
    }
}
```

Apply Detekt and add the rules in each module you want to check:

```kotlin
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

dependencies {
    detektPlugins("com.github.Stone-Soup-Tech:custom-detekt-rules:TAG_OR_COMMIT")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt.yml"))
}
```

Replace `TAG_OR_COMMIT` with a tag or commit hash from [Stone-Soup-Tech/custom-detekt-rules](https://github.com/Stone-Soup-Tech/custom-detekt-rules). Use the exact tag name or commit hash. `TAG_OR_COMMIT` is a placeholder, not an available release. If Detekt is already applied, add the dependency and configuration to your existing setup.

JitPack builds the selected GitHub revision and makes its artifacts available to Gradle. Pin a tag or commit for reproducible dependency selection. See [JitPack's usage documentation](https://docs.jitpack.io/intro/).

## Configuration

Copy [config/detekt.yml](config/detekt.yml) to your project's `config/detekt.yml`. The sample enables all rules in the `android-conventions` ruleset. Merge that section into your existing Detekt configuration if you already have one.

Set `AppOwnedExtension.appOwnedPackages` to the package roots owned by your application, including shared and domain modules:

```yaml
android-conventions:
  active: true
  AppOwnedExtension:
    active: true
    appOwnedPackages:
      - 'com.example.app'
      - 'com.example.domain'
```

An empty ownership list disables ownership checks. Set an individual rule's `active` property to `false` to disable it.

## Run checks

Use a variant task with type resolution to run all rules:

```sh
./gradlew :app:detektDebug
```

Replace `app` and `Debug` with your module and variant. The plain `detekt` task runs syntax checks but does not supply the type information needed by `ExhaustiveWhen`, `RedundantDefaultArgument`, `NamedArgumentsForLargeCalls`, or `AppOwnedExtension`. See [Detekt's type-resolution setup](https://detekt.dev/docs/1.23.8/gettingstarted/gradle/#using-type-resolution).

## Rules

| Rule | Behavior | Type resolution |
| --- | --- | --- |
| `BlankLineBeforeDocumentedProperty` | Requires a blank line before data-class constructor property KDoc after the first property. Supports annotations and multiline declarations. | No |
| `KDocLiteralBackticks` | Requires backticks around standalone lowercase `true`, `false`, and `null` in KDoc prose. Skips code spans, fenced code blocks, links, and URLs. | No |
| `NoForceUnwrap` | Reports postfix `!!`. Suggests safe `let` or a local value with an Elvis early return. | No |
| `ExhaustiveWhen` | Requires exhaustive `when` expressions and statements. Compiler-proven exhaustive cases need no `else`. | Yes |
| `NoBlankLinesBetweenWhenBranches` | Disallows empty lines between adjacent branches, including around comments. Empty lines inside branch bodies are allowed. | No |
| `BracesRequired` | Requires braces for multiline `if` and `if-else` conditionals. Single-line conditionals are allowed in any context, including early returns. Allows `else if` chains. | No |
| `WhenBranchBracesRequired` | Requires braces when a `when` branch's arrow and body span multiple physical lines. Multiple conditions may span lines if the arrow and result stay on one line. | No |
| `SingleInitBlock` | Reports each `init` block after the first in a class or object. | No |
| `MemberVisibilityOrder` | Orders properties and functions as public → internal → protected → private. Constructor properties participate; nested classes are checked independently. | No |
| `RedundantDefaultArgument` | Reports explicit arguments equal to source default constants or enum entries, including inherited defaults and annotation constructors. | Yes |
| `NamedArgumentsForLargeCalls` | Requires named arguments when a callee declares at least three parameters and a call explicitly supplies at least three distinct parameters. | Yes |
| `AppOwnedExtension` | Requires a nearby rationale for extension functions and properties on configured app-owned types. | Yes |

Visibility order is shared across properties and functions. Implicit visibility is treated as public. Constructors, init blocks, and nested type declarations do not participate as members in the ordering check.

### Documented constructor properties

```kotlin
data class User(
    /** First property needs no leading blank line. */
    val name: String,

    /** Other properties need a blank line before their KDoc. */
    @Deprecated("Use userId")
    val id: String,
)
```

An undocumented first property still counts as the first property. Ordinary classes, body properties, and undocumented parameters are excluded. Blank lines inside comments do not count as whitespace before a KDoc.

### Conditional braces

Braces are optional when the entire `if` or `if-else` fits on one physical line, regardless of whether it assigns a value, calls a function, or transfers control:

```kotlin
if (!isReady) return
if (enabled) start() else stop()
```

Multiline conditionals require braces around their bodies:

```kotlin
if (!isReady) {
    return
}
```

### When branch braces

`WhenBranchBracesRequired` checks each branch independently, including `else`. Multiple options may share an inline result. If the result starts after the arrow's line or continues across lines, wrap it in braces:

```kotlin
when (fileType) {
    FileType.PNG,
    FileType.JPEG -> "Image"
    FileType.CSV,
    FileType.TSV -> {
        buildTablePreview(file)
    }
    FileType.TEXT -> "Plain text"
}
```

The rule checks physical line breaks, without imposing a maximum line length.

### Default arguments

```kotlin
fun refresh(force: Boolean = false) { /* ... */ }

refresh(force = false) // reported: call refresh()
```

This rule compares compiler constant values, including literals, null, const references, and constant expressions, and also recognizes resolved enum entries. It does not compare arbitrary expressions by spelling: two `Any()` or `loadValue()` calls can create different values or have side effects. Varargs and defaults whose source is unavailable (for example, compiled dependency declarations) are skipped. When removing a positional argument before other arguments, use named arguments for the remaining parameters as needed.

### Named arguments

`NamedArgumentsForLargeCalls` checks both the resolved callee's parameter count and the number of distinct parameters explicitly supplied at the call site. When both counts are at least three, use names for arguments inside parentheses:

```kotlin
fun connect(host: String, port: Int, secure: Boolean = true) { /* ... */ }

connect("example.com", 443) // Allowed: only two parameters supplied.
connect("example.com", 443, false) // Reported.
connect(host = "example.com", port = 443, secure = false) // Allowed.
```

The rule covers functions, methods, extension functions, Kotlin constructors (including `super`/`this` delegation and enum entries), annotation constructors, and custom `invoke` operators. It requires type resolution, so use a variant task such as `detektDebug`.

Omitted defaults do not count. A trailing lambda counts as one supplied parameter but may remain outside parentheses. Multiple vararg elements count as one parameter; expanded elements cannot be named individually and are exempt, while a spread array can be named. Calls without stable parameter names, such as Java methods and function-type invocations, are skipped; parameter-name comments are not required.

### App-owned extensions

Ownership is explicit through `appOwnedPackages`, matched at package boundaries. For example, `com.example.app` includes `com.example.app.models` but excludes `com.example.application`. The default empty list checks no receivers. Receiver types are resolved, including aliases, generics, and nullable types; external receivers are allowed.

Move stable derived behavior into the receiver type. For a dependency-boundary exception, write an adjacent comment or KDoc with a nonempty `Extension rationale:` marker:

```kotlin
// Extension rationale: Android formatting belongs in the UI module, outside the domain module.
fun User.displayName(): String = /* ... */
```

Static analysis cannot judge whether behavior is stable or whether a boundary justification is convincing. This rule enforces declared ownership and a written justification; review must assess the justification. A detached comment separated by a blank line does not justify an extension.

All rules report findings without automatic corrections. In particular, combining init blocks or moving members can change initialization order. Keep initialization steps in their original execution order and extract complex logic into class-level helpers as appropriate. Detekt's usual `@Suppress("RuleName")` mechanism is available for specific exceptions.


### KDoc literal formatting

Use inline code for literal values:

```kotlin
/** Returns `true` when enabled, or `null` when unknown. */
```

`KDocLiteralBackticks` checks standalone lowercase words in KDoc, including tag descriptions. It also flags ordinary prose uses of these words; use the standard rule suppression for exceptions. Regular comments, string literals, longer identifiers, existing code spans (including longer backtick delimiters), fenced code blocks, KDoc links, and link destinations are excluded. Inline Markdown link labels are checked. This rule reports findings without automatic correction.
