# Source prettify plan — English comments + Javadoc + dead code + formatting

Status: **active**, branch `code-prettify` (cut from `master`). Supersedes the earlier
Spanish comments-only pass (branch `prettify-comentarios`, kept only as a record).

## Goal

Leave the source clean and consistent:

- ALL prose comments and Javadoc in **English**, condensed to only the useful and true ones.
- **Javadoc** normalized on public classes / methods.
- **Dead code** removed (conservatively).
- Light, consistent **formatting / indentation**.

Commit messages, releases and anything on GitHub are written in **English**.

## Golden rule: behaviour must not change

Reformatting and dead-code removal are behaviour-preserving; the `tools/qa` JUnit suite is the
safety net. Every gate must keep the build green **and** the tests passing. A baseline run on
clean `master` is taken first so any breakage is attributable.

## What each file gets

1. **Comments → English.** Translate every Spanish prose comment (and Javadoc) to English.
   Condense rambling blocks down to the non-obvious *why*; drop narration of the obvious and dead
   history. Verify each against the code — fix or delete false/obsolete comments; never leave a
   false comment, and never change code to match a comment.
2. **Javadoc.** Normalize on public classes and methods (`@param`/`@return`/`@throws` where they
   add value). Keep it terse.
3. **Dead code (conservative).** Remove unused imports, and unused **private** fields / methods /
   local variables that are provably unreferenced. Never remove public/protected members, anything
   reachable by reflection (e.g. `Huevos.M1`/`M2`, string-named lookups) or by the NetBeans form,
   and never touch generated regions.
4. **Formatting.** Keep the file's existing NetBeans style (4-space indent, K&R braces, ~100 col).
   Fix clearly-wrong indentation/spacing; do **not** reflow whole files gratuitously. (The code is
   already largely NetBeans-formatted, so these diffs stay small.)

## Forbidden zones (leave byte-for-byte)

- GPL license header + ASCII art at the top of each file.
- NetBeans generated code and markers: `// <editor-fold ... Generated Code>`, `//GEN-BEGIN`,
  `//GEN-END`, `//GEN-FIRST`, `//GEN-LAST`, `Variables declaration - do not modify`.
- Trailing `// NOI18N`.
- The `.form` files.
- Vendored / third-party code, OUT OF SCOPE entirely: packages `org.alberta.poker.*` (Alberta
  poker AI) and `org.dosse.upnp.*`, plus any attributed boilerplate (e.g. the WrapLayout Javadoc).

## Verification per batch

1. `mvn -o -DskipTests compile` — fast smoke.
2. `mvn -DskipTests install` + `mvn -f tools/qa/pom.xml test -Dcoronapoker.version=23.33` — run
   after batches that remove code and after the big files; reduced volume during iteration
   (`-Dqa.sessions`/`-Dqa.hands`), a full run at the end.
3. Review each diff for accidental logic changes.
4. English commit messages. Small leaf classes may be grouped per batch; big files commit alone.

## Order of attack (scope = `com.tonikelope.coronapoker` only, ~144 files)

1. Warm-up: the smallest leaf classes (bot/eval, small UI helpers).
2. Medium core: crypto/*, dialogs, panels, net.
3. Large: `StatsDialog`, `NewGameDialog`, `RemotePlayer`, `LocalPlayer`, `Helpers`,
   `WaitingRoomFrame`, `GameFrame`.
4. `Crupier.java` (~21k lines) last, split across several commits.
