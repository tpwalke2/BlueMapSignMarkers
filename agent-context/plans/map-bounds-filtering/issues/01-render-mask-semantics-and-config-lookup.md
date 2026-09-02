# Research: BlueMap render-mask evaluation semantics and config lookup

Type: research
Status: resolved

## Question

To evaluate whether a sign's position is inside a given BlueMap map's render bounds, BSM needs to
read and interpret that map's `render-mask` config (see
`../../../../run/config/bluemap/maps/world_nether_roof.conf` lines 74-90 for the shape: a list of boxes, each
with optional `min-x/max-x/min-y/max-y/min-z/max-z`, and an optional `subtract: true`). BlueMap's
docs for this are at https://bluemap.bluecolored.de/wiki/customization/Masks.html.

Answer, with citations (doc quotes, or BlueMap source if the wiki is ambiguous):

1. **Combination algorithm**: given a list of mask entries, what is the exact rule for whether a
   point is inside the final render bounds? Is it: default-outside, then a point is inside if
   it's inside at least one non-`subtract` box AND not inside any `subtract` box regardless of
   which non-`subtract` box(es) it also matches? Are entries evaluated in list order (later
   entries override earlier verdicts) or as a flat union/difference? Does an *empty* `render-mask`
   list (or the key entirely absent/commented out, as in most of this repo's other sample configs
   under `../../../../run/config/bluemap/maps`) mean "unbounded" (everything inside)?
2. **Missing axis bounds**: within one box entry, an omitted `min-x`/`max-x`/etc. means unbounded
   on that axis (per the nether-roof sample, the first entry only sets `min-y`, leaving x/z
   unbounded) — confirm this is the correct reading for all six axis bounds, not just y.
3. **Map id ↔ config file**: does a `BlueMapMap.getId()` value returned by the BlueMap API always
   equal the filename stem of its `config/bluemap/maps/<id>.conf` file (e.g. map id
   `world_nether_roof` ↔ `world_nether_roof.conf`)? Check BlueMap's own source/docs if available,
   or reason from how BlueMap loads map configs at startup.
4. **Config directory location**: is `config/bluemap/maps/` (relative to server root) a fixed
   location, or can it be relocated via BlueMap's core config? If relocatable, is the actual path
   discoverable via the BlueMap API at runtime, or would BSM have to assume the default?

This is pure research to inform an implementation plan — do not write any BSM code.

## Answer

Confirmed against BlueMap's actual source (cloned `BlueMap-Minecraft/BlueMap` and
`BlueMap-Minecraft/BlueMapAPI` from GitHub, current `main`), not just the wiki — the wiki text is
correct but vague on the exact algorithm.

### 1. Combination algorithm

Not a symmetric "union of includes minus union of subtracts." It's **last-matching-entry-wins**,
scanned bottom-of-list first, with a default when nothing matches.

`CombinedMask` (`core/.../map/mask/CombinedMask.java`) stores each config-list entry as a
`(mask, value)` layer in list order (`CombinedMaskSerializer` adds them via `node.childrenList()`
in file order, so index 0 = first entry in the config). Its point test:

```java
for (int i = layers.size() - 1; i >= 0; i--) {
    MaskLayer layer = layers.get(i);
    if (!layer.mask.test(x, y, z)) continue;
    return layer.value;   // value = !subtract
}
return layers.isEmpty();
```

So: walk the list from the **last** entry to the **first**; the first box (in that reverse walk —
i.e. the *last*, bottommost entry in the config) that contains the point decides the verdict
outright via its own `subtract` flag. Earlier entries that also cover that point are irrelevant.
This is exactly the wiki's "applied in top-to-bottom order... later masks overrule previous
masks," read literally: order is not cosmetic — two overlapping boxes give a different result
depending which is listed last, even one include + one subtract overlapping each other.

If **no** entry's box contains the point, the verdict is `layers.isEmpty()` — i.e. `false`
(excluded) whenever the render-mask list is non-empty and nothing else says otherwise.

Special case in `MaskConfig.addTo` → `CombinedMask.add`:
```java
public void add(Mask mask, boolean value) {
    if (!value && layers.isEmpty())
        layers.add(new MaskLayer(ALL, true));
    layers.add(new MaskLayer(mask, value));
}
```
If the very *first* entry added is `subtract: true`, an implicit "include everything" layer is
inserted underneath it first. That's what makes the wiki's "secret building" example work:
starting the list with a subtract box means "render everything except this," not "render nothing
except what gets subtracted from nothing."

**Empty/absent `render-mask`**: zero entries → `layers.isEmpty()` is `true` on the no-match path →
every point included. Matches the wiki ("by default BlueMap renders the entire world") and the
map config comment itself ("Default is no min or max value (= infinite bounds)"), and matches
every other sample config under `../../../../run/config/bluemap/maps` that has `render-mask` commented out.

