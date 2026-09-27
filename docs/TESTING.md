# Testing and certification

CoronaPoker separates code verification from game-behaviour certification.
This keeps everyday feedback fast and makes a certification result easy to
interpret.

![CoronaPoker testing and certification flow](diagrams/testing-certification-flow.png)

## The four layers

| Layer | Purpose | Main command |
|---|---|---|
| Product tests | Rules, networking, persistence, security, GDX contracts and architecture | `mvn clean verify` |
| Extended QA | Large opt-in regression suite under `tools/qa` | `mvn -f tools/reactor/pom.xml verify` |
| Headless campaigns | Seeded high-volume protocol and fault simulation | `.\tools\qa\headless-sim.cmd` |
| GDX certification | Complete gameplay scenarios, including real sockets and separate processes where required | `.\tools\qa\certify.cmd -Mode balanced` |

The layers are complementary. A build failure is a code-test failure. A
certification failure is a gameplay-scenario failure. `certify.cmd` does not
silently run the other layers.

## Recommended order

1. Run the smallest test that reproduces the change.
2. Run `mvn clean verify` before committing product or build changes.
3. Run the extended QA lane when shared game code, networking, persistence,
   security or test infrastructure changes.
4. Run the affected GDX scenario while diagnosing behavioural work.
5. Run `certify.cmd -Mode balanced` for a normal release or a significant
   gameplay change.
6. Reserve `stress` for broad protocol, recovery, concurrency or security work,
   or for establishing a new certification baseline.

Documentation-only and presentation-only changes do not require gameplay
certification unless they also change executable commands, input wiring or game
lifecycle behaviour.

## Product build and tests

From the repository root:

```powershell
mvn clean verify
```

This runs the tests in the product reactor:

- `coronapoker-core`: rules, protocol, networking, storage and shared logic;
- `coronapoker-gdx`: frontend state, command/event wiring and GDX contracts;
- `coronapoker-qa`: architecture and source-ownership rules.

The product build excludes tests tagged `certification`. Those scenarios are
run only through `certify.cmd`, where each mapped method receives an isolated
Maven process and the selected certification mode.

The runnable output is `target/CoronaPoker_<version>.jar`. Use
`-DskipTests` only to diagnose packaging; it is not a verified build.

A focused product test can be selected without failing the other modules for
having no matching class:

```powershell
mvn -f modules/pom.xml -pl coronapoker-gdx -am test `
  '-Dtest=GdxScenarioContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'
```

## Extended QA

`tools/qa` is an opt-in test module and is never packaged in the game. The
reactor wrapper builds it against the current product sources, so no manual
installation or version pin is needed.

```powershell
# Fast deterministic QA. This is the default tools/qa selection.
mvn -f tools/reactor/pom.xml verify

# Fast and slow non-bot tests.
mvn -f tools/reactor/pom.xml verify -P qa-all

# Slow non-bot tests only.
mvn -f tools/reactor/pom.xml verify -P qa-heavy

# Statistical bot-strength tests only.
mvn -f tools/reactor/pom.xml verify -P qa-bots

# Slow cryptographic tests only.
mvn -f tools/reactor/pom.xml verify -P qa-crypto

# Slow real-socket integration tests only.
mvn -f tools/reactor/pom.xml verify -P qa-network
```

`qa-bots` remains separate because it measures playing strength statistically
and consumes substantially more CPU than integrity regressions. `qa-all`
intentionally excludes it. Neither profile is a gameplay certificate; only
`certify.cmd` produces scenario-certification evidence.

To run one extended test:

```powershell
mvn -f tools/reactor/pom.xml verify `
  '-Dtest=PotMathTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'
```

## Headless campaigns

The simulator stresses production protocol and domain components without
starting a complete GDX table lifecycle:

```powershell
.\tools\qa\headless-sim.cmd `
  -Hands 5000 -Faults 5000 -BotHands 100 -Seed 42
