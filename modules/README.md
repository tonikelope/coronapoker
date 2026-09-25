# CoronaPoker 24.11 product modules

This directory contains the Maven reactor for the CoronaPoker desktop
application. The modules provide compile-time boundaries inside one product.
The public build output is a single runnable JAR.

## Source ownership

- `coronapoker-core` owns poker rules, hand progression, bots, networking,
  persistence, security and renderer-neutral presentation contracts.
- `coronapoker-assets` packages the resources stored in
  `../src/main/resources`. Installable MOD packs remain external to the
  official JAR.
- `coronapoker-gdx` owns the desktop launcher, screens, table renderer, input,
  audio and operating-system integration.
- `coronapoker-qa` verifies dependency direction, source ownership and product
  distribution rules.

Each code module has its own `src/main/java` tree. This is stronger than using
packages inside one source tree: Maven allows GDX to depend on the core while
preventing the core from importing GDX. Product Java sources do not live in the
repository-root `src/main/java` directory.

Shared resources deliberately remain under `../src/main/resources`. They are
product data, not a second Java source tree.

## Build

From the repository root:

```powershell
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' `
  -B clean verify
```

The tracked `.mvn/maven.config` uses the ignored checkout-local
`.m2/repository`, so the build does not depend on the user's global Maven
cache.

The reactor publishes the runnable application to the repository's product
artifact directory:

```text
target/CoronaPoker-24.11.jar
```

The root `coronaupdater.jar` is retained because the self-updater requires that
location. Module-local `target` directories contain intermediate Maven output,
not additional distributions.

## Validation

The `coronapoker-qa` module checks the architecture during the normal product
build. The extended suite under `tools/qa` adds protocol, recovery, security,
simulation and application scenarios.

A successful automated build does not validate visual fidelity, audio quality
or frame pacing. Those release checks require a manual run on the supported
display configurations.
