Type: research
Status: resolved

## Question

The colour-conflict rule (map Notes) re-evaluates a marker's colour "on every change," matching how
`SignTransitionResolver` already recomputes a sign's representation on edit/removal/reload. That existing
recompute is driven by two mixins (`SignBlockEntityInject` on `updateSignText`, `AbstractBlockInject` on
`affectNeighborsAfterRemoval`) plus `BLOCK_ENTITY_LOAD`/`CHUNK_LOAD` hooks — none of which fire on a player
dyeing an already-placed sign (dyeing doesn't call `updateSignText`, isn't a block removal, and doesn't reload
the chunk).

Find, for the Minecraft/Fabric version pinned in `gradle.properties`:

1. What method/event fires when a player dyes a placed sign (right-clicks with a dye item) — is there a
   Fabric API event, or does it require a new mixin target? Name the exact class/method.
2. Confirm `net.minecraft.world.level.block.entity.SignBlockEntity`/`SignText` exposes the sign's current
   `DyeColor` and `hasGlowingText` readably (not just at placement) — same object `SignHelper.getRawLines`
   already reads lines from.
3. Confirm `net.minecraft.world.item.DyeColor` (or equivalent) exposes a usable RGB value per dye (e.g.
   `getTextureDiffuseColors()`, `getFireworkColor()`, or similar) accessible from this mod's Fabric dependency
   set — not a client-rendering-only API.

Report the exact hook point(s) needed and the RGB source to use, so ticket 03 (recompute-trigger design) isn't
guessing at feasibility.

## Answer

Full research: `research-dye-glow-event-hooks.md` on branch `research/dye-glow-event-hooks` (not on `main` —
checkout that branch to read it).

1. Dye, ink sac, and glow ink sac all reach the sign through
   `net.minecraft.world.level.block.SignBlock.useItemOn` → the item's `SignApplicator.tryApplyToSign` →
   `SignBlockEntity.updateText(UnaryOperator<SignText>, boolean)` — the same method `updateSignText` itself
   calls internally, so it's the one true funnel for every sign-content mutation (text edit, dye, glow,
   un-glow). No Fabric API event covers this without duplicating vanilla's own "did this actually change"
   check. Needed: a new mixin, `@Inject` at `RETURN` of `SignBlockEntity.updateText`, gated on a `true`
   return value — this subsumes the existing `updateSignText`-only mixin.
2. Confirmed: `SignText.getColor()` (`DyeColor`, default `BLACK`) and `SignText.hasGlowingText()` (`boolean`)
   are readable at any time off `SignBlockEntity.getFrontText()`/`getBackText()`, the same object
   `SignHelper.getRawLines` reads lines from.
3. Confirmed: `DyeColor.getTextureDiffuseColor()` returns a server-safe opaque ARGB `int` per dye — the class
   lives in `net.minecraft.world.item` with no client/rendering imports, so it's usable from this mod's
   `DedicatedServerModInitializer` context.
