/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.store;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.model.CallChainTree;

import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Call Chain Store.
 * Stores call chain tree data with rotation.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@Component
public class CallChainStore {

    private final JsonFileStore<CallChainTree> store;

    /**
     * Call Chain Store.
     *
     * @param properties the properties
     */
    public CallChainStore(OperationIntelligenceProperties properties) {
        Path dir = properties.resolveDataRoot()
            .resolve("call-chain")
            .resolve("normalize");

        long rotationMs = properties.getCallChain().getRotationIntervalMs();
        long retentionMs = properties.getCallChain().getNormalizeDataRetentionDays() * 86400_000L;

        this.store = new JsonFileStore<>(
            dir,
            "call_chain",
            new TypeReference<List<CallChainTree>>() {},
            true,
            rotationMs,
            retentionMs
        );
        this.store.init();
    }

    /**
     * Save call chain tree.
     *
     * @param tree the call chain tree
     */
    public void save(CallChainTree tree) {
        store.append(tree);
    }

    /**
     * Save multiple call chain trees.
     *
     * @param trees the call chain trees
     */
    public void saveAll(List<CallChainTree> trees) {
        store.appendAll(trees);
    }

    /**
     * Query call chains by time range.
     *
     * @param startTime the start time in milliseconds
     * @param endTime the end time in milliseconds
     * @return list of call chain trees
     */
    public List<CallChainTree> queryByTimeRange(long startTime, long endTime) {
        return store.loadRange(startTime, endTime);
    }

    /**
     * Query call chains by type and time range.
     *
     * @param chainType the chain type
     * @param startTime the start time in milliseconds
     * @param endTime the end time in milliseconds
     * @return list of call chain trees
     */
    public List<CallChainTree> queryByTypeAndTimeRange(String chainType, long startTime, long endTime) {
        return store.loadRange(startTime, endTime).stream()
            .filter(tree -> chainType.equals(tree.getChainType()))
            .toList();
    }

    /**
     * Cleanup expired data.
     */
    public void cleanup() {
        store.cleanup();
    }
}
