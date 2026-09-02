# 02 — Wire sorting/toggleable/depthTest/cssClasses into BlueMap marker builders

**What to build:** Pass the four new `MarkerGroup` fields (ticket 01) into the corresponding BlueMap API builder
calls in `BlueMapAPIConnector`.

See `../spec.md` for full context.

**Blocked by:** 01-marker-group-config-fields.md

**Status:** code complete; manual verification outstanding

- [x] `getOrCreateMarkerSet`'s `MarkerSet.builder()` call adds `.sorting(markerGroup.sorting())` and
      `.toggleable(markerGroup.toggleable())` alongside the existing `.defaultHidden(...)`.
- [x] `setLineMarker`/`setShapeMarker`'s `LineMarker.builder()`/`ShapeMarker.builder()` calls add
      `.depthTestEnabled(markerGroup.depthTest())`.
- [x] The POI marker creation path's `POIMarker.builder()` call adds
      `.styleClasses(markerGroup.cssClasses().toArray(new String[0]))` when `cssClasses` is non-empty.
- [x] `./gradlew build` passes.
- [ ] Manually verified via `runServer`: set each field on a test group, `/bluemap reload`, confirm menu order,
      toggle-ability, depth-test behavior (place a `LINE`/`SHAPE` group underground), and a `custom.css` rule
      targeting a configured `cssClasses` value all take effect in BlueMap's web UI.
