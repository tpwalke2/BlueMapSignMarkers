package com.tpwalke2.bluemapsignmarkers.core.bluemap.actions;

import com.tpwalke2.bluemapsignmarkers.core.bluemap.markers.MarkerSetIdentifierCollection;

import java.util.HashMap;
import java.util.Optional;

public class ActionFactoryFactory {
    private ActionFactoryFactory() {}

    private static final HashMap<String, ActionFactory> actionFactories = new HashMap<>();
    public static ActionFactory getActionFactory(MarkerSetIdentifierCollection markerKeyCollection, String worldId) {
        var result = Optional
                .ofNullable(actionFactories.get(worldId))
                .orElseGet(() -> new ActionFactory(markerKeyCollection, worldId));

        actionFactories.putIfAbsent(worldId, result);

        return result;
    }
}
