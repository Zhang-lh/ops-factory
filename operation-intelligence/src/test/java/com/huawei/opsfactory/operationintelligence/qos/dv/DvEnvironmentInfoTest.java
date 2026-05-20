/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.dv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dv Environment Info Test.
 *
 * @author call-chain
 * @since 2026-05-18
 */
class DvEnvironmentInfoTest {

    @Test
    void testDefaultConstructor() {
        DvEnvironmentInfo info = new DvEnvironmentInfo();
        assertTrue(info.isStrictSsl());
    }

    @Test
    void testFullConstructor() {
        DvEnvironmentInfo info = new DvEnvironmentInfo(
            "test-env",
            "test-type",
            "https://example.com",
            "testuser",
            "testpass",
            "crt-content",
            "cert.pem"
        );

        assertEquals("test-env", info.getEnvCode());
        assertEquals("test-type", info.getAgentSolutionType());
        assertEquals("https://example.com", info.getServerUrl());
        assertEquals("testuser", info.getUtmUser());
        assertEquals("testpass", info.getUtmPassword());
        assertEquals("crt-content", info.getCrtContent());
        assertEquals("cert.pem", info.getCrtFileName());
    }

    @Test
    void testConstructorWithDns() {
        DvEnvironmentInfo info = new DvEnvironmentInfo(
            "test-env",
            "test-type",
            "https://example.com",
            "testuser",
            "testpass",
            "crt-content",
            "cert.pem",
            "8.8.8.8"
        );

        assertEquals("test-env", info.getEnvCode());
        assertEquals("8.8.8.8", info.getDns());
    }

    @Test
    void testSettersAndGetters() {
        DvEnvironmentInfo info = new DvEnvironmentInfo();

        info.setEnvCode("prod");
        info.setAgentSolutionType("CRM");
        info.setServerUrl("https://prod.example.com");
        info.setUtmUser("admin");
        info.setUtmPassword("secret");
        info.setCrtContent("cert-data");
        info.setCrtFileName("server.crt");
        info.setDns("1.1.1.1");
        info.setStrictSsl(false);

        assertEquals("prod", info.getEnvCode());
        assertEquals("CRM", info.getAgentSolutionType());
        assertEquals("https://prod.example.com", info.getServerUrl());
        assertEquals("admin", info.getUtmUser());
        assertEquals("secret", info.getUtmPassword());
        assertEquals("cert-data", info.getCrtContent());
        assertEquals("server.crt", info.getCrtFileName());
        assertEquals("1.1.1.1", info.getDns());
        assertFalse(info.isStrictSsl());
    }

    @Test
    void testToString() {
        DvEnvironmentInfo info = new DvEnvironmentInfo(
            "test-env",
            "test-type",
            "https://example.com",
            "testuser",
            "testpass",
            "crt-content",
            "cert.pem"
        );

        String str = info.toString();
        assertTrue(str.contains("test-env"));
        assertTrue(str.contains("testuser"));
        assertTrue(str.contains("*****"));
        assertFalse(str.contains("testpass"));
    }
}