package com.airag.modules.integration.businesscenter.client;

public class BusinessCenterClientException extends RuntimeException {

    private final String code;

    public BusinessCenterClientException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
