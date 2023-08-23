package com.tpwalke2.bluemapsignmarkers.core;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.BlueMapAPIConnector;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.MarkerMap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SignManager {
    private static SignManager INSTANCE;
    public static SignManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SignManager();
        }

        return INSTANCE;
    }

    private final BlueMapAPIConnector blueMapAPIConnector;

    private SignManager() {
        blueMapAPIConnector = new BlueMapAPIConnector();
    }

    public static void addOrUpdate(SignEntry signEntry) {
        getInstance().addOrUpdateSign(signEntry);
    }

    private static final String POI_MARKER_SENTINEL = "[map]";
    private static final String POI_MARKER_SET_ID = "points_of_interest";
    private static final String POI_MARKER_SET_LABEL = "Points of Interest";
    private static final String POI_MARKER_SET_LAYER = "POI";

    private final ConcurrentMap<SignEntryKey, SignEntry> signCache = new ConcurrentHashMap<>();

    public List<SignEntry> getAllSigns() {
        return new ArrayList<>(signCache.values());
    }

    public void shutdown() {
        blueMapAPIConnector.shutdown();
    }

    public void addOrUpdateSign(SignEntry signEntry) {
        var key = signEntry.getKey();
        var existing = signCache.get(key);
        var isPOIMarker = isPOIMarker(signEntry);

        // TODO add method for creating label and detail from sign text
        var label = "";
        var detail = "";
        // TODO convert between map types
        var map = MarkerMap.UNKNOWN;

        if (existing == null && isPOIMarker) {
            signCache.put(key, signEntry);
            blueMapAPIConnector.addPOIMarker(
                    signEntry.x(),
                    signEntry.y(),
                    signEntry.z(),
                    label,
                    detail,
                    map);
            return;
        }

        if (existing != null && !isPOIMarker) {
            removeSign(signEntry);
            blueMapAPIConnector.removePOIMarker(
                    signEntry.x(),
                    signEntry.y(),
                    signEntry.z(),
                    map);
        }

        if (existing != null && isPOIMarker) {
            signCache.put(key, signEntry);
            blueMapAPIConnector.updatePOIMarker(
                    signEntry.x(),
                    signEntry.y(),
                    signEntry.z(),
                    label,
                    detail,
                    map);
        }
    }

    private boolean isPOIMarker(SignEntry signEntry) {
        return signEntry.frontText().trim().startsWith(POI_MARKER_SENTINEL)
                || signEntry.backText().trim().startsWith(POI_MARKER_SENTINEL);
    }

    private void removeSign(SignEntry signEntry) {
        var key = signEntry.getKey();

        signCache.remove(key);
    }
}