```

Use it for conservation, settlement, SRA, signed actions, Rabbit, Run It Twice,
exit/recovery models, SQLite replay and seeded fault injection. It is not a
substitute for socket/process scenarios.

`-AllNonVisual` runs all automated non-visual QA except statistical bot quality.
Run `headless-sim.cmd -Help` for the current options.

## GDX behavioural certification

The public certification entry point is:

```powershell
.\tools\qa\certify.cmd -Mode balanced
```

`tools/qa/reference/swing-gold-scenarios.tsv` preserves the final Swing scenario
catalogue. `GdxScenarioContract.STRICT_HOMOLOGUE_TESTS` is its executable GDX
mapping, while `GDX_ONLY_SCENARIOS` holds product scenarios added after the
baseline, including Rabbit Hunting. The certifier reads both mappings directly,
resolves every named JUnit method and launches each one in a fresh Maven process.
There is no second hand-maintained scenario list.

The mapping includes production-core game flows, native GDX command/dialog
wiring and multiprocess tests. Scenarios that historically depended on sockets,
disconnects, crashes or recovery keep separate host/client JVMs. The Swing code
is not part of the product and is not executed.

### Modes

| Mode | Catalogue | Repetitions | Normal soak | Use |
|---|---:|---:|---:|---|
| `quick` | Critical subset | 1 | 5 hands | Inner-loop behavioural check |
| `fast` | Complete | 1 | 5 hands | Full breadth before broader validation |
| `balanced` | Complete | 2 | 20 hands | Normal release certificate |
| `stress` | Complete | 5 | 50 hands | Major or adversarial validation |

Stress also deepens the heads-up, full mixed and full-human normal topologies.
Explicit `-ScenarioRepeats` and `-SoakHands` values override the mode defaults.

Useful commands:

```powershell
# Show the exact executable catalogue.
.\tools\qa\certify.cmd -ListOnly

# Run every strict test mapped to one scenario.
.\tools\qa\certify.cmd -Scenario spectator-rebuy-cycle -Mode fast

# Replay with a known base seed.
.\tools\qa\certify.cmd -Mode stress -Seed 42

# Continue an interrupted schedule with the original mode and seed.
.\tools\qa\certify.cmd -Mode stress `
  -StartAtScenario reconnect-every-street -StartAtRepeat 3 -Seed 42
```

The certifier clean-compiles the GDX scenario reactor first. It then stops at
the first failed test and writes per-test logs plus `summary.csv` and
`summary.json` below `target/certification/<timestamp>-<mode>/`.

A continuation report is not a standalone certificate. Keep it together with
the preceding partial report. If a fix changes shared protocol, scheduling or
harness semantics, restart the complete required mode instead of resuming.

## Choosing the right gate

| Change | Required validation |
|---|---|
| Local calculation or parser | Focused regression, then `mvn clean verify` |
| GDX layout or cosmetic presentation | Focused contract/layout tests and product build; manual visual review |
| Shared game rules or table lifecycle | Product build, extended QA, affected scenario, then `balanced` when release-bound |
| Sockets, reconnect, crash or recovery | Product build, extended QA, affected multiprocess scenario, then `fast` or `balanced` |
| Crypto or signed protocol | Product build, `qa-crypto`, headless campaign, affected scenarios; `stress` for broad changes |
| Bot decision quality | Product build and `qa-bots`; scenario certification only if game flow changed |
| Test harness only | Harness contracts and affected scenarios; broaden only if common semantics changed |
| Normal release | Product build, appropriate extended QA and `balanced` certification |
| Major release baseline | `fast`, then fresh-seed `stress` after all narrower lanes pass |

Always distinguish a product defect from a harness defect. The first violated
contract, process log and seed replay should identify which side is wrong. Do
not weaken an oracle merely to make a scenario green.

## What automation proves

The automated layers verify rules, money conservation, protocol agreement,
persistence, recovery, command/event wiring, socket/process lifecycle and the
historical scenario catalogue.

They do not certify subjective rendering quality, exact pixels, physical audio
devices, real Internet/NAT behaviour or every operating-system display setup.
Those checks remain manual and complement the automated certificate.

## Generated state

- `.m2/repository` is the ignored checkout-local Maven cache. Deleting it does
  not affect source code, but Maven must reconstruct it.
- Module `target` directories contain intermediate build output.
- Repository-root `target` contains the runnable JAR and ignored QA reports.
- Test homes are generated below build output and must never be committed.

Use `mvn clean` for normal build cleanup. Do not delete caches or reports while
another Maven, certification or game process is using them.

See [Adding GDX test scenarios](ADDING_TEST_SCENARIOS.md) for contributor rules
and scenario wiring.
