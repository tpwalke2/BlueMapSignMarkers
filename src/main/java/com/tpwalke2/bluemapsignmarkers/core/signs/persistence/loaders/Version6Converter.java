package com.tpwalke2.bluemapsignmarkers.core.signs.persistence.loaders;

import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntry;
import com.tpwalke2.bluemapsignmarkers.core.signs.SignEntryHelper;
import com.tpwalke2.bluemapsignmarkers.core.signs.persistence.models.SignEntryV5;

public class Version6Converter {

    private Version6Converter() {
    }

    public static SignEntry convertToV6(SignEntryV5 entry) {
        return new SignEntry(
                entry.key(),
                entry.playerId(),
                entry.frontText(),
                entry.backText(),
                entry.createdAtMillis(),
                entry.frontRawLines(),
                entry.backRawLines(),
                SignEntryHelper.UNDYED_DYE,
                SignEntryHelper.UNDYED_DYE);
    }
}
