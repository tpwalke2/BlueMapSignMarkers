# Research: HOCON parsing approach for reading BlueMap map configs

Type: research
Status: resolved

## Question

BlueMap's per-map config files (`config/bluemap/maps/<id>.conf`, see
`run/config/bluemap/maps/world_nether_roof.conf`) are in HOCON format. This project has no HOCON
parser dependency today (confirmed: nothing in `build.gradle`/`gradle.properties` pulls one in).

Survey options and recommend one, for a small, well-scoped read: just the `render-mask` block
(a list of objects with `min-x/max-x/min-y/max-y/min-z/max-z` numeric fields and an optional
`subtract` boolean) — not full HOCON generality (no need for substitutions, includes, or
object-merging semantics BlueMap's own config may otherwise support elsewhere in the file).

1. What's the standard library BlueMap itself uses to parse its configs (check if it's on the
   `bluemap-api` classpath transitively, or if it's a separate BlueMap-core-only dependency not
   available to API consumers)?
2. What lightweight, actively-maintained Java HOCON parsers exist (e.g. Typesafe/Lightbend
   `config`), and what's each one's jar size / transitive dependency footprint / license? This
   project is a Fabric mod — jar size and dependency shading matter (check `build.gradle` for how
   other deps are currently shaded/included, if at all).
3. Alternative: is a small hand-rolled parser for just the `render-mask` block's subset of HOCON
   syntax (which per the sample file is close to JSON — quoted or bare keys, `{}`/`[]`, numbers,
   booleans, `#`-comments) a realistic lower-risk option instead of adding a new dependency? What
   would it need to handle correctly (comments, trailing commas or lack thereof, nested
   brace/bracket matching) based on the sample files under `run/config/bluemap/maps/`?

Recommend one approach with a one-paragraph justification. This is pure research to inform an
implementation plan — do not write any BSM code.

## Answer

**1. What BlueMap itself uses.** BlueMap's `MapConfig` (bluemap-core) parses its `.conf` files
with SpongePowered **Configurate** (`configurate-core` + `configurate-hocon`, which wraps
`lightbend/config`'s HOCON grammar under the hood). This is a `bluemap-core`-only dependency —
confirmed by reading `BlueMapAPI/build.gradle.kts` (the API module BSM actually depends on):
its only declared dependencies are `flow.math`, `gson` (API surface), plus `jetbrains.annotations`
and `lombok` (compile-only/annotation processing). No Configurate, no HOCON parser, anywhere in
that file. BSM depends on `de.bluecolored:bluemap-api:2.8.0` as `compileOnly`
(`build.gradle:44`) — a `compileOnly` API jar, so even if Configurate were pulled in
transitively (it isn't) it would never end up on BSM's runtime classpath. **Conclusion: nothing
usable is available for free — any HOCON parsing capability has to be added by BSM itself.**

**2. Lightweight Java HOCON parser options.**
- **`com.typesafe:config` (Lightbend `config`, github.com/lightbend/config)** — the reference
  HOCON implementation, Apache 2.0, actively maintained, pure Java, **zero transitive
  dependencies**, jar in the low-hundreds-of-KB range. This is the same grammar BlueMap's own
  Configurate-HOCON binding wraps, so parsing semantics would match BlueMap's exactly (comments,
  bare/quoted keys, no-comma object/array separators, etc.) with no reimplementation risk.
- **SpongePowered `configurate-hocon`** — pulls in `configurate-core` and (transitively)
  `lightbend/config` anyway, plus a heavier API surface (nodes, type serializers) BSM doesn't
  need for a 6-numeric-field-plus-a-boolean record. Larger footprint for no extra benefit over
  using `com.typesafe:config` directly.
- Both are LGPL-free (Apache 2.0 / Configurate is also Apache-2.0-ish), so licensing isn't a
  differentiator.

**Packaging cost for either:** `build.gradle` today has no shading/jar-in-jar setup at all —
`bluemap-api` is `compileOnly` (never bundled), Fabric API modules are `implementation`/
`runtimeOnly` and rely on the player already having Fabric API installed, and there is no
`shadow`/`shadowJar` plugin or Loom `include(...)` call anywhere in the file. Adding
`com.typesafe:config` as a real runtime dependency would be the *first* dependency this project
ever has to bundle into its own jar (via Loom's `include()`/jar-in-jar, since it's not something
end users install separately like Fabric API), which is new build-system surface area, not just
a one-line dependency add.

**3. Hand-rolled parser for the `render-mask` subset.** Every sample file under
`run/config/bluemap/maps/*.conf` (8 files checked) uses exactly the same narrow shape:
```
render-mask: [
  {
    #min-x: -4000
    min-y: 127
  }
  {
    subtract: true
    min-y: 0
    max-y: 126
  }
]
```
i.e.: a top-level `render-mask: [ ... ]` array; array elements are `{ ... }` objects with **no
commas between elements** (whitespace/newline-separated, standard HOCON array syntax); each
object has only bare (unquoted) keys from a fixed known set (`min-x`, `max-x`, `min-y`, `max-y`,
`min-z`, `max-z`, `subtract`); values are bare integers or `true`/`false`, again comma-optional
between key-value pairs; `#` starts a line comment anywhere, including commenting out an entire
key inside an object; no nesting beyond object-in-array, no substitutions (`${...}`), no
includes, no multi-line/triple-quoted strings, no string values at all in this block. This is a
strict, well-bounded subset — a hand-rolled parser only needs to: strip `#`-comments per line,
find the `render-mask` key's value bracketed by matching `[`/`]` (accounting for nested `{`/`}`
pairs so a stray `]`-like character inside a comment or string doesn't confuse it — though none
of the sample values contain one), split that span into `{`...`}` chunks, and inside each chunk
parse `key: value` pairs (colon-separated, comma-or-newline-separated, whitespace-tolerant) into
the six numeric fields and the boolean flag, defaulting anything absent/commented-out to
"unbounded" for that axis. This is a much smaller and more mechanical piece of code than a
general HOCON tokenizer — it never has to handle strings, substitutions, includes, or arbitrary
object nesting because the field is contractually limited to that shape by BlueMap's own schema
for `render-mask`.

