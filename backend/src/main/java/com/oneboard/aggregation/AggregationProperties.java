package com.oneboard.aggregation;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("oneboard.aggregation")
public record AggregationProperties(
        @DefaultValue("false") boolean scheduleEnabled,
        @DefaultValue("java") List<String> defaultQueries,
        @DefaultValue("20s") Duration httpTimeout) {
}
