/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Web Config Test.
 *
 * @author call-chain
 * @since 2026-05-18
 */
class WebConfigTest {

    @Test
    void testCorsWebFilter() {
        OperationIntelligenceProperties properties = new OperationIntelligenceProperties();
        WebConfig config = new WebConfig(properties);

        var filter = config.corsWebFilter();
        assertNotNull(filter);
    }

    @Test
    void testCorsWebFilterWithOrigin() {
        OperationIntelligenceProperties properties = new OperationIntelligenceProperties();
        properties.setCorsOrigin("https://example.com,https://test.com");
        WebConfig config = new WebConfig(properties);

        var filter = config.corsWebFilter();
        assertNotNull(filter);
    }

    @Test
    void testCorsWebFilterWithoutOrigin() {
        OperationIntelligenceProperties properties = new OperationIntelligenceProperties();
        WebConfig config = new WebConfig(properties);

        var filter = config.corsWebFilter();
        assertNotNull(filter);
    }
}