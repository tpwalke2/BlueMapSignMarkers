package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV4;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV5;

public class Version5Converter {

    private Version5Converter() {
    }

    // Pre-V5 entries have no raw sign lines on disk to backfill from - null (not empty arrays) marks
    // "not available", matching SignEntryV5's sentinel convention.
    public static SignEntryV5 convertToV5(SignEntryV4 entry) {
        return new SignEntryV5(
                entry.key(),
                entry.playerId(),
                entry.frontText(),
                entry.backText(),
                entry.createdAtMillis(),
                null,
                null);
    }
}
