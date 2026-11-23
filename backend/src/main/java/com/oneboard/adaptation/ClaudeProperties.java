package com.oneboard.adaptation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("oneboard.claude")
public record ClaudeProperties(
        String apiKey,
        @DefaultValue("claude-opus-5-5") String model,
        @DefaultValue("16000") long maxTokens) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
