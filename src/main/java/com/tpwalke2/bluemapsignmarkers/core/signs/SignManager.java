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

    public static void reload() {
        getInstance().reloadSigns();
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
        var prefix = SignEntryHelper.getPrefix(signEntry);

        if (prefix == null) {
            LOGGER.debug("Cannot add or update sign as no prefix found: {}", signEntry);
            return;
        }

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
    }

    private void removeByKey(SignEntryKey key) {
        var removed = signCache.remove(key);

        if (removed == null) {
            LOGGER.debug("No sign found for key: {}", key);
            return;
        }

        var prefix = SignEntryHelper.getPrefix(removed);

        if (prefix == null) {
            LOGGER.debug("Cannot remove sign as no prefix found: {}", removed);
            return;
        }

        blueMapAPIConnector.dispatch(
                actionFactory.createRemovePOIAction(
                        key.x(),
                        key.y(),
                        key.z(),
                        key.parentMap(),
                        prefixGroupMap.get(prefix)));
    }

    private void removeEntry(SignEntry signEntry) {
        removeByKey(signEntry.key());
    }
}
