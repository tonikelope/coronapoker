# Swing to GDX dialog parity

This document records the dialog and warning audit used to prevent the GDX
frontend from silently dropping behavior that existed in the last stable Swing
release.

## Baseline and scope

- **Reference:** tag `v24.10`, the last stable Swing release.
- **Audit date:** 2026-10-17.
- **Mechanical inventory:** 91 calls to `Helpers.mostrarMensaje*` in the main
  CoronaPoker process.
- **Separate executable:** the three calls in `coronaupdater/Init.java` belong
  to the updater process and are not part of the 91-call frontend inventory.

The number 91 is a call-site count, not a count of missing GDX popups. It
includes four waiting-room wrapper methods, repeated error paths, confirmations
owned by tools that were replaced with native GDX workflows, and runtime
decisions that now cross renderer-neutral interfaces.

Parity means preserving the user-visible decision or warning contract. It does
not require retaining a Swing widget, the old helper name, or the same
translation key when GDX provides an equivalent native message.

## Inventory disposition

| Swing area | Calls | GDX disposition |
| --- | ---: | --- |
| Dealer and table runtime (`Crupier`) | 16 | Mandatory engine contract. Zero Trust, misdeal, IWTSTH, recovery, end-of-table and fatal errors use `GameDialogSink` or `GameDecisionSink`; GDX renders them in-window. |
| Table shell and player actions (`GameFrame`, `CommunityCardsPanel`, `LocalPlayer`, `RemotePlayer`) | 10 | Native table confirmations and errors cover last hand, fold-with-free-check, rebuy limits, forced reconnect and remote timeout/expulsion. GDX applies pause immediately, so Swing's informational “pause on next turn” popup is intentionally obsolete. |
| Waiting room and legacy executable trust (`WaitingRoomFrame`, `Participant`) | 7 | Four calls are helper wrappers rather than separate behavior. Lobby errors and rebuy feedback use native notices. The old modified-executable warning was deliberately retired with the executable check when SRA mental poker became the security authority; GDX must not revive it. |
| New game, presets and settings (`NewGameDialog`, `WaitingGameSettingsPanel`, `BlindStructureManagerDialog`, `SettingsDialog`) | 19 | Native GDX validation, profile confirmations, recovery status, avatar validation, blind-structure editor, unsaved-changes confirmation and restore feedback. Invalid submissions remain blocked even when feedback is inline or a toast rather than a popup. Swing's advisory password-entropy estimate was deliberately retired and is not a protocol guarantee. |
| Chat images, screenshots and voice notes | 14 | Native GDX confirmations, progress state and error feedback own image history, capture copy/delete and voice-note delete/purge paths. |
| Statistics | 12 | Native statistics pickers and confirmations own imported/private/game deletion and result/error feedback. Legacy log-file export errors belong to the retired Swing export controls. |
| Identicon tool | 2 | The Swing-only PNG copy/save tool was replaced by the native identity/session fingerprint verifier. Verification failure is reported by the GDX dialog; the retired export controls have no live error path. |
| Startup, update and process infrastructure (`Helpers`, main `Init`) | 11 | Native update/mod-update prompts, adult-mod acknowledgement, install/open failures, force-exit handling and `JvmDeadlockWatchdog`. Swing restart plumbing and the old bootstrap window are not active product paths. |
| **Total** | **91** | Every call has an engine-contract, native-workflow or retired-Swing-only disposition. |

## Mandatory runtime route

Runtime warnings must never call a frontend toolkit from the core:

```text
core rule / protocol check
  -> GameDialogSink or GameDecisionSink
  -> GdxGameDialogSink
  -> GdxApplicationShell / CoronaPokerGdxTable
  -> native modal rendered on the GDX thread
```

`GdxLauncher` supplies the concrete sink when constructing the application.
The shell queues messages safely across threads; the table owns modal input,
backdrop and acknowledgement. Tests cover the sink queue and the host/table
maintenance commands.

The mandatory set includes:

- Zero Trust suspicious-peer, unverified-deck, critical-lockdown, seat-redraw
  and recovery-mismatch alerts;
- misdeal/refund and fatal dealer errors;
- IWTSTH authorization and anti-flood feedback;
- rebuy rejection, remote timeout/expulsion and forced-reconnect decisions;
- host anti-DoS traffic warning and the host's expel/block decision.

Anti-DoS monitoring is advisory until the host approves the action. Approval
expels the hostile participant and keeps its network address blocked for the
rest of that table; rejection resets the observation counter without freezing
or disconnecting the participant. These rules are specified in
[`SECURITY.md`](SECURITY.md).

## Deliberately retired Swing paths

The following historical messages are not missing GDX wiring and must not be
reintroduced merely because an old translation or call site still exists in a
Swing tag:

- the modified-executable warning, retired when SRA mental poker replaced the
  executable hash as the game-integrity authority;
- the advisory password-entropy estimate, which was never a protocol guarantee
  and is intentionally no longer shown;
- Swing restart/bootstrap plumbing and its private restart-error popup;
- the "pause on next turn" information popup, because GDX applies the requested
  pause immediately;
- PNG copy/save errors from the old identicon export tool, replaced by the
  native identity and session-fingerprint verifier;
- raw game/chat log export errors from retired Swing statistics controls.

## Audit rules for future changes

1. A new core warning or question must be added to a renderer-neutral sink;
   importing Swing or GDX into the core is forbidden.
2. A GDX modal must capture keyboard and pointer input, render above the
   blurred owner surface and have an explicit close/decision path.
3. A status that blocks an operation may use inline validation or a toast only
   when no decision is required and the failure remains visible and
   actionable.
4. Repeated snapshots must not replay the same warning. A state transition may
   re-arm it only when the underlying condition first becomes true again.
5. Removing a Swing-only tool does not require reproducing its private error
   dialogs, but the replacement workflow and the reason must remain recorded in
   the inventory above.
6. A historical translation key or stale snapshot field is not evidence that
   its old condition still exists. The current producer and authority must be
   traced before adding a GDX warning; retired executable-integrity checks, for
   example, must not be revived after SRA mental poker replaced them.
7. Security and protocol alerts require focused automated coverage plus the
   security tests appropriate to the changed core path. Passing tests do not
   constitute visual certification of a modal.
