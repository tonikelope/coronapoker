# CoronaPoker paper artifacts

Reproduction materials for **CoronaPoker: A Verifiable Protocol for Peer-to-Peer Poker**.

This directory contains the exact benchmark source and recorded samples used in the paper, the selection of 193 existing regression tests, and a portable driver for repeating both runs. The measurements concern local computation on valid protocol inputs. The regression results describe selected implementation coverage. Neither establishes a complete soundness, zero-knowledge, or execution-privacy theorem.

## Contents

| File | Purpose |
| --- | --- |
| `ProtocolCost.java` | Original benchmark source, unchanged |
| `results.csv` | Original 44 recorded benchmark samples, unchanged |
| `reproduce.py` | Compile and run the benchmark or the recorded regression selection |
| `test-selection.json` | Exact selected classes, methods, and expected counts |
| `dependencies.json` | Pinned JUnit and SQLite download locations and SHA-256 hashes |
| `provenance.json` | Source revisions, recorded counts, and scope |
| `recorded/benchmark-summary.json` | Medians, ranges, and payload sizes calculated from the recorded CSV |
| `recorded/benchmark-environment.json` | Original benchmark environment |
| `recorded/regression-summary.json` | Original per-selection JUnit counts and elapsed times |
| `recorded/regression-test-cases.csv` | All 193 original test-case names and durations |
| `recorded/regression-source-sha256.json` | Hashes of the compiled production and selected test source files |
| `recorded/regression-core-sources.txt` | Relative paths of the compiled core sources |
| `recorded/regression-test-sources.txt` | Relative paths of the explicitly compiled test sources |
| `recorded/regression-java-version.txt` | Java version reported during the regression run |
| `recorded/source-equivalence.json` | Evidence that the measured cryptographic source is identical between the two recorded revisions |
| `SHA256SUMS` | Integrity manifest for all other distributed files |

The package is prepared for the repository path `docs/paper-artifacts/`. Copy that directory from the ZIP into the repository root and review it through the project's normal contribution workflow. Preparing this package does not publish it to GitHub.

## Requirements

- Git, Python 3.9 or later, and a **full OpenJDK 21** installation.
- Linux or macOS. The original runs used Linux. This portable driver has not been used here to repeat the experiments on macOS.
- An unused working directory outside any existing source checkout.
- Internet access to GitHub for the fresh clones. The regression driver also downloads the two pinned JARs from Maven Central and verifies their hashes before using them.
- Permission to bind loopback sockets for the existing network tests. The benchmark itself opens no game connections.

Maven and additional Python packages are not required. The driver uses the JDK compiler module with `-source 17 -target 17`, matching the recorded compilation. Use `--java /path/to/jdk-21/bin/java` if the default `java` is another version.

## Verify and prepare a fresh working directory

Start in this artifact directory. Choose a new directory name if the example already exists.

```sh
python3 reproduce.py verify-package
CORONAPOKER_ARTIFACTS="$PWD"
mkdir "$HOME/coronapoker-paper-reproduction"
cd "$HOME/coronapoker-paper-reproduction"
```

Keep the artifact directory available while the experiments run. The driver reads its source, selections, and manifests from that location. It never checks out a revision or changes an existing source tree. It requires the specified clone to be clean and at the exact recorded revision. It also refuses an output directory that already exists, including a partial output directory from a failed run.

## Reproduce the benchmark

```sh
git clone https://github.com/tonikelope/coronapoker.git benchmark-source
git -C benchmark-source checkout --detach 11cafd9eb758afcf1850baf3f167888247415729
python3 "$CORONAPOKER_ARTIFACTS/reproduce.py" benchmark \
  --repo benchmark-source --out benchmark-run
```

The driver compiles the original `ProtocolCost.java` into a fresh class directory using the pinned source checkout. It then executes one JVM with `-Xms256m -Xmx2g`, headless mode, and an isolated `user.home` below the output directory.

The benchmark constructs 52-card bundles for 2, 4, 6, and 10 participants. Each configuration has five warmups followed by eleven recorded samples. All 64 constructed bundles, including the warmups, must pass the production verifier for the run to finish. The output CSV contains the 44 recorded samples.

The generated files include:

- `benchmark-run/results.csv` and `benchmark-run/summary.json`
- The exact compiler and runtime command arrays in `*.command.json`
- Compiler output, benchmark stderr, and exit status records
- Java and platform information in `run-environment.json`

