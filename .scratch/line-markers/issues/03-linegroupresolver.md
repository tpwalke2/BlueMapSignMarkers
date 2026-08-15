# 03 — `LineGroupResolver`: grouping key and membership resolution

**Spec:** `.scratch/line-markers/spec.md` §3 ("Grouping key and membership resolution")

**Blocked by:** 02 (needs `SignEntry.createdAtMillis`)

**What to build:**
New pure-Java, unit-testable `core/signs/LineGroupResolver.java`:

```java
public static List<SignEntry> members(Collection<SignEntry> allSigns, String parentMap, String prefix, String label) {
    return allSigns.stream()
        .filter(e -> parentMap.equals(e.key().parentMap()))
        .filter(e -> prefix.equals(SignEntryHelper.getPrefix(e)))
        .filter(e -> label.equals(SignEntryHelper.getLabel(e)))
        .sorted(Comparator.comparingLong(SignEntry::createdAtMillis)
                .thenComparingInt(e -> e.key().x())
                .thenComparingInt(e -> e.key().y())
                .thenComparingInt(e -> e.key().z()))
        .toList();
}
```

Takes a plain `Collection<SignEntry>` (not `SignManager`'s cache type) — no Minecraft/BlueMap types in its
signature, per `AGENTS.md`'s testable-core convention. A line's key is `(parentMap, prefix, label)` — two signs
with the same prefix+label in different dimensions are different lines (already scoped by `parentMap`).

**Status:** done

- [x] `LineGroupResolver.members(...)` implemented as above
- [x] New test `src/test/java/.../core/signs/LineGroupResolverTest.java` covers: membership scanning across
      dimensions/prefixes/labels, ordering by `createdAtMillis`, tie-breaking on duplicate `createdAtMillis`
      (per ticket 02's cross-file-migration note)
- [x] `./gradlew test` and `./gradlew build` pass
