Type: task
Status: resolved
Blocked by: 02, 03

## Question

Assemble `agent-context/plans/player-marker-colors/spec.md` consolidating: the map's Notes (locked decisions), ticket 01's
research findings, ticket 02's config schema, and ticket 03's recompute design, into a single implementation-ready
spec — plus the README/config-example updates it implies (per the map's "Not yet specified"). This is the
destination; once written, this map is done.

## Answer

Written to `agent-context/plans/player-marker-colors/spec.md` — consolidates the map's Notes plus tickets 01-03 into an
implementation-ready spec: `allowPlayerColors` config field, `SignEntry` V6 dye persistence, the
`updateText`-mixin hook, `Representation` carrying each sign's raw dye, the `ColorResolver` conflict rule, the
`ActionFactory` signature change, and a README/implementation checklist. No open decisions remain.