The proof calls are `ShuffleCascade.proveBoundStepWire`, `DualLockWire.proveBoundRotationStepWire`, and the `DualLockWire.verifyFullChainWire` overload taking `ShuffleProofContext`. The experiment does not use the legacy unbound proof path.

Fixture creation, key generation, deck transformations, and command Base64 assembly are outside the timed generation interval. Generation includes the production wire constructors. Verification includes binary proof decoding and the complete key, context, and final-deck checks. Process CPU time includes JVM background work. Payload size is one encoded `DUALLOCK_BUNDLE` command, including its four Base64 lists, not total network traffic for a hand.

Fresh random inputs, JIT compilation, garbage collection, hardware, and system load make repeated timings differ. The original environment reported Debian OpenJDK 21.0.13, an Intel Xeon Platinum 8573C, nine logical processors, and a 2 GiB maximum heap. Dedicated processor allocation was not established. The recorded CSV is retained separately from every new run.

## Reproduce the 193 selected regression tests

```sh
git clone https://github.com/tonikelope/coronapoker.git regression-source
git -C regression-source checkout --detach 3a29ce661fe1e07571960c09916ba02b9e3eabf8
python3 "$CORONAPOKER_ARTIFACTS/reproduce.py" regression \
  --repo regression-source --out regression-run
```

The driver downloads JUnit Platform Console Standalone 1.10.2 and SQLite JDBC 3.51.1.0, verifies the hashes in `dependencies.json`, and checks all selected source-file hashes against the recorded manifest. It compiles the core and selected test sources into fresh directories. It then runs the exact class and method selectors in `test-selection.json`.

| Selection | Expected tests |
| --- | ---: |
| Cryptographic components and proof handling | 141 |
| State, authorization, dispatcher, and recovery | 48 |
| Ordinary authenticated two-table game | 1 |
| Selected native gateway methods | 3 |
| **Total** | **193** |

The original run had zero failures, errors, skipped tests, or aborted tests. Seven cases inspect source or documentation text. The other 186 execute Java behavior, ranging from small helpers and legacy primitive tests to current bound-proof paths and native-channel integration. These counts are not counts of complete security scenarios.

The state selection includes a real 60-second missing-proof timeout. Allow several minutes on a slower machine. The ordinary game test completes at least two hands on both local tables. The three gateway methods cover channel closure, authenticated channel transfer, and reconnection without lost or duplicated commands. These are headless loopback tests, not Internet games or exhaustive concurrency exploration.

Every selection gets an isolated `user.home`. Output includes compiler logs, exact command arrays, test logs, exit statuses, `test-summary.json`, and JUnit XML below `reports/`. The driver requires the recorded test counts and zero failures, errors, and skipped cases. It stops with a nonzero exit status if a build, dependency check, selection, or result check fails. Inspect the retained files before drawing conclusions from a failed reproduction.

## Why the two runs identify different source revisions

The timing experiment ran on `11cafd9eb758afcf1850baf3f167888247415729`. The 193-test regression run used `3a29ce661fe1e07571960c09916ba02b9e3eabf8`. The benchmark was not rerun on the latter revision.

The complete cryptographic source directory has the same Git tree object at both revisions. `Crupier.java`, which supplies the contextual starting deck used by the benchmark, also has the same blob. The identifiers are retained in `recorded/source-equivalence.json`. To repeat that comparison after creating either full clone:

```sh
git -C regression-source diff --exit-code \
  11cafd9eb758afcf1850baf3f167888247415729 \
  3a29ce661fe1e07571960c09916ba02b9e3eabf8 -- \
  modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/crypto \
  modules/coronapoker-core/src/main/java/com/tonikelope/coronapoker/Crupier.java
```

No output and exit status zero confirm this comparison. It justifies retaining the measurements for the unchanged cryptographic implementation. It does not turn them into a new timing run or a measurement of gateway latency.

## Integrity and changes

`python3 reproduce.py verify-package` checks every distributed file against `SHA256SUMS`, excluding the manifest itself. The included Git attributes preserve the recorded file bytes across checkouts. The checksums establish consistency within this package, not an external signature or a security certification. If the distributed material is intentionally edited, regenerate the manifest and clearly identify the resulting version before publishing it.
