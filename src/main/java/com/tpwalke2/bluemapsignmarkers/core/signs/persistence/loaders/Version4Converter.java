package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV3;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV4;

public class Version4Converter {

    private Version4Converter() {
    }

    // createdAtMillis for a migrated entry is arbitrary but stable - no real placement history exists
    // for pre-existing signs, so this is deliberately not a reconstruction of true history.
    public static SignEntryV4 convertToV4(SignEntryV3 entry, int indexInFile, long fileLastModifiedMillis) {
        return new SignEntryV4(
                entry.key(),
                entry.playerId(),
                entry.frontText(),
                entry.backText(),
                fileLastModifiedMillis + indexInFile);
    }
}
