# Testing and certification

CoronaPoker separates code verification from game-behaviour certification.
This keeps everyday feedback fast and makes a certification result easy to
interpret.

![CoronaPoker testing and certification flow](diagrams/testing-certification-flow.png)

## The four layers

| Layer | Purpose | Typical cost | Main command |
|---|---|---|---|
| Product tests | Rules, networking, persistence, security, GDX contracts and architecture | A few minutes | `mvn clean verify` |
| Extended QA | Large opt-in regression suite under `tools/qa` | Minutes for the default lane; longer for slow profiles | `mvn -f tools/reactor/pom.xml verify` |
| Headless campaigns | Seeded high-volume protocol and fault simulation | Proportional to the requested hands and faults | `.\tools\qa\headless-sim.cmd` |
| GDX certification | Complete gameplay scenarios, including real sockets and separate processes where required | From a targeted run to a long stress run | `.\tools\qa\certify.cmd -Mode balanced` |

The layers are complementary. A build failure is a code-test failure. A
certification failure is a gameplay-scenario failure. `certify.cmd` does not
silently run the other layers.

## Automatic and opt-in execution

| Execution | Starts when | Included work |
|---|---|---|
| Product build gate | Every `mvn verify`, `mvn package`, `mvn clean verify` or `mvn clean package` unless tests are explicitly skipped | Product unit tests, deterministic integration tests and architecture rules |
| Extended QA | Only when `tools/reactor/pom.xml` or `tools/qa/pom.xml` is invoked | The selected `qa-*` profile; the recommended reactor also runs the product gate against the same checkout |
| Headless campaign | Only when `headless-sim.cmd` is invoked | Configurable seeded protocol and fault simulations; by default it first runs the product gate |
| GDX certification | Only when `certify.cmd` is invoked | Isolated behavioural scenarios selected by mode or name |

The dependency is one-way: a product build never enters `tools/qa`, but the
recommended QA reactor runs the product modules before its selected tool suite.
The product gate is intentionally substantial: it includes deterministic
in-process and real-loopback-socket integration tests because those checks are
stable enough to protect every build. The expensive statistical, high-volume,
multi-process and certification workloads remain opt-in. They never start in
the background and are not prerequisites for an ordinary local compile.

The commands are self-contained unless stated otherwise. The extended QA
reactor runs the product build and its tests before `tools/qa`. The headless
runner uses that reactor by default unless `-SkipGameBuild` is explicitly used;
that switch requires compatible product artifacts to be installed already.
The certifier performs a clean GDX test compilation with tests skipped and then
starts only each scheduled scenario test in an isolated Maven process.

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

Select one test method by appending `#methodName`:

```powershell
mvn -f modules/pom.xml -pl coronapoker-gdx -am test `
  '-Dtest=GdxScenarioContractTest#everySwingScenarioHasItsOwnStrictGdxCoverage' `
  '-Dsurefire.failIfNoSpecifiedTests=false'
```

## Extended QA

`tools/qa` is an independent opt-in test module and is never reached by a root
product build or packaged in the game. The reactor wrapper builds it against
the current product sources, so no manual installation or version pin is
needed.

The Maven profiles are the executable grouping. They are intentionally based
on runtime characteristics rather than mirroring every source package:

| Selection | What it validates | Relative cost | When to use it |
|---|---|---:|---|
| default or `qa-fast` | Deterministic regressions not tagged `slow` | Low | Normal development and before a commit that changes shared code |
| `qa-heavy` | All slow non-bot regressions, including cryptographic differentials, socket stalls and protocol campaigns | High | After broad crypto, concurrency, network or protocol changes |
| `qa-crypto` | Slow tests in the cryptographic package | High | After changes to SRA, proofs, shuffle or field arithmetic |
| `qa-network` | The slow real-socket stall integration test | Medium | After socket framing, timeout or shutdown changes |
| `qa-protocol-sim` | Seeded protocol, lifecycle, Rabbit, recovery and SQL campaigns | Configurable | For high-volume deterministic protocol validation |
| `qa-headless-all` | Every automated non-visual integrity check except bot-strength statistics and the environment-specific ACL smoke | High | Broad regression before a release or after cross-cutting changes |
| `qa-all` | Fast and slow replayable QA except bot-strength statistics | High | Complete extended regression on a suitable developer or build host |
| `qa-bots` | Statistical matchups, hand-potential sampling and bot game-flow smoke | Very high | Only after bot decision or evaluator changes, and before a release that changes bots |

