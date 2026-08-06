package com.tpwalke2.bluemapsignmarkers.common;

public class HtmlUtils {

    private HtmlUtils() {}

    public static String escape(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static String toHtmlDetail(String text) {
        return escape(text).replace("\n", "<br>");
    }
}
