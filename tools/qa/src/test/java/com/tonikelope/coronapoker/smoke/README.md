# Smoke harness: fast refactoring invariants

This package contains the invariant smoke tests run before code changes are
merged. [`docs/TESTING.md`](../../../../../../../../../docs/TESTING.md) is the
canonical source for test lanes, execution order and
[adding scenarios](../../../../../../../../../docs/TESTING.md#adding-scenarios).

## Purpose

These tests do not measure playing quality. The 10,000-hand `Baseline*` and
`Multiway_*` matchups under `bot/harness/` do that. Smoke tests verify that the
code still satisfies fundamental invariants: chip conservation, no NaN or
infinite values, non-negative stacks, monotonic counters and no exceptions.

They answer one question: after a change, does the basic game flow still work?

## When to run them

- After any change to `Crupier.java`, `Bot.java`, `bot/*` or other hand-flow
  code.
- Before merging a development branch into `master`.
- Fast smoke tests run automatically in the default fast lane because the QA
  POM includes `**/*Smoke.java`. `GameFlowSmoke` is tagged `slow`, so it belongs
  only to the explicit `qa-bots` lane and never to `qa-heavy` or `qa-all`.

## Running only smoke tests

```powershell
mvn -f tools/reactor/pom.xml verify -P qa-fast '-Dtest=*Smoke' '-Dsurefire.failIfNoSpecifiedTests=false'
```

That command skips `GameFlowSmoke` because the default lane excludes its
`@Tag("slow")`. Run it explicitly with:

```powershell
mvn -f tools/reactor/pom.xml verify -P qa-bots '-Dtest=GameFlowSmoke' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Fast smoke tests take a few seconds. `GameFlowSmoke` adds up to approximately
30 seconds, as documented by the class itself.

## Deliberately covered elsewhere

- Bot quality and equity: `bot/harness/`.
- SRA cryptography: `sra/`.
- Real socket protocol behavior: `net/`, including framing, stall/back-pressure
  and send-queue tests. Full multiplayer GDX games run in separate JVMs through
  `tools/qa/certify.cmd`. See `docs/TESTING.md`.
- Pixel-level GDX paint and layout inspection remains manual. Functional GDX,
  socket and `Crupier` transitions are automated by the multiprocess suite.

## Test inventory

The default fast lane runs every smoke below except `GameFlowSmoke`.

| Class | Contract | Lane |
|---|---|---|
| `GameFlowSmoke` | Bot engine and 3/6/9-seat game flow at every difficulty: chip conservation, finite values, non-negative stacks, monotonic hand number and valid winners | qa-bots |
| `HandEvaluatorSmoke` | The ten hand rankings and evaluator edge cases such as the wheel, kickers, full house selection and straight flushes | fast |
| `I18nBundleIntegritySmoke` | Structural integrity of localization bundles and referenced keys | fast |
| `IdentityKeypairAclSmoke` | Owner-only private-key ACL and public-key creation | fast |
| `MisdealRefundOrderSmoke` | Money conservation when a hand is cancelled during settlement | fast |
| `PropertiesResilienceSmoke` | Startup behavior with malformed Unicode escapes and unreadable properties | fast |
| `ReadBoundedLineSmoke` | Bounded-line parsing, LF/CRLF trimming, EOF semantics and clean wire characters | fast |
| `SynthesizeFoldActionSmoke` | Canonical synthetic fold when a peer exits and defensive invalid-input handling | fast |
| `TelemetryWireFormatSmoke` | Telemetry round-trip, conflict characters and malformed payload tolerance | fast |

## Adding a smoke test

1. Create `XxxSmoke.java` in this package.
2. Document the scenario in the class.
3. Keep each method below 30 seconds.
4. Assert specific observables rather than only asserting that no crash occurs.
5. Add the class to the table above.

Before adding one, use the layer selector in
[Adding scenarios](../../../../../../../../../docs/TESTING.md#adding-scenarios).
A normal domain regression usually belongs beside its package. A flow requiring
real sockets or `Crupier` belongs in the multi-JVM simulator.
