package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.config.ConfigManager;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.BlueMapAPIConnector;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.IResetHandler;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.ActionFactory;
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

public class SignManager implements IResetHandler {
    private static SignManager instance;
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

    private void reloadSigns() {
        LOGGER.info("Reloading all signs...");
        var existingSigns = getAllSigns();
        signCache.clear();
        chunkIndex.clear();
        for (SignEntry signEntry : existingSigns) {
            addOrUpdateSign(signEntry);
        }
    }

    private void addOrUpdateSign(SignEntry signEntry) {
        var config = runtimeConfig;
        var prefixGroupMap = config.prefixGroupMap();
        var actionFactory = config.actionFactory();

        var key = signEntry.key();
        var existing = signCache.get(key);

        var isPOIMarker = SignEntryHelper.isMarkerType(signEntry, prefixGroupMap, MarkerGroupType.POI);
        var label = SignEntryHelper.getLabel(signEntry);
        var detail = SignEntryHelper.getDetail(signEntry);
        var newPrefix = SignEntryHelper.getPrefix(signEntry);

        if (newPrefix == null) {
            if (existing != null) {
                LOGGER.debug("Removing POI marker as sign no longer has a prefix: {}", signEntry);
                removeEntry(signEntry);
            } else {
                LOGGER.debug("Cannot add or update sign as no prefix found: {}", signEntry);
            }
            return;
        }

        if (shouldAddPOIMarker(existing, isPOIMarker)) {
            var newGroup = prefixGroupMap.get(newPrefix);
            if (newGroup == null) {
                LOGGER.warn("No marker group configured for prefix {}, skipping add: {}", newPrefix, signEntry);
                return;
            }

            LOGGER.debug("Adding POI marker: {}", signEntry);
            signCache.put(key, signEntry);
            chunkIndex.add(key);
            blueMapAPIConnector.dispatch(
                    actionFactory.createAddPOIAction(
                            key.x(),
                            key.y(),
                            key.z(),
                            key.parentMap(),
                            label,
                            detail,
                            newGroup));
            return;
        }

        if (shouldRemovePOIMarker(existing, isPOIMarker)) {
            LOGGER.debug("Removing POI marker: {}", signEntry);
            removeEntry(signEntry);
        }

        if (shouldUpdatePOIMarker(existing, isPOIMarker)) {
            var existingPrefix = SignEntryHelper.getPrefix(existing);

            LOGGER.debug("Updating POI marker: {}", signEntry);
            signCache.put(
                    key,
                    signEntry.playerId().equals("unknown")
                            ? new SignEntry(
                                    key,
                                    existing.playerId(),
                                    signEntry.frontText(),
                                    signEntry.backText())
                            : signEntry);

            if (existingPrefix.equals(newPrefix)) {
                var existingLabel = SignEntryHelper.getLabel(existing);
                var existingDetail = SignEntryHelper.getDetail(existing);

                if (isTextDifferent(existingLabel, label, existingDetail, detail)) {
                    var group = prefixGroupMap.get(newPrefix);
                    if (group == null) {
                        LOGGER.warn("No marker group configured for prefix {}, skipping update: {}", newPrefix, signEntry);
                    } else {
                        LOGGER.debug("Updating POI marker label and detail");
                        blueMapAPIConnector.dispatch(
                                actionFactory.createUpdatePOIAction(
                                        key.x(),
                                        key.y(),
                                        key.z(),
                                        key.parentMap(),
                                        label,
                                        detail,
                                        group));
                    }
                }
            } else {
                var existingGroup = prefixGroupMap.get(existingPrefix);
                if (existingGroup == null) {
                    LOGGER.warn("No marker group configured for previous prefix {}, skipping remove: {}", existingPrefix, signEntry);
                } else {
                    blueMapAPIConnector.dispatch(
                            actionFactory.createRemovePOIAction(
                                    key.x(),
                                    key.y(),
                                    key.z(),
                                    key.parentMap(),
                                    existingGroup));
                }

                var newGroup = prefixGroupMap.get(newPrefix);
                if (newGroup == null) {
                    LOGGER.warn("No marker group configured for prefix {}, skipping add: {}", newPrefix, signEntry);
                } else {
                    blueMapAPIConnector.dispatch(
                            actionFactory.createAddPOIAction(
                                    key.x(),
                                    key.y(),
                                    key.z(),
                                    key.parentMap(),
                                    label,
                                    detail,
                                    newGroup));
                }
            }
        }
    }

    private static boolean isTextDifferent(String existingLabel, String label, String existingDetail, String detail) {
        return !existingLabel.equals(label) || !existingDetail.equals(detail);
    }

    private static boolean shouldUpdatePOIMarker(SignEntry existing, boolean isPOIMarker) {
        return existing != null && isPOIMarker;
    }

    private static boolean shouldRemovePOIMarker(SignEntry existing, boolean isPOIMarker) {
        return existing != null && !isPOIMarker;
    }

    private static boolean shouldAddPOIMarker(SignEntry existing, boolean isPOIMarker) {
        return existing == null && isPOIMarker;
    }

    private void removeByKey(SignEntryKey key) {
        var removed = signCache.remove(key);

        if (removed == null) {
            LOGGER.debug("No sign found for key: {}", key);
            return;
        }

        chunkIndex.remove(key);

        var prefix = SignEntryHelper.getPrefix(removed);

        if (prefix == null) {
            LOGGER.debug("Cannot remove sign as no prefix found: {}", removed);
            return;
        }

        var config = runtimeConfig;
        var group = config.prefixGroupMap().get(prefix);

        if (group == null) {
            LOGGER.warn("No marker group configured for prefix {}, skipping remove dispatch: {}", prefix, removed);
            return;
        }

        blueMapAPIConnector.dispatch(
                config.actionFactory().createRemovePOIAction(
                        key.x(),
                        key.y(),
                        key.z(),
                        key.parentMap(),
                        group));
    }

    private void removeEntry(SignEntry signEntry) {
        removeByKey(signEntry.key());
    }

    @Override
    public void reset() {
        reloadConfig();
        reloadSigns();
    }

    private void reloadConfig() {
        LOGGER.info("Reloading marker group configuration...");
        ConfigManager.reload();
        SignHelper.reloadParser();
        runtimeConfig = buildRuntimeConfig();
    }
}
