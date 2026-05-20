/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.common;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.Map;

/**
 * Custom Error Attributes.
 *
 * <p>Includes detailed error messages from ResponseStatusException in error responses for non-production environments.
 *
 * @author x00000000
 * @since 2026-05-19
 */
@Component
public class CustomErrorAttributes extends DefaultErrorAttributes {

    private final Environment environment;

    public CustomErrorAttributes(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Map<String, Object> errorAttributes = super.getErrorAttributes(request, options);
        if (!isProduction()) {
            errorAttributes.put("detail", getError(request).getMessage());
        }
        return errorAttributes;
    }

    private boolean isProduction() {
        String activeProfile = environment.getProperty("spring.profiles.active", "");
        return "prod".equalsIgnoreCase(activeProfile);
    }
}