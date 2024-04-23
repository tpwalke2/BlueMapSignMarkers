package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.config.ConfigManager;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.BlueMapAPIConnector;
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
    private final ActionFactory actionFactory;
    private final ConcurrentMap<SignEntryKey, SignEntry> signCache = new ConcurrentHashMap<>();
    private final Map<String, MarkerGroup> prefixGroupMap;

    private SignManager() {
        var groups = ConfigManager.get().getMarkerGroups();
        prefixGroupMap = new TreeMap<>();
        for (var group : groups) {
            if (prefixGroupMap.containsKey(group.prefix())) {
                LOGGER.warn("Duplicate marker group prefix found: {}", group.prefix());
                continue;
            }

            prefixGroupMap.put(group.prefix(), group);
        }

        MarkerSetIdentifierCollection markerSetIdentifierCollection = new MarkerSetIdentifierCollection();
        blueMapAPIConnector = new BlueMapAPIConnector();
        actionFactory = new ActionFactory(markerSetIdentifierCollection);
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
        for (SignEntry signEntry : existingSigns) {
            addOrUpdateSign(signEntry);
        }
    }

    private void addOrUpdateSign(SignEntry signEntry) {
        var key = signEntry.key();
        var existing = signCache.get(key);

        var isPOIMarker = SignEntryHelper.isMarkerType(signEntry, prefixGroupMap, MarkerGroupType.POI);
        var label = SignEntryHelper.getLabel(signEntry);
        var detail = SignEntryHelper.getDetail(signEntry);

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
                            prefixGroupMap.get(signEntry.frontText().prefix())));
            // TODO handle different back text marker groups
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
                            prefixGroupMap.get(signEntry.frontText().prefix())));
            // TODO handle different back text marker groups
        }
    }

    private void removeByKey(SignEntryKey key) {
        var removed = signCache.remove(key);

        if (removed == null) {
            LOGGER.debug("No sign found for key: {}", key);
            return;
        }

        blueMapAPIConnector.dispatch(
                actionFactory.createRemovePOIAction(
                        key.x(),
                        key.y(),
                        key.z(),
                        key.parentMap(),
                        prefixGroupMap.get(removed.frontText().prefix())));
        // TODO handle different back text marker groups
    }

    private void removeEntry(SignEntry signEntry) {
        removeByKey(signEntry.key());
    }
}
