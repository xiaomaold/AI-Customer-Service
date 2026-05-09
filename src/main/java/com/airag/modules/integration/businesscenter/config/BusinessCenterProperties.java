package com.airag.modules.integration.businesscenter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "business-center.client")
public class BusinessCenterProperties {

    private String baseUrl = "http://localhost:8091/api/business";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