Worked check against `world_nether_roof.conf`'s two entries (list order: `[include min-y:127]`,
`[subtract y 0-126]`):
- `y=200`: only entry 1's box contains it → included.
- `y=50`: entry 1 doesn't contain it (min-y 127 unmet); entry 2 does → subtract → excluded.
- `y=-50`: neither box contains it → no match → excluded (list non-empty, no implicit-ALL case
  since entry 1 isn't a subtract).

### 2. Missing axis bounds

Confirmed for all six axes, not just y. `BoxMaskConfig`
(`common/.../config/mask/BoxMaskConfig.java`):
```java
private int minX = Integer.MIN_VALUE, minY = Integer.MIN_VALUE, minZ = Integer.MIN_VALUE,
            maxX = Integer.MAX_VALUE, maxY = Integer.MAX_VALUE, maxZ = Integer.MAX_VALUE;
```
Each of the six fields independently defaults to `MIN_VALUE`/`MAX_VALUE` when its config key is
omitted — any subset can be set, the rest float to effectively unbounded.

### 3. Map id ↔ config file

Not a literal unconditional identity, but true for any filename made of `[A-Za-z0-9_]` — which is
how BlueMap itself names generated configs and how essentially every real config is named,
including `world_nether_roof.conf`. From `BlueMapConfigManager.loadMapConfigs`
(`common/.../config/BlueMapConfigManager.java`):
```java
String id = sanitiseMapId(configManager.getConfigName(configFile));
...
private String sanitiseMapId(String id) { return id.replaceAll("\\W", "_"); }
```
`getConfigName` strips the file suffix (`.conf`); `sanitiseMapId` then replaces every non-word
character with `_`. So `id == filename stem` exactly whenever the stem has no non-word chars
(spaces, dashes, etc. would get turned into `_` instead of preserved). `loadMapConfigs` also
throws a `ConfigurationException` at BlueMap startup if two files sanitize to the same id, so ids
are always unique within one server's `maps/` folder. Takeaway for BSM: use the filename stem as
the lookup key, but run the same `\W` → `_` sanitization BlueMap does rather than assuming raw
identity, so an oddly-named config file still resolves the same id BlueMap itself computed.

### 4. Config directory location

Fixed for the Fabric platform BSM runs on — not relocatable, and not queryable via the API either
way. `FabricMod.getConfigFolder()` (`implementations/fabric/.../FabricMod.java`):
```java
@Override
public Path getConfigFolder() { return Path.of("config", "bluemap"); }
```
This literal flows straight into `BlueMapConfigManager.builder().configRoot(...)`
(`common/.../plugin/Plugin.java`), and maps live under `<configRoot>/maps/`
(`BlueMapConfigManager.MAPS_CONFIG_FOLDER_NAME = "maps"`) — so `config/bluemap/maps/` relative to
server root, always, for Fabric. (BlueMap's CLI implementation does accept a `--config` flag for
this, but the Fabric mod implementation never wires that up — it's hardcoded.) Separately,
`BlueMapAPI`/`BlueMapMap` (`BlueMapAPI.java`, `BlueMapMap.java` in the `BlueMapAPI` repo, v2.8.0)
expose no config-folder or config-path accessor at all — `BlueMapMap` only has `getId()`, no
`Path`-returning method — so there's nothing to query at runtime even if the path were
relocatable. BSM assuming the literal `config/bluemap/maps/` path is both correct today and the
only option available on this platform.

### Sources

- BlueMap wiki: https://bluemap.bluecolored.de/wiki/customization/Masks.html
- `BlueMap-Minecraft/BlueMap` (github.com/BlueMap-Minecraft/BlueMap, `main`):
  `core/src/main/java/de/bluecolored/bluemap/core/map/mask/{Mask,CombinedMask,BoxMask}.java`;
  `common/src/main/java/de/bluecolored/bluemap/common/config/mask/{MaskConfig,BoxMaskConfig}.java`;
  `common/src/main/java/de/bluecolored/bluemap/common/config/typeserializer/CombinedMaskSerializer.java`;
  `common/src/main/java/de/bluecolored/bluemap/common/config/BlueMapConfigManager.java`;
  `common/src/main/java/de/bluecolored/bluemap/common/config/ConfigManager.java`;
  `implementations/fabric/src/main/java/de/bluecolored/bluemap/fabric/FabricMod.java`;
  `common/src/main/java/de/bluecolored/bluemap/common/plugin/Plugin.java`
- `BlueMap-Minecraft/BlueMapAPI` (github.com/BlueMap-Minecraft/BlueMapAPI):
  `src/main/java/de/bluecolored/bluemap/api/{BlueMapAPI,BlueMapMap}.java`
