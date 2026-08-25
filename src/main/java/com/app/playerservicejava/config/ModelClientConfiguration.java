package com.app.playerservicejava.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class ModelClientConfiguration {

    @Bean("modelRestTemplate")
    public RestTemplate modelRestTemplate(
            RestTemplateBuilder builder,
            @Value("${player-service-model.connect-timeout:2s}") Duration connectTimeout,
            @Value("${player-service-model.read-timeout:10s}") Duration readTimeout
    ) {
        return builder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }
}
