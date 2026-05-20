/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.store;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.model.CallChainTree;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Call Chain Store Test.
 *
 * @author call-chain
 * @since 2026-05-18
 */
class CallChainStoreTest {

    @TempDir
    Path tempDir;

    private CallChainStore store;

    @BeforeEach
    void setUp() {
        OperationIntelligenceProperties properties = new OperationIntelligenceProperties();
        properties.setDataRoot(tempDir.toString());

        OperationIntelligenceProperties.CallChain callChain = new OperationIntelligenceProperties.CallChain();
        callChain.setRotationIntervalMs(3600000L);
        callChain.setNormalizeDataRetentionDays(7);
        properties.setCallChain(callChain);

        store = new CallChainStore(properties);
    }

    @Test
    void testSaveAndQuery() {
        CallChainTree tree = createTestTree();
        store.save(tree);

        long startTime = Instant.now().minusSeconds(3600).toEpochMilli();
        long endTime = Instant.now().plusSeconds(3600).toEpochMilli();

        List<CallChainTree> result = store.queryByTimeRange(startTime, endTime);
        assertNotNull(result);
        assertTrue(result.size() >= 1);
    }

    @Test
    void testQueryByTypeAndTimeRange() {
        CallChainTree tree1 = createTestTree();
        tree1.setChainType("BES");
        store.save(tree1);

        CallChainTree tree2 = createTestTree();
        tree2.setChainType("API");
        store.save(tree2);

        long startTime = Instant.now().minusSeconds(3600).toEpochMilli();
        long endTime = Instant.now().plusSeconds(3600).toEpochMilli();

        List<CallChainTree> result = store.queryByTypeAndTimeRange("BES", startTime, endTime);
        assertNotNull(result);
        result.forEach(tree -> assertEquals("BES", tree.getChainType()));
    }

    @Test
    void testSaveAll() {
        List<CallChainTree> trees = List.of(createTestTree(), createTestTree(), createTestTree());
        store.saveAll(trees);

        long startTime = Instant.now().minusSeconds(3600).toEpochMilli();
        long endTime = Instant.now().plusSeconds(3600).toEpochMilli();

        List<CallChainTree> result = store.queryByTimeRange(startTime, endTime);
        assertTrue(result.size() >= 3);
    }

    @Test
    void testCleanup() {
        CallChainTree tree = createTestTree();
        store.save(tree);

        store.cleanup();

        long startTime = Instant.now().minusSeconds(3600).toEpochMilli();
        long endTime = Instant.now().plusSeconds(3600).toEpochMilli();

        List<CallChainTree> result = store.queryByTimeRange(startTime, endTime);
        assertNotNull(result);
    }

    private CallChainTree createTestTree() {
        CallChainTree tree = new CallChainTree();
        tree.setChainType("BES");
        tree.setTotalCount(100L);
        tree.setFlows(List.of());

        CallChainTree.QueryTimeRange timeRange = new CallChainTree.QueryTimeRange();
        timeRange.setStartTime("2025-01-01 00:00:00");
        timeRange.setEndTime("2025-01-02 00:00:00");
        tree.setQueryTimeRange(timeRange);

        return tree;
    }
}