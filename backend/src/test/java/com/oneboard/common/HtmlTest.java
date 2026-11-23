package com.oneboard.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HtmlTest {

    @Test
    void convertsHtmlToReadableText() {
        String html = "<h2>About</h2><p>We&nbsp;build <b>things</b> &amp; more.</p><ul><li>Java</li><li>SQL</li></ul>";
        assertEquals("About\n\nWe build things & more.\n\n- Java\n- SQL", Html.toText(html));
    }

    @Test
    void nullSafe() {
        assertEquals("", Html.toText(null));
    }
}
