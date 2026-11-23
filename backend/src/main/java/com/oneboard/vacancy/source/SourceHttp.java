package com.oneboard.vacancy.source;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.oneboard.aggregation.AggregationProperties;

@Configuration
public class SourceHttp {

    /** Shared HTTP client for all job-board integrations. */
    @Bean
    RestClient sourceRestClient(AggregationProperties props) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(props.httpTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(props.httpTimeout());
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "OneBoard/0.1 (job aggregator)")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
