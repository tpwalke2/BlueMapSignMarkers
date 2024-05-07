package com.tpwalke2.bluemapsignmarkers.core.signs;

public class SignDetails {
    private final SignEntry signEntry;
    private final String label;
    private final String detail;
    private final String prefix;

    public SignDetails(SignEntry signEntry) {
        this.signEntry = signEntry;
        this.label = SignEntryHelper.getLabel(signEntry);
        this.detail = SignEntryHelper.getDetail(signEntry);
        this.prefix = SignEntryHelper.getPrefix(signEntry);
    }

    public SignEntry getSignEntry() {
        return signEntry;
    }

    public String getLabel() {
        return label;
    }

    public String getDetail() {
        return detail;
    }

    public String getPrefix() {
        return prefix;
    }
}
