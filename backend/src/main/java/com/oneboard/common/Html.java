package com.oneboard.common;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

/** Converts job-board HTML descriptions into readable plain text. */
public final class Html {
    private Html() {}

    public static String toText(String html) {
        if (html == null || html.isBlank()) return "";
        Document.OutputSettings settings = new Document.OutputSettings().prettyPrint(false);
        // keep line structure: block elements -> newlines, list items -> bullets
        String marked = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|ul|ol|tr)>", "\n\n")
                .replaceAll("(?i)<li[^>]*>", "\n- ");
        String text = Jsoup.clean(marked, "", Safelist.none(), settings);
        text = Parser.unescapeEntities(text, false);
        return text.replace(' ', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
