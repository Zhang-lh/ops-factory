/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.store;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.model.ChainTypeConfig;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chain Type Config Store Test.
 *
 * @author call-chain
 * @since 2026-05-18
 */
class ChainTypeConfigStoreTest {

    @Test
    void testImportAndLoadAll() {
        OperationIntelligenceProperties properties = new OperationIntelligenceProperties();
        properties.setDataRoot("./target/test-data");

        ChainTypeConfigStore store = new ChainTypeConfigStore(properties);

        List<ChainTypeConfig> configs = List.of(
            createConfig("BES", "Business Execution System", "menuId", "url"),
            createConfig("API", "External Interface", "serviceName", "url,serviceName")
        );

        store.importConfigs(configs);

        List<ChainTypeConfig> loaded = store.loadAll();
        assertEquals(2, loaded.size());
    }

    @Test
    void testGetByChainType() {
        OperationIntelligenceProperties properties = new OperationIntelligenceProperties();
        properties.setDataRoot("./target/test-data");

        ChainTypeConfigStore store = new ChainTypeConfigStore(properties);

        ChainTypeConfig config = createConfig("BES", "Business Execution System", "menuId", "url");
        store.importConfigs(List.of(config));

        ChainTypeConfig found = store.getByChainType("BES");
        assertNotNull(found);
        assertEquals("BES", found.getChainType());

        ChainTypeConfig notFound = store.getByChainType("NONEXISTENT");
        assertNull(notFound);
    }

    @Test
    void testGetEnabledConfigs() {
        OperationIntelligenceProperties properties = new OperationIntelligenceProperties();
        properties.setDataRoot("./target/test-data");

        ChainTypeConfigStore store = new ChainTypeConfigStore(properties);

        ChainTypeConfig enabled = createConfig("BES", "Business Execution System", "menuId", "url");
        enabled.setEnabled(true);

        ChainTypeConfig disabled = createConfig("API", "External Interface", "serviceName", "url,serviceName");
        disabled.setEnabled(false);

        store.importConfigs(List.of(enabled, disabled));

        List<ChainTypeConfig> enabledConfigs = store.getEnabledConfigs();
        assertEquals(1, enabledConfigs.size());
        assertTrue(enabledConfigs.get(0).getEnabled());
    }

    private ChainTypeConfig createConfig(String chainType, String description, String conditionKey, String extractFields) {
        ChainTypeConfig config = new ChainTypeConfig();
        config.setChainType(chainType);
        config.setDescription(description);
        config.setConditionKey(conditionKey);
        config.setExtractFields(extractFields);
        config.setEnabled(true);
        return config;
    }
}