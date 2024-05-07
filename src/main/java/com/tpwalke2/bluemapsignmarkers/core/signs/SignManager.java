package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.config.ConfigManager;
import com.tpwalke2.bluemapsignmarkers.core.BlockPosition;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.BlueMapAPIConnector;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.ActionFactory;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifierCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SignManager {
    private static final SignManager INSTANCE = new SignManager();
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    public static void addOrUpdate(SignEntry signEntry) {
        INSTANCE.addOrUpdateSign(signEntry);
    }

    public static void remove(SignEntryKey key) {
        INSTANCE.removeByKey(key);
    }

    public static List<SignEntry> getAll() {
        return INSTANCE.getAllSigns();
    }

    public static void stop() {
        INSTANCE.shutdown();
    }

    public static void reload() {
        INSTANCE.reloadSigns();
    }

    private final BlueMapAPIConnector blueMapAPIConnector;
    private final SignMarkerReducer signMarkerReducer;
    private final SignState signState = new SignState();

    private SignManager() {
        var groups = ConfigManager.get().getMarkerGroups();
        //private final ConcurrentMap<SignEntryKey, SignEntry> signCache = new ConcurrentHashMap<>();
        //private final SignGroups lineSignsCache = new SignGroups();
        var prefixGroupMap = new TreeMap<String, MarkerGroup>();
        for (var group : groups) {
            if (prefixGroupMap.containsKey(group.prefix())) {
                LOGGER.warn("Duplicate marker group prefix found: {}", group.prefix());
                continue;
            }

            prefixGroupMap.put(group.prefix(), group);
        }

        blueMapAPIConnector = new BlueMapAPIConnector();
        ActionFactory actionFactory = new ActionFactory(new MarkerSetIdentifierCollection());
        signMarkerReducer = new SignMarkerReducer(actionFactory, prefixGroupMap);
    }

    private List<SignEntry> getAllSigns() {
        return signState.getAllSigns();
    }

    private void shutdown() {
        blueMapAPIConnector.shutdown();
    }

    private void reloadSigns() {
        LOGGER.info("Reloading all signs...");
        var existingSigns = getAllSigns();
        signState.clear();
        for (SignEntry signEntry : existingSigns) {
            addOrUpdateSign(signEntry);
        }
    }

    private void addOrUpdateSign(SignEntry signEntry) {
        signMarkerReducer.reduce(
                        signEntry,
                        SignMarkerOperation.UPSERT,
                        signState)
                .forEach(blueMapAPIConnector::dispatch);
        /*var key = signEntry.key();
        var existing = signCache.get(key);

        var label = SignEntryHelper.getLabel(signEntry);
        var detail = SignEntryHelper.getDetail(signEntry);
        var prefix = SignEntryHelper.getPrefix(signEntry);

        if (prefix == null) {
            LOGGER.debug("Cannot add or update sign as no prefix found: {}", signEntry);
            return;
        }

        processPOISigns(
                signEntry,
                existing,
                SignEntryHelper.isMarkerType(signEntry, prefixGroupMap, MarkerGroupType.POI),
                key,
                label,
                detail,
                prefix);

        processLineSigns(
                signEntry,
                existing,
                SignEntryHelper.isMarkerType(signEntry, prefixGroupMap, MarkerGroupType.LINE),
                key,
                label,
                prefix);*/
    }

    /*private void processLineSigns(SignEntry signEntry,
                                  SignEntry existing,
                                  boolean isLineMarker,
                                  SignEntryKey key,
                                  String label,
                                  String prefix) {
        var groupKey = new SignGroupKey(key.parentMap(), prefixGroupMap.get(prefix), label);

        if (existing == null && isLineMarker) {
            LOGGER.debug("Adding line marker: {}", signEntry);
            lineSignsCache.addOrUpdateSign(
                    groupKey,
                    signEntry);
            signCache.put(key, signEntry);

            // TODO remove existing line
            var lineSigns = lineSignsCache.getSigns(groupKey);

            blueMapAPIConnector.dispatch(
                    actionFactory.createAddLineAction(
                            key.x(),
                            key.y(),
                            key.z(),
                            key.parentMap(),
                            label,
                            prefixGroupMap.get(prefix),
                            lineSigns.stream()
                                    .map(s -> new BlockPosition(s.key().x(), s.key().y(), s.key().z()))));

            return;
        }

        if (existing != null && !isLineMarker) {
            LOGGER.debug("Removing line marker: {}", signEntry);
            lineSignsCache.removeSign(signEntry);
            signCache.remove(key);

            // TODO remove existing line
            var lineSigns = lineSignsCache.getSigns(groupKey);

            blueMapAPIConnector.dispatch(
                    actionFactory.createAddLineAction(
                            key.x(),
                            key.y(),
                            key.z(),
                            key.parentMap(),
                            label,
                            prefixGroupMap.get(prefix),
                            lineSigns.stream()
                                    .map(s -> new BlockPosition(s.key().x(), s.key().y(), s.key().z()))));
        }

        if (existing != null && isLineMarker) {
            LOGGER.debug("Updating line marker: {}", signEntry);
            lineSignsCache.addOrUpdateSign(
                    groupKey,
                    signEntry);
            signCache.put(key, signEntry);

            // TODO remove existing line
            var lineSigns = lineSignsCache.getSigns(groupKey);

            blueMapAPIConnector.dispatch(
                    actionFactory.createAddLineAction(
                            key.x(),
                            key.y(),
                            key.z(),
                            key.parentMap(),
                            label,
                            prefixGroupMap.get(prefix),
                            lineSigns.stream()
                                    .map(s -> new BlockPosition(s.key().x(), s.key().y(), s.key().z()))));
        }
    }

    private void processPOISigns(SignEntry signEntry, SignEntry existing, boolean isPOIMarker, SignEntryKey key, String label, String detail, String prefix) {
        if (existing == null && isPOIMarker) {
            LOGGER.debug("Adding POI marker: {}", signEntry);
            signCache.put(key, signEntry);
            blueMapAPIConnector.dispatch(
                    actionFactory.createAddPOIAction(
                            key.x(),
                            key.y(),
                            key.z(),
                            key.parentMap(),
                            label,
                            detail,
                            prefixGroupMap.get(prefix)));
            return;
        }

        if (existing != null && !isPOIMarker) {
            LOGGER.debug("Removing POI marker: {}", signEntry);
            removeEntry(signEntry);
        }

        if (existing != null && isPOIMarker) {
            LOGGER.debug("Updating POI marker: {}", signEntry);
            if (signEntry.playerId().equals("unknown")) {
                signCache.put(key, new SignEntry(
                        key,
                        existing.playerId(),
                        signEntry.frontText(),
                        signEntry.backText()));
            } else {
                signCache.put(key, signEntry);
            }

            blueMapAPIConnector.dispatch(
                    actionFactory.createUpdatePOIAction(
                            key.x(),
                            key.y(),
                            key.z(),
                            key.parentMap(),
                            label,
                            detail,
                            prefixGroupMap.get(prefix)));
        }
    }*/

    private void removeByKey(SignEntryKey key) {
        var removed = signState.getSign(key);

        if (removed.isEmpty()) {
            LOGGER.debug("No sign found for key: {}", key);
            return;
        }

        signMarkerReducer.reduce(
                        removed.get(),
                        SignMarkerOperation.REMOVE,
                        signState)
                .forEach(blueMapAPIConnector::dispatch);

        /*var prefix = SignEntryHelper.getPrefix(removed);

        if (prefix == null) {
            LOGGER.debug("Cannot remove sign as no prefix found: {}", removed);
            return;
        }

        blueMapAPIConnector.dispatch(
                actionFactory.createRemoveMarkerAction(
                        key.x(),
                        key.y(),
                        key.z(),
                        key.parentMap(),
                        prefixGroupMap.get(prefix)));*/
    }

    /*
    private void removeEntry(SignEntry signEntry) {
        removeByKey(signEntry.key());
    }
    */
}
