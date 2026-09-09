package com.tpwalke2.bluemapsignmarkers.config.models;

import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class BMSMConfigV2Test {

    @Test
    void getShutdownAwaitSecondsDefaultsWhenNotSpecified() {
        var config = new BMSMConfigV2(MarkerGroup.DEFAULT_POI_GROUP);

        assertEquals(BMSMConfigV2.DEFAULT_SHUTDOWN_AWAIT_SECONDS, config.getShutdownAwaitSeconds());
    }

    @Test
    void getShutdownAwaitSecondsReturnsTheConfiguredValue() {
        var config = new BMSMConfigV2(new MarkerGroup[]{MarkerGroup.DEFAULT_POI_GROUP}, 30);

        assertEquals(30, config.getShutdownAwaitSeconds());
    }

    @Test
    void getMarkerGroupsReturnsADefensiveCopy() {
        var config = new BMSMConfigV2(MarkerGroup.DEFAULT_POI_GROUP);

        var first = config.getMarkerGroups();
        first[0] = null;

        var second = config.getMarkerGroups();
        assertNotSame(first, second);
        assertEquals(MarkerGroup.DEFAULT_POI_GROUP, second[0]);
    }
}
