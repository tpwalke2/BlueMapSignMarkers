package com.tpwalke2.bluemapsignmarkers.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HtmlUtilsTest {

    @Test
    void escapeNeutralizesIndividualMetacharacters() {
        assertEquals("&amp;", HtmlUtils.escape("&"));
        assertEquals("&lt;", HtmlUtils.escape("<"));
        assertEquals("&gt;", HtmlUtils.escape(">"));
        assertEquals("&quot;", HtmlUtils.escape("\""));
        assertEquals("&#39;", HtmlUtils.escape("'"));
    }

    @Test
    void escapeNeutralizesScriptPayload() {
        var result = HtmlUtils.escape("<script>alert('xss')</script>");

        assertEquals("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;", result);
    }

    @Test
    void escapeLeavesPlainTextUntouched() {
        assertEquals("Town Hall, Open 9-5", HtmlUtils.escape("Town Hall, Open 9-5"));
    }

    @Test
    void toHtmlDetailEscapesBeforeConvertingLiteralBrText() {
        var result = HtmlUtils.toHtmlDetail("<br>");

        assertEquals("&lt;br&gt;", result);
    }

    @Test
    void toHtmlDetailConvertsNewlinesToBr() {
        assertEquals("Town Hall<br>Open 9-5<br>Ask for Bob",
                HtmlUtils.toHtmlDetail("Town Hall\nOpen 9-5\nAsk for Bob"));
    }

    @Test
    void toHtmlDetailConvertsConsecutiveNewlines() {
        assertEquals("Line 1<br><br>Line 2", HtmlUtils.toHtmlDetail("Line 1\n\nLine 2"));
    }

    @Test
    void toHtmlDetailIsNoOpOnLineBreaksWhenNoneArePresent() {
        assertEquals("Town Hall", HtmlUtils.toHtmlDetail("Town Hall"));
    }
}
