/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.store;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.model.ChainTypeConfig;

import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Chain Type Config Store.
 * Stores call chain type configurations.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@Component
public class ChainTypeConfigStore {

    private final JsonFileStore<ChainTypeConfig> store;

    /**
     * Chain Type Config Store.
     *
     * @param properties the properties
     */
    public ChainTypeConfigStore(OperationIntelligenceProperties properties) {
        Path dir = properties.resolveDataRoot()
            .resolve("call-chain")
            .resolve("config");

        this.store = new JsonFileStore<>(
            dir,
            "chain_type_config",
            new TypeReference<List<ChainTypeConfig>>() {},
            false,
            0,
            0
        );
        this.store.init();
    }

    /**
     * Import chain type configurations.
     *
     * @param configs the chain type configurations
     */
    public void importConfigs(List<ChainTypeConfig> configs) {
        store.replaceAll(configs);
    }

    /**
     * Load all configurations.
     *
     * @return list of chain type configurations
     */
    public List<ChainTypeConfig> loadAll() {
        return store.loadAll();
    }

    /**
     * Get configuration by chain type.
     *
     * @param chainType the chain type
     * @return the configuration, or null if not found
     */
    public ChainTypeConfig getByChainType(String chainType) {
        return store.loadAll().stream()
            .filter(config -> chainType.equals(config.getChainType()))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get enabled configurations.
     *
     * @return list of enabled configurations
     */
    public List<ChainTypeConfig> getEnabledConfigs() {
        return store.loadAll().stream()
            .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
            .toList();
    }
}
