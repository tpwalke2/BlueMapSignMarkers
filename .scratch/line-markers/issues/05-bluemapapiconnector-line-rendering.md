# 05 — `BlueMapAPIConnector`: building the actual `LineMarker`

**Spec:** `.scratch/line-markers/spec.md` §5 ("`BlueMapAPIConnector`: building the actual `LineMarker`")

**Blocked by:** 04 (needs `SetLineMarkerAction`/`RemoveLineMarkerAction`/`DispatchedMarkerIdentifier`/`GroupTransitionMarkerAction`)

**What to build:**
- New `common/ColorUtils.java` — `parseHex(String hex) -> int[]{r,g,b,a}`, plain Java, no BlueMap types, unit
  testable.
- `core/bluemap/BlueMapAPIConnector.java`:
  - `applyToMarkerSets` parameter narrows from `MarkerIdentifier` to `DispatchedMarkerIdentifier` (needs only
    `.parentSet()`, already on the interface).
  - Extract `removeMarker`'s body into id-based `removeMarkerById(String id, Stream<Map<String, Marker>>)`, reused
    by both `RemoveMarkerAction` and `RemoveLineMarkerAction`.
  - New cases in `processMarkerAction`'s switch:
    ```java
    case SetLineMarkerAction setAction ->
            applyToMarkerSets(setAction.getMarkerIdentifier(), maps -> setLineMarker(setAction, maps));
    case RemoveLineMarkerAction removeAction ->
            applyToMarkerSets(removeAction.getMarkerIdentifier(), maps -> removeMarkerById(removeAction.getMarkerIdentifier().getId(), maps));
    ```
  - `setLineMarker`:
    ```java
    private static void setLineMarker(SetLineMarkerAction action, Stream<Map<String, Marker>> markerSetMaps) {
        if (action.getPoints().size() < 2) return; // defensive - SignManager should never dispatch below 2
        var line = new Line(action.getPoints().stream().map(p -> new Vector3d(p.x(), p.y(), p.z())).toList());
        var color = ColorUtils.parseHex(action.getLineColor());
        markerSetMaps.forEach(markers -> markers.put(action.getMarkerIdentifier().getId(),
                LineMarker.builder()
                        .label(action.getLabel())
                        .detail(HtmlUtils.toHtmlDetail(action.getDetail()))
                        .line(line)
                        .lineWidth(action.getLineWidth())
                        .lineColor(new Color(color[0], color[1], color[2], color[3]))
                        .build()));
    }
    ```
  - `ChangeGroupMarkerAction` case in `processMarkerAction` and `logProcessingMessage`'s switch → `GroupTransitionMarkerAction` case, iterating `effects` inside the same synchronized method (same atomicity guarantee as before,
    generalized to a variable count).
  - `logProcessingMessage` pattern-matches on `action.getMarkerIdentifier()`: `MarkerIdentifier` logs x/y/z,
    `LineMarkerIdentifier` logs label/point-count. Add `SetLineMarkerAction`/`RemoveLineMarkerAction` cases
    ("Adding"/"Updating" chosen from `isFirstAppearance` for `SetLineMarkerAction`, per §4's log-only flag).
- Line detail text: each member sign contributes its own detail (`SignEntryHelper.getDetail`); the line's
  `detail` is those joined in point order, same `FRONT:`/`BACK:`-style join `SignEntryHelper.getDetail` already
  uses.

**Status:** resolved

- [x] `ColorUtils.parseHex` implemented with a unit test covering valid/invalid hex input
- [x] `setLineMarker`/`removeMarkerById` implemented; `applyToMarkerSets` widened (already narrowed to
      `DispatchedMarkerIdentifier` by ticket 04)
- [x] Both switch statements (`processMarkerAction`, `logProcessingMessage`) have cases for
      `SetLineMarkerAction`/`RemoveLineMarkerAction`/`GroupTransitionMarkerAction` — no silent `default`
      fallthrough for these types (`GroupTransitionMarkerAction` case was already present from ticket 04)
- [x] `./gradlew test` and `./gradlew build` pass
- [ ] Manual smoke test deferred to ticket 08 (needs `SignManager` wiring from ticket 06 to actually dispatch a
      line action end-to-end)

## Comments

Implemented `common/ColorUtils.parseHex` (accepts `#RRGGBB`/`#RRGGBBAA`, optional leading `#`, falls back to
opaque red on malformed input) plus `BlueMapAPIConnector.setLineMarker`/`removeMarkerById` and the two new switch
cases, matching the spec's code exactly. `applyToMarkerSets` and the `GroupTransitionMarkerAction` case were
already in place from ticket 04, so no change needed there.
