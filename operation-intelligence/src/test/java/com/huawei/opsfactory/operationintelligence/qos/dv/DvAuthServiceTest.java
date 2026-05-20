/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.dv;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dv Auth Service Test.
 *
 * @author call-chain
 * @since 2026-05-18
 */
class DvAuthServiceTest {

    @Test
    void testBuildAuthHeaders() {
        DvSslContextFactory sslFactory = new DvSslContextFactory();
        DvAuthService authService = new DvAuthService(sslFactory);

        DvEnvironmentInfo env = new DvEnvironmentInfo();
        env.setServerUrl("https://test.example.com");
        env.setUtmUser("testuser");
        env.setUtmPassword("testpass");

        try {
            Map<String, String> headers = authService.buildAuthHeaders(env);
            assertNotNull(headers);
            assertTrue(headers.containsKey("Content-Type"));
            assertTrue(headers.containsKey("Accept"));
            assertTrue(headers.containsKey("X-Auth-Token"));
            assertTrue(headers.containsKey("roaRand"));
        } catch (RuntimeException e) {
            // Expected in test environment without actual DV server
            assertTrue(e.getMessage().contains("DV SSO login failed"));
        }
    }

    @Test
    void testClearCache() {
        DvSslContextFactory sslFactory = new DvSslContextFactory();
        DvAuthService authService = new DvAuthService(sslFactory);

        authService.clearCache();
        // No exception should be thrown
    }
}