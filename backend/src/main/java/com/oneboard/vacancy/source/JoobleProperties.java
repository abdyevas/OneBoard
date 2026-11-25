package com.oneboard.vacancy.source;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Jooble partner REST API. Keys are issued per country: request one for Azerbaijan at
 * https://az.jooble.org/api/about. Free plan: 100 requests per day per key.
 */
@ConfigurationProperties("oneboard.jooble")
public record JoobleProperties(
        String apiKey,
        @DefaultValue("https://az.jooble.org") String baseUrl,
        @DefaultValue("Azərbaycan") String location,
        @DefaultValue("50") int resultsPerPage,
        @DefaultValue("2") int maxPages) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
