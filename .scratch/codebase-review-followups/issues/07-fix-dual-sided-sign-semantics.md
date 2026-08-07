# 07 — Fix dual-sided sign marker-group semantics

**What to build:** When a sign's front and back text match different marker groups, the marker's generated detail
text is consistent with which group actually owns the marker. Today, the marker's group (icon/type/visibility)
comes from whichever side `getPrefix` picks (front preferred), but `getDetail` merges *both* sides' text regardless
of whether they matched the same group — so a marker can display detail text for a group it doesn't actually belong
to.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Decide and document the intended behavior for a dual-sided sign whose front and back match different marker groups (e.g. only the winning side's text is used, or both are shown but clearly attributed)
- [ ] `getDetail`'s output matches that decision for mixed-group signs
- [ ] Single-group (front and back match the same group, or only one side matches) behavior is unchanged
- [ ] Test coverage for the mixed-group case
