package com.bighealth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kg")
public class KgProperties {
    private String modelName;
    private String apiKey;

    private String url;

    public String getModelName() {
        return modelName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getUrl() {
        return url;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}