## Recommendation

**Hand-roll a minimal parser scoped strictly to the `render-mask` array**, not a general HOCON
library. Reasons:
- No parser dependency exists on BSM's actual classpath today (bluemap-api is `compileOnly` and
  carries no HOCON support anyway) — either library option means adding and packaging a brand
  new runtime dependency, and this project's `build.gradle` has no shading/jar-in-jar mechanism
  in place yet to bundle one. That's new build-system risk for a feature that only needs to read
  six numbers and a boolean.
- The actual syntax surface to support is small and closed (confirmed across all 8 sample
  `.conf` files: same array-of-objects-with-known-keys shape, no substitutions/includes/nesting
  anywhere in this block) — a scoped hand-rolled parser directly matches the "just the
  `render-mask` block, not full HOCON generality" scope the question itself sets, and keeps the
  parsing logic in a plain-Java class with no external dependency, consistent with this
  project's "prefer testable, dependency-free plain Java for parsing logic" convention (see
  `AGENTS.md`'s "Testable vs. game-coupled code" section — `SignLinesParser` is the existing
  precedent for exactly this kind of small hand-rolled parser).
- Risk is bounded and testable: unit tests can pin down comment-stripping, missing/commented
  fields defaulting to unbounded, and `subtract: true` detection directly against fixtures
  copied from the real sample files, with no reliance on a third-party library's own edge-case
  behavior.
- If BlueMap's `render-mask` syntax ever grows beyond this subset (e.g. someone hand-writes
  substitutions into it) the fail-open behavior already required by the parent map ("fails open
  on any missing/unreadable/unparseable map config") absorbs that risk safely — an unparseable
  file just means unbounded, not a crash.

Do **not** pull in `com.typesafe:config` or `configurate-hocon` for this: both are reasonable
libraries in isolation (typesafe/config in particular has zero transitive deps and would parse
correctly), but they solve a bigger problem — full HOCON — than this feature has, at the cost of
introducing this project's first bundled runtime dependency and jar-in-jar packaging step for a
six-field/one-boolean read.
