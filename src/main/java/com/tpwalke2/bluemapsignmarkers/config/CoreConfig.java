package com.tpwalke2.bluemapsignmarkers.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
@ConfigSerializable
public class CoreConfig {
    private String poiPrefix = "[poi]";

    public String getPoiPrefix() {
        return poiPrefix;
    }
}
