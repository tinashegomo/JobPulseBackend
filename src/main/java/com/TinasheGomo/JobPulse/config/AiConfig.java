package com.TinasheGomo.JobPulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AiConfig {

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.api.base-url:https://console.opencode.ai/inference/openai/v1}")
    private String baseUrl;

    @Value("${ai.api.model:mimo-v2.5-free}")
    private String primaryModel;

    @Bean
    public RestTemplate aiRestTemplate() {
        return new RestTemplate();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getPrimaryModel() {
        return primaryModel;
    }
}
