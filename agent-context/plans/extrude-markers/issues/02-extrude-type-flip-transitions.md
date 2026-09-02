# 02 — EXTRUDE type-flip transitions (POI/LINE/SHAPE ↔ EXTRUDE)

**What to build:** Every `POI`↔`EXTRUDE`, `LINE`↔`EXTRUDE`, and `SHAPE`↔`EXTRUDE` type-flip pair (both directions)
self-heals on `/bluemap reload` without leaving an orphaned marker behind, matching every other type-flip pair
today. If ticket 01's `SignTransitionResolver` refactor already generalizes over group type (as `SHAPE`'s did over
`POI`/`LINE` — see `../../../../.scratch/shape-markers/issues/01-core-shape-marker-happy-path.md`'s Comments), this may already
be covered; this ticket exists to verify and add explicit test coverage either way.

See `../spec.md` for full context.

**Blocked by:** 01-core-extrude-marker-happy-path.md

**Status:** ready-for-agent

- [ ] A sign's prefix change moving it from a `POI`/`LINE`/`SHAPE` group into an `EXTRUDE` group (or vice versa)
      removes the old marker and creates/updates the new one, with no duplicate or leftover marker.
- [ ] A group's `type` flip (e.g. `SHAPE` → `EXTRUDE`) takes effect on `/bluemap reload` without a server restart,
      using `GroupTransitionMarkerAction` (remove-old/add-new pair) the same way existing type flips do.
- [ ] `SignTransitionResolverTest` covers every `POI`/`LINE`/`SHAPE`↔`EXTRUDE` combination (both directions),
      including the `isReload` variants exercised by existing `SHAPE`/`LINE`/`POI` reload tests.
- [ ] `./gradlew build` passes.
