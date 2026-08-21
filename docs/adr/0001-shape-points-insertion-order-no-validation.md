# Shape marker points follow insertion order, with no geometry validation

`SHAPE` groups (polygon markers built from signs sharing a prefix and label) order their points by
`createdAtMillis` — placement time, not spatial position — and never validate the resulting polygon for
self-intersection or zero-area collinear points. We considered spatially sorting points (e.g. by angle around a
centroid) or rejecting/warning on a degenerate shape, but chose insertion order + no validation to stay consistent
with the existing `LINE` group's contract, which already accepts placement-order zigzags as a known limitation. A
bowtie or sliver shape is possible if signs aren't placed in a sensible perimeter walk; the placer is expected to
notice and fix it in-game, the same way a bad `LINE` is fixed today.
