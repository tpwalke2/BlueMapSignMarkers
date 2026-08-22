package com.tpwalke2.bluemapsignmarkers.core.bounds;

// One entry of a render-mask list: a shape plus whether it subtracts from (rather than adds to)
// the render bounds. RenderMaskEvaluator's combination algorithm is shape-agnostic - it only ever
// calls contains()/subtract() on the last-matching entry, regardless of which shape it is.
interface RenderMaskShape {
    boolean contains(int x, int y, int z);

    boolean subtract();
}
