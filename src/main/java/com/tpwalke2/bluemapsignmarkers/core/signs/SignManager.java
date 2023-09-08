package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.BlueMapAPIConnector;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.ActionFactory;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerSetIdentifierCollection;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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

    private final BlueMapAPIConnector blueMapAPIConnector;
    private final ActionFactory actionFactory;
    private final ConcurrentMap<SignEntryKey, SignEntry> signCache = new ConcurrentHashMap<>();

    private SignManager() {
        MarkerSetIdentifierCollection markerSetIdentifierCollection = new MarkerSetIdentifierCollection();
        blueMapAPIConnector = new BlueMapAPIConnector(markerSetIdentifierCollection);
        actionFactory = new ActionFactory(markerSetIdentifierCollection);
    }

    private List<SignEntry> getAllSigns() {
        return new ArrayList<>(signCache.values());
    }

    private void shutdown() {
        blueMapAPIConnector.shutdown();
    }

    private void addOrUpdateSign(SignEntry signEntry) {
        var key = signEntry.key();
        var existing = signCache.get(key);

        var isPOIMarker = SignEntryHelper.isMarkerType(signEntry, MarkerType.POI);
        var label = SignEntryHelper.getLabel(signEntry);
        var detail = SignEntryHelper.getDetail(signEntry);

        if (existing == null && isPOIMarker) {
            LOGGER.info("Adding POI marker: {}", signEntry);
            signCache.put(key, signEntry);
            blueMapAPIConnector.dispatch(
                    actionFactory.createAddPOIAction(
                            key.x(),
                            key.y(),
                            key.z(),
                            key.parentMap(),
                            label,
                            detail));
            return;
        }

        if (existing != null && !isPOIMarker) {
            LOGGER.info("Removing POI marker: {}", signEntry);
            removeEntry(signEntry);
        }

        if (existing != null && isPOIMarker) {
            LOGGER.info("Updating POI marker: {}", signEntry);
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
                            detail));
        }
    }

    private void removeByKey(SignEntryKey key) {
        signCache.remove(key);

        blueMapAPIConnector.dispatch(
                actionFactory.createRemovePOIAction(
                        key.x(),
                        key.y(),
                        key.z(),
                        key.parentMap()));
    }

    private void removeEntry(SignEntry signEntry) {
        removeByKey(signEntry.key());
    }
}
