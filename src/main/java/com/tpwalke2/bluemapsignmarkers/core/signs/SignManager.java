package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.config.ConfigManager;
import com.tpwalke2.bluemapsignmarkers.core.WorldMap;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.BlueMapAPIConnector;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.IResetHandler;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.ActionFactory;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifierCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class SignManager implements IResetHandler {
    private static volatile SignManager instance;
    private static final Object mutex = new Object();

    private static SignManager getInstance() {
        SignManager result = instance;
        if (result == null) {
            synchronized (mutex) {
                result = instance;
                if (result == null) {
                    instance = result = new SignManager();
                }
            }
        }
        return result;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    public static void addOrUpdate(SignEntry signEntry) {
        getInstance().addOrUpdateSign(signEntry);
    }

    public static void remove(SignEntryKey key) {
        getInstance().removeByKey(key);
    }

    public static List<SignEntry> getAll() {
        return getInstance().getAllSigns();
    }

    public static void stop() {
        getInstance().shutdown();
    }

    public static List<SignEntryKey> getKeysInChunk(String parentMap, int chunkX, int chunkZ) {
        return getInstance().chunkIndex.keysInChunk(parentMap, chunkX, chunkZ);
    }

    private record RuntimeConfig(
            Map<String, MarkerGroup> prefixGroupMap, ActionFactory actionFactory, SignLinesParser parser) {
    }

    private final BlueMapAPIConnector blueMapAPIConnector;
    private final ConcurrentMap<SignEntryKey, SignEntry> signCache = new ConcurrentHashMap<>();
    private final SignChunkIndex chunkIndex = new SignChunkIndex();
    private volatile RuntimeConfig runtimeConfig;

    private SignManager() {
        runtimeConfig = buildRuntimeConfig();
        blueMapAPIConnector = new BlueMapAPIConnector();
        blueMapAPIConnector.addResetHandler(this);
    }

    private static RuntimeConfig buildRuntimeConfig() {
        var groups = ConfigManager.get().getMarkerGroups();
        return new RuntimeConfig(
                buildPrefixGroupMap(groups),
                new ActionFactory(new MarkerSetIdentifierCollection()),
                new SignLinesParser(Arrays.asList(groups)));
    }

    private static Map<String, MarkerGroup> buildPrefixGroupMap(MarkerGroup[] groups) {
        Map<String, MarkerGroup> result = new TreeMap<>();
        for (var group : groups) {
            if (result.containsKey(group.prefix())) {
                LOGGER.warn("Duplicate marker group prefix found: {}", group.prefix());
                continue;
            }

            result.put(group.prefix(), group);
        }
        return result;
    }

    private List<SignEntry> getAllSigns() {
        return new ArrayList<>(signCache.values());
    }

    private void shutdown() {
        blueMapAPIConnector.shutdown();
    }

    private synchronized void addOrUpdateSign(SignEntry signEntry) {
        var config = runtimeConfig;
        var prefixGroupMap = config.prefixGroupMap();
        var actionFactory = config.actionFactory();

        var key = signEntry.key();
        var existing = signCache.get(key);

        var oldRep = SignTransitionResolver.computeRepresentation(existing, prefixGroupMap);
        var newRep = SignTransitionResolver.computeRepresentation(signEntry, prefixGroupMap);

        var mergedEntry = existing == null
                ? signEntry
                : new SignEntry(
                        key,
                        WorldMap.UNKNOWN.equals(signEntry.playerId()) ? existing.playerId() : signEntry.playerId(),
                        signEntry.frontText(),
                        signEntry.backText(),
                        existing.createdAtMillis(),
                        signEntry.frontRawLines(),
                        signEntry.backRawLines());

        if (newRep == null) {
            if (existing != null) {
                signCache.remove(key);
                chunkIndex.remove(key);
            }
        } else {
            signCache.put(key, mergedEntry);
            if (existing == null) {
                chunkIndex.add(key);
            }
        }

        dispatchTransition(this::getAllSigns, key, oldRep, newRep, actionFactory, false);
    }

    private synchronized void removeByKey(SignEntryKey key) {
        var removed = signCache.remove(key);

        if (removed == null) {
            LOGGER.debug("No sign found for key: {}", key);
            return;
        }

        chunkIndex.remove(key);

        var config = runtimeConfig;
        var oldRep = SignTransitionResolver.computeRepresentation(removed, config.prefixGroupMap());
        dispatchTransition(this::getAllSigns, key, oldRep, null, config.actionFactory(), false);
    }

    // Both call sites (live sign edits/removals from the mixins, and the reload loop below) run on their
    // own thread with no caller-side try/catch: a mixin injection or the CHUNK_LOAD handler propagating an
    // uncaught exception straight into live Minecraft server code would violate this mod's "never crash the
    // server" rule (AGENTS.md), so failures computing/dispatching a single sign's transition are caught,
    // logged, and skipped rather than allowed to escape.
    private void dispatchTransition(
            Supplier<List<SignEntry>> allSignsSupplier,
            SignEntryKey key,
            SignTransitionResolver.Representation oldRep,
            SignTransitionResolver.Representation newRep,
            ActionFactory actionFactory,
            boolean isReload) {
        try {
            var action = SignTransitionResolver.computeTransitionAction(allSignsSupplier, key, oldRep, newRep, actionFactory, isReload);
            if (action != null) {
                LOGGER.debug("Dispatching marker action for {}: {}", key, action);
                blueMapAPIConnector.dispatch(action);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to compute/dispatch marker transition for {}; skipping.", key, e);
        }
    }

    @Override
    public void reset() {
        reloadConfig();
    }

    // synchronized (same monitor as addOrUpdateSign/removeByKey above) so the whole swap-config-then-diff
    // sequence is one atomic step relative to live sign edits/removals arriving from the mixins on the
    // server thread. IResetHandler.reset() fires on whatever thread BlueMapAPI.onEnable runs on, not
    // necessarily the server thread, so without this a live edit could land mid-diff and get clobbered by
    // a stale dispatch, or a sign removed mid-diff could be silently re-added. dispatch() only enqueues
    // onto ReactiveQueue (no blocking BlueMap API work happens under this lock), so this doesn't introduce
    // hot-path contention the way locking around processMarkerAction would.
    //
    // signCache/chunkIndex are deliberately NOT cleared here (unlike a naive clear-and-replay): a marker's
    // id can be content-keyed (a LINE marker's id is "line:" + label) rather than position-keyed, so a
    // config change that flips a group's type between POI and LINE would otherwise leave the old id's
    // marker orphaned in BlueMap's MarkerSet forever, since a replayed add only ever adds under the new id
    // and nothing ever explicitly removes the old one. Diffing each sign's representation under the old vs.
    // new config and running that pair through the same transition table as a live edit dispatches an
    // explicit leave-effect for the old representation whenever it differs, so no id is ever left behind.
    private synchronized void reloadConfig() {
        LOGGER.info("Reloading marker group configuration...");
        var oldPrefixGroupMap = runtimeConfig.prefixGroupMap();

        ConfigManager.reload();
        SignHelper.reloadParser();
        runtimeConfig = buildRuntimeConfig();
        blueMapAPIConnector.clearMarkerSetsCache();

        var newConfig = runtimeConfig;
        var allSigns = getAllSigns();
        for (SignEntry entry : allSigns) {
            var oldRep = SignTransitionResolver.computeRepresentation(entry, oldPrefixGroupMap);

            // Self-heal a sign whose representation drifted from its cached parse (e.g. a REGEX group's
            // prefix text was edited) by reparsing from the raw sign text under the new config, rather
            // than trusting the stale cached SignLinesParseResult as an identity key - see
            // agent-context/plans/stale-prefix-orphaned-signs-fix.md. Entries with no raw text (migrated
            // pre-V5) fall back to today's behavior: diff the cached parse as-is.
            var reparsed = reparseFromRawLines(entry, newConfig.parser());
            if (reparsed != entry) {
                signCache.put(reparsed.key(), reparsed);
                entry = reparsed;
            }

            var newRep = SignTransitionResolver.computeRepresentation(entry, newConfig.prefixGroupMap());
            dispatchTransition(() -> allSigns, entry.key(), oldRep, newRep, newConfig.actionFactory(), true);
        }
    }

    // Extracted as a static, game-type-free method so it's directly unit testable (SignManager itself
    // can't be - its constructor touches live BlueMapAPI static state, see AGENTS.md). Returns entry
    // unchanged (same reference) when raw text isn't available, so callers can cheaply detect a no-op.
    static SignEntry reparseFromRawLines(SignEntry entry, SignLinesParser parser) {
        if (entry.frontRawLines() == null || entry.backRawLines() == null) {
            return entry;
        }

        var freshFront = parser.parse(entry.frontRawLines());
        var freshBack = parser.parse(entry.backRawLines());
        return entry.withParsedText(freshFront, freshBack);
    }
}
