package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.config.ConfigManager;
import com.tpwalke2.bluemapsignmarkers.core.WorldMap;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.BlueMapAPIConnector;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.IResetHandler;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.ActionFactory;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.GroupTransitionMarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.MarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.markers.LinePoint;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifierCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

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

    private record RuntimeConfig(Map<String, MarkerGroup> prefixGroupMap, ActionFactory actionFactory) {
    }

    // A sign's marker representation under the current config: null means the sign matches no marker
    // group (NONE); otherwise group.type() says whether it's a POI or a LINE member.
    private record Representation(MarkerGroup group, String label, String detail) {
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
        return new RuntimeConfig(buildPrefixGroupMap(), new ActionFactory(new MarkerSetIdentifierCollection()));
    }

    private static Map<String, MarkerGroup> buildPrefixGroupMap() {
        var groups = ConfigManager.get().getMarkerGroups();
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

    private Representation computeRepresentation(SignEntry entry, Map<String, MarkerGroup> prefixGroupMap) {
        if (entry == null) return null;

        var prefix = SignEntryHelper.getPrefix(entry);
        if (prefix == null) return null;

        var group = prefixGroupMap.get(prefix);
        if (group == null) {
            LOGGER.warn("No marker group configured for prefix {}, skipping: {}", prefix, entry);
            return null;
        }

        return new Representation(group, SignEntryHelper.getLabel(entry), SignEntryHelper.getDetail(entry));
    }

    private static boolean sameGroupAndLabel(Representation a, Representation b) {
        return a.group().prefix().equals(b.group().prefix()) && a.label().equals(b.label());
    }

    // The (oldRepresentation, newRepresentation) transition table from
    // .scratch/line-markers/spec.md §6. Returns null for a no-op, a single MarkerAction when only one
    // effect applies, or a GroupTransitionMarkerAction bundling a leave-effect + join-effect so
    // ReactiveQueue's lack of ordering guarantees can't transiently show a sign in two places.
    private MarkerAction computeTransitionAction(
            List<SignEntry> allSigns,
            SignEntryKey key,
            Representation oldRep,
            Representation newRep,
            ActionFactory actionFactory) {
        if (oldRep == null && newRep == null) return null;

        if (oldRep == null) {
            return newRep.group().type() == MarkerGroupType.POI
                    ? actionFactory.createAddPOIAction(key.x(), key.y(), key.z(), key.parentMap(), newRep.label(), newRep.detail(), newRep.group())
                    : lineJoinAction(allSigns, key.parentMap(), newRep, actionFactory, false);
        }

        if (newRep == null) {
            return oldRep.group().type() == MarkerGroupType.POI
                    ? actionFactory.createRemovePOIAction(key.x(), key.y(), key.z(), key.parentMap(), oldRep.group())
                    : lineLeaveAction(allSigns, key.parentMap(), oldRep, actionFactory);
        }

        var oldType = oldRep.group().type();
        var newType = newRep.group().type();

        if (oldType == MarkerGroupType.POI && newType == MarkerGroupType.POI) {
            if (sameGroupAndLabel(oldRep, newRep)) {
                return oldRep.detail().equals(newRep.detail())
                        ? null
                        : actionFactory.createUpdatePOIAction(key.x(), key.y(), key.z(), key.parentMap(), newRep.label(), newRep.detail(), newRep.group());
            }
            return actionFactory.createChangeGroupPOIAction(key.x(), key.y(), key.z(), key.parentMap(), newRep.label(), newRep.detail(), oldRep.group(), newRep.group());
        }

        if (oldType == MarkerGroupType.LINE && newType == MarkerGroupType.LINE && sameGroupAndLabel(oldRep, newRep)) {
            return oldRep.detail().equals(newRep.detail())
                    ? null
                    : lineJoinAction(allSigns, key.parentMap(), newRep, actionFactory, true);
        }

        var effects = new ArrayList<MarkerAction>(2);

        var leave = oldType == MarkerGroupType.POI
                ? actionFactory.createRemovePOIAction(key.x(), key.y(), key.z(), key.parentMap(), oldRep.group())
                : lineLeaveAction(allSigns, key.parentMap(), oldRep, actionFactory);
        if (leave != null) effects.add(leave);

        var join = newType == MarkerGroupType.POI
                ? actionFactory.createAddPOIAction(key.x(), key.y(), key.z(), key.parentMap(), newRep.label(), newRep.detail(), newRep.group())
                : lineJoinAction(allSigns, key.parentMap(), newRep, actionFactory, false);
        if (join != null) effects.add(join);

        if (effects.isEmpty()) return null;
        if (effects.size() == 1) return effects.get(0);
        return new GroupTransitionMarkerAction(effects);
    }

    // Recomputes a line group including the current sign (it must already be in signCache under this
    // group/label by the time this is called). Dispatches Set once ≥2 members exist; below that the line
    // is still incomplete and nothing is dispatched. sameGroupRecompute forces isFirstAppearance=false,
    // since a same-group/label recompute can only reach ≥2 members if a marker already existed.
    private MarkerAction lineJoinAction(List<SignEntry> allSigns, String parentMap, Representation rep, ActionFactory actionFactory, boolean sameGroupRecompute) {
        var members = LineGroupResolver.members(allSigns, parentMap, rep.group().prefix(), rep.label());
        if (members.size() < 2) return null;

        var isFirstAppearance = !sameGroupRecompute && members.size() == 2;
        return actionFactory.createSetLineAction(parentMap, rep.group(), rep.label(), joinLineDetail(members), toPoints(members), isFirstAppearance);
    }

    // Recomputes a line group excluding the current sign (it must already be removed from/no longer
    // present in signCache under this group/label by the time this is called). Dispatches Set if ≥2
    // members remain, Remove if it drops below 2 and a marker existed before (i.e. exactly 1 remains -
    // meaning there were 2 before), or nothing if there was never a marker to begin with (0 remain).
    private MarkerAction lineLeaveAction(List<SignEntry> allSigns, String parentMap, Representation rep, ActionFactory actionFactory) {
        var members = LineGroupResolver.members(allSigns, parentMap, rep.group().prefix(), rep.label());

        if (members.size() >= 2) {
            return actionFactory.createSetLineAction(parentMap, rep.group(), rep.label(), joinLineDetail(members), toPoints(members), false);
        }

        if (members.size() == 1) {
            return actionFactory.createRemoveLineAction(parentMap, rep.group(), rep.label());
        }

        return null;
    }

    private static List<LinePoint> toPoints(List<SignEntry> members) {
        return members.stream().map(e -> new LinePoint(e.key().x(), e.key().y(), e.key().z())).toList();
    }

    private static String joinLineDetail(List<SignEntry> members) {
        return members.stream().map(SignEntryHelper::getDetail).collect(Collectors.joining(System.lineSeparator()));
    }

    private synchronized void addOrUpdateSign(SignEntry signEntry) {
        var config = runtimeConfig;
        var prefixGroupMap = config.prefixGroupMap();
        var actionFactory = config.actionFactory();

        var key = signEntry.key();
        var existing = signCache.get(key);

        var oldRep = computeRepresentation(existing, prefixGroupMap);
        var newRep = computeRepresentation(signEntry, prefixGroupMap);

        var mergedEntry = existing == null
                ? signEntry
                : new SignEntry(
                        key,
                        WorldMap.UNKNOWN.equals(signEntry.playerId()) ? existing.playerId() : signEntry.playerId(),
                        signEntry.frontText(),
                        signEntry.backText(),
                        existing.createdAtMillis());

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

        var action = computeTransitionAction(getAllSigns(), key, oldRep, newRep, actionFactory);
        if (action != null) {
            LOGGER.debug("Dispatching marker action for {}: {}", key, action);
            blueMapAPIConnector.dispatch(action);
        }
    }

    private synchronized void removeByKey(SignEntryKey key) {
        var removed = signCache.remove(key);

        if (removed == null) {
            LOGGER.debug("No sign found for key: {}", key);
            return;
        }

        chunkIndex.remove(key);

        var config = runtimeConfig;
        var oldRep = computeRepresentation(removed, config.prefixGroupMap());
        var action = computeTransitionAction(getAllSigns(), key, oldRep, null, config.actionFactory());
        if (action != null) {
            LOGGER.debug("Dispatching marker action for {}: {}", key, action);
            blueMapAPIConnector.dispatch(action);
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
            var oldRep = computeRepresentation(entry, oldPrefixGroupMap);
            var newRep = computeRepresentation(entry, newConfig.prefixGroupMap());
            var action = computeTransitionAction(allSigns, entry.key(), oldRep, newRep, newConfig.actionFactory());
            if (action != null) {
                LOGGER.debug("Dispatching marker action for {}: {}", entry.key(), action);
                blueMapAPIConnector.dispatch(action);
            }
        }
    }
}
