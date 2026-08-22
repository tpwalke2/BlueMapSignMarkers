# 08 — How should RenderMaskEvaluator handle circle/ellipse/polygon mask entries?

Type: grilling
Status: resolved

## Question

Given the confirmed shape types in `.scratch/map-bounds-filtering/issues/07-non-box-render-mask-types.md`
(box/circle/ellipse/polygon, mixable in one `render-mask` list, order-sensitive per ticket 01),
how should `RenderMaskEvaluator` handle a `circle`/`ellipse`/`polygon` entry — implement real
point-in-shape geometry for all four types, or some fail-open/skip behavior for the non-box
three? This blocks finishing ticket 05 correctly (today's parser silently mis-evaluates non-box
entries as unbounded-on-XZ box entries — a correctness bug, not a graceful fallback).

**Blocked by:** None (07 resolved)

## Answer

Implement all four shapes properly (box, circle, ellipse, polygon) rather than failing open or
skipping non-box types — matches BlueMap's actual behavior for any `render-mask` a server writes.
Ticket 05's scope and acceptance criteria are updated in place to cover all four; see that ticket
and `agent-context/plans/map-bounds-filtering-plan.md`'s "New module: render-mask evaluator"
section for the resulting design (a `type`-dispatching parser plus one `contains(x, y, z)` shape
record per type). An unrecognized `type` value still fails open for that one map, consistent with
this module's existing fail-open contract for missing/unreadable/unparseable configs.
