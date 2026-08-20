# Context

Glossary for BlueMap Sign Markers. See `AGENTS.md` for architecture; this file is vocabulary only.

## Terms

**Marker group** — a configured `(prefix, matchType, type, ...)` rule (`MarkerGroup`) that turns matching sign text
into a marker. `type` (`MarkerGroupType`) picks the shape: `POI`, `LINE`, or `SHAPE`.

**POI marker** — a single-sign point marker. One sign, one marker.

**Line** — a multi-sign marker built from every sign sharing a `LINE` group's prefix and the same label text (that
shared `(prefix, label)` pair is the line's membership key). Renders once 2+ members exist; points are ordered by
placement time (`createdAtMillis`), not spatial position.

**Shape** — a multi-sign marker built the same way as a Line (shared `(prefix, label)` membership key, points
ordered by placement time), but rendered as a flat, closed BlueMap `ShapeMarker` (2D polygon at one Y height, with a
fill) instead of an open 3D line. Renders once 3+ members exist — a 2-point "polygon" is degenerate. The shape's Y
height is taken from its oldest (first-placed) member, the same sign that anchors point order. A Shape's detail
popup shows the members' shared label text, same as a Line's.

**Label** — the sign text after a group's prefix is stripped. For `LINE`/`SHAPE` groups, label equality (within the
same prefix) is what makes two signs members of the same line/shape.

**Representation** — a sign's computed `(group, label, detail)` under the current config, or `null` if no group
matches its prefix (`SignTransitionResolver.Representation`). Comparing a sign's old vs. new representation is how
the mod decides what marker action to dispatch on any change (edit, removal, or a `/bluemap reload` config swap).
