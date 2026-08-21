# SHAPE duplicates LINE's group-resolver/action pattern instead of a generic abstraction

`SHAPE` groups are structurally identical to `LINE` groups: N signs sharing a `(prefix, label)` key, ordered by
placement time, recomputed on any member change, rendered once a threshold is met. This is the second concrete
instance of that pattern, so extracting a generic "multi-point group" mechanism (parameterized by marker kind and
threshold) was a real option. We chose to duplicate `LINE`'s pattern instead — new `ShapeGroupResolver`,
`SetShapeMarkerAction`/`RemoveShapeMarkerAction`, `ShapeMarkerIdentifier`, and a 3-way switch in
`SignTransitionResolver` — because that resolver's transition table is dense branch-per-type-pair logic already
tuned around two types, and forcing a third through a generic abstraction risked making it harder to read for a
marginal reuse win. Genuinely-identical inline logic (member resolution, join/leave/threshold math) was factored
into shared pure functions where duplication would otherwise be copy-paste, short of a full generic type. Revisit
extraction if a fourth multi-point marker kind appears.
