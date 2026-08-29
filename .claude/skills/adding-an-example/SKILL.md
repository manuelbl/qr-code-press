---
name: adding-an-example
description: How to add a new example project under examples/. Use when creating a new example for QR Code Press, or when wiring an example's pom.xml against the library.
---

# Adding an example

Each example is its own Maven project under `examples/`, with its own `pom.xml` and Maven wrapper.
It resolves the library **by version property against the installed artifact**, not from a reactor:

```xml
<properties>
    <qr-code-press.version>0.9.0-SNAPSHOT</qr-code-press.version>
</properties>
```

So an example is built after `./mvnw install` in `qr-code-press/`. This keeps every example a
copy-pasteable starting point that exercises the published artifact exactly as a real user would.

Nothing has to be registered anywhere for that to work, but that is the perspective of someone
*using* the library. There is no aggregator pom and no module list, so no Maven project has to
learn about a new example. It does not apply to CI.

## Register the example in CI

`.github/workflows/build.yml` compiles and runs every example, from an explicit list of steps.
Add one for the new example, after the existing example steps and before `Upload test reports`:

```yaml
      - name: "Run example: <directory>"
        working-directory: ./examples/<directory>
        run: ./mvnw -B --no-transfer-progress compile exec:java
```

The list is explicit rather than discovered, so an example that is not added here is never built
by CI. Two things follow for the example itself:

- its `pom.xml` sets `exec.mainClass` and declares `exec-maven-plugin`, so `./mvnw compile
  exec:java` is the whole invocation, the same command its README documents;
- `main` runs to completion with no arguments and no input, and exits non-zero on failure. Exiting
  zero is all CI asserts; whatever the example renders is not compared against anything.
