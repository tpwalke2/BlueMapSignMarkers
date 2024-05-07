package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.config.ConfigManager;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.BlueMapAPIConnector;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.ActionFactory;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifierCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
    }

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
    }
}