`qa-fast` is contained in `qa-all` and `qa-headless-all`; running either broad
lane immediately after `qa-fast` repeats those fast checks. `qa-crypto` and
`qa-network` are focused subsets of the slow coverage in `qa-heavy` and
`qa-all`. `qa-bots` is deliberately independent and is never pulled in by an
aggregate profile. The protocol simulator can be invoked directly through the
profile, but `headless-sim.cmd` is preferred because it validates arguments,
records or generates a replay seed and builds the current checkout by default.
Focused profiles select only their named workload; run the default fast lane
first unless `qa-all` or `qa-headless-all` will cover it in the same validation.

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

# Seeded protocol simulations using Maven properties directly.
mvn -f tools/reactor/pom.xml verify -P qa-protocol-sim

# Every non-visual integrity check except statistical bot quality.
mvn -f tools/reactor/pom.xml verify -P qa-headless-all
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

The same selector syntax runs one method:

```powershell
mvn -f tools/reactor/pom.xml verify `
  '-Dtest=PotMathTest#singleWinnerGetsEverythingExact' `
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

`GdxScenarioContract.CERTIFICATION_SCENARIOS` is the only executable scenario
catalogue. It contains every complete GDX gameplay scenario: independent JVMs
over real loopback sockets, native GDX controls, focused network flows, IWTSTH,
blind increases and post-Swing behaviour such as Rabbit Hunting. The certifier
reads that one map, resolves every named JUnit method and launches each one in a
fresh Maven process. There is one command, one schedule and one result.

`tools/qa/reference/swing-gold-scenarios.tsv` only preserves the final
37-scenario Swing baseline. Contract tests require every historical identifier
to remain in the unified catalogue and to retain at least one real
independent-process port. It is not a second executable lane, and the Swing code
is neither part of the product nor executed.

### Modes

| Mode | Catalogue | Repetitions | Normal soak | Use |
|---|---:|---:|---:|---|
| `quick` | Critical subset | 1 | 5 hands | Inner-loop behavioural check |
| `fast` | Complete (same catalogue) | 1 | 5 hands | Full breadth before broader validation |
| `balanced` | Complete (same catalogue) | 2 | 20 hands | Normal release certificate |
| `stress` | Complete (same catalogue) | 5 | 50 hands | Major or adversarial validation |

`fast`, `balanced` and `stress` never add or remove scenarios: only their number
of passes and soak depth change. Stress also deepens the heads-up, full mixed
and full-human normal topologies. Explicit `-ScenarioRepeats` and `-SoakHands`
values override the mode defaults.

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

The printed scenario seed controls every game-path entropy domain used by the
GDX harness: hand and shuffle seeds, commit-reveal seating, bot personality and
decisions, automated straddle choices, recovery seating and observable
cinematic selection. Each domain is derived independently, so adding a draw in
one cannot shift the others. Cryptographic keys, proof randomness, transport
nonces and encryption IVs deliberately remain secure and may differ on replay;
they do not change the resulting cards or functional scenario path. A direct
JUnit replay may use `-Dcoronapoker.qa.scenarioSeed=<seed>`; the certifier sets
that property automatically from the seed recorded in its report.

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

Use `mvn clean` for product build cleanup. Because extended QA is deliberately
outside the product reactor, use `mvn -f tools/reactor/pom.xml clean` when its
generated `tools/qa/target` state should also be removed. Do not delete caches
or reports while another Maven, certification or game process is using them.

See [Adding GDX test scenarios](ADDING_TEST_SCENARIOS.md) for contributor rules
and scenario wiring.
