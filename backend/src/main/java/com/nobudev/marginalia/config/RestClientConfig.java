package com.nobudev.marginalia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent",
                        "Marginalia/1.0 (RSS Reader; +https://github.com/nobudev7/marginalia)")
                .defaultHeader("Accept",
                        "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                .build();
    }
}
