# CoronaPoker 24.11 product modules

This is the product Maven reactor. It builds the Swing and GDX executables and
publishes them exclusively to `../target`. The only JAR kept at the repository
root is `../coronaupdater.jar`, because the self-updater requires that location.

## Physical source ownership

- `coronapoker-core` owns game logic, networking, persistence and
  renderer-neutral presentation contracts under
  `coronapoker-core/src/main/java`.
- `coronapoker-swing` owns the classic Swing frontend under
  `coronapoker-swing/src/main/java`.
- `coronapoker-gdx` owns the complete libGDX frontend under
  `coronapoker-gdx/src/main/java`.
- `coronapoker-assets` packages the shared resources from
  `../src/main/resources`; installable MOD packs remain external to the
  official JARs.

Product Java sources exist only in the three source modules. Architecture tests
reject Java sources in the repository-root source tree, duplicate classes,
frontend dependencies in the core and cross-frontend ownership violations.

Shared resources deliberately remain under `../src/main/resources`: they are
product data, not another copy of Java code.

## Build

From the repository root:

```powershell
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' `
  -B clean verify
```

The tracked `.mvn/maven.config` automatically uses the ignored checkout-local
`.m2/repository`, so the command is independent of the user's global Maven
cache and requires no machine-specific repository argument.

The reactor produces both executables in the repository's single product
artifact directory:

```text
target/CoronaPoker-24.11-swing.jar
target/CoronaPoker-24.11-gdx.jar
```

The reactor's `clean` phase removes versioned product JARs and obsolete smoke
logs from that directory while preserving the last complete runnable pair until
their replacements are ready. Running `package` for only one frontend updates
only that frontend and is not a clean distribution build.

The GDX JAR is built exclusively from the product GDX frontend.

## Validation

The `coronapoker-qa` module verifies dependency direction, prohibited core
imports and unique ownership of every class. The extended `tools/qa` suite adds
protocol, recovery and multi-process application scenarios.

A successful build does not validate visual fidelity, audio or frame pacing.
Manual windowed/full-screen runs and screenshot comparison remain independent
release criteria.
