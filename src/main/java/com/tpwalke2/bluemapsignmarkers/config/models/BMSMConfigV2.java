package com.tpwalke2.bluemapsignmarkers.config.models;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;

public final class BMSMConfigV2 {
    public static final int DEFAULT_SHUTDOWN_AWAIT_SECONDS = 5;

    public BMSMConfigV2() {}

    public BMSMConfigV2(MarkerGroup[] markerGroups) {
        this.markerGroups = markerGroups;
    }

    public BMSMConfigV2(MarkerGroup markerGroup) {
        this.markerGroups = new MarkerGroup[]{markerGroup};
    }

    public BMSMConfigV2(MarkerGroup[] markerGroups, int shutdownAwaitSeconds) {
        this.markerGroups = markerGroups;
        this.shutdownAwaitSeconds = shutdownAwaitSeconds;
    }

    private MarkerGroup[] markerGroups = new MarkerGroup[]{MarkerGroup.DEFAULT_POI_GROUP};
    // How long ReactiveQueue.shutdown() (used for the BlueMap marker-action queue) waits for in-flight
    // tasks to finish before forcing a shutdownNow(); see AGENTS.md / ReactiveQueue for why this matters.
    private int shutdownAwaitSeconds = DEFAULT_SHUTDOWN_AWAIT_SECONDS;

    public MarkerGroup[] getMarkerGroups() {
        return markerGroups.clone();
    }

    public int getShutdownAwaitSeconds() {
        return shutdownAwaitSeconds;
    }
}
