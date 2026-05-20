/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.service;

import com.huawei.opsfactory.operationintelligence.qos.model.CallChainTree;
import com.huawei.opsfactory.operationintelligence.qos.model.CallFlow;
import com.huawei.opsfactory.operationintelligence.qos.model.FlowNode;
import com.huawei.opsfactory.operationintelligence.qos.model.TraceLogRecord;
import com.huawei.opsfactory.operationintelligence.qos.parser.AppendInfoParser;
import com.huawei.opsfactory.operationintelligence.qos.parser.TraceLogParser;
import com.huawei.opsfactory.operationintelligence.service.CallChainStatistics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Call Chain Builder Test.
 *
 * @author call-chain
 * @since 2026-05-14
 */
class CallChainBuilderTest {

    private CallChainBuilder builder;
    private CallChainStatistics statistics;

    @BeforeEach
    void setUp() {
        TraceLogParser parser = new TraceLogParser(new AppendInfoParser());
        statistics = new CallChainStatistics(parser);
        builder = new CallChainBuilder(statistics);
    }

    @Test
    void testBuildSimpleFlow() {
        // Create test data with same traceId and multiple logs
        List<TraceLogRecord> logs = new ArrayList<>();

        TraceLogRecord log1 = new TraceLogRecord();
        log1.setTraceId("BES1234567890");
        log1.setSeqNo("1");
        log1.setUrl("/api/v1/test");
        log1.setIp("10.0.0.1");
        log1.setCluster("TestCluster");
        log1.setCost(100L);
        log1.setLogMessage("ER");
        log1.setMenuId("604015020");
        logs.add(log1);

        TraceLogRecord log2 = new TraceLogRecord();
        log2.setTraceId("BES1234567890");
        log2.setSeqNo("1.1");
        log2.setUrl("/api/v1/test2");
        log2.setIp("10.0.0.1");
        log2.setCluster("TestCluster");
        log2.setCost(50L);
        log2.setLogMessage("ER");
        logs.add(log2);

        TraceLogRecord log3 = new TraceLogRecord();
        log3.setTraceId("BES1234567890");
        log3.setSeqNo("2");
        log3.setUrl("/api/v1/test3");
        log3.setIp("10.0.0.1");
        log3.setCluster("TestCluster");
        log3.setCost(75L);
        log3.setLogMessage("ER");
        logs.add(log3);

        // Build call chain - use 1 for total count (only 1 traceId)
        CallChainTree tree = builder.build("BES", "menuId", "604015020", logs, 1);

        assertNotNull(tree);
        assertEquals("BES", tree.getChainType());
        assertEquals(1L, tree.getTotalCount());
        assertNotNull(tree.getFlows());
    }

    @Test
    void testBuildWithEmptyLogs() {
        List<TraceLogRecord> logs = new ArrayList<>();

        CallChainTree tree = builder.build("BES", "menuId", "test", logs, 0);

        assertNotNull(tree);
        assertTrue(tree.getFlows().isEmpty());
        assertEquals(0L, tree.getTotalCount());
    }

    @Test
    void testSeqNoComparison() {
        // Test seqNo comparison
        int result1 = builder.compareSeqNo("1", "2");
        assertTrue(result1 < 0);

        int result2 = builder.compareSeqNo("1.1", "1.2");
        assertTrue(result2 < 0);

        int result3 = builder.compareSeqNo("1.1", "1.10");
        assertTrue(result3 < 0);

        int result4 = builder.compareSeqNo("1", "1");
        assertEquals(0, result4);
    }

    private List<TraceLogRecord> createTestLogs() {
        List<TraceLogRecord> logs = new ArrayList<>();

        TraceLogRecord log1 = new TraceLogRecord();
        log1.setTraceId("BES1234567890");
        log1.setSeqNo("1");
        log1.setUrl("/api/v1/test");
        log1.setIp("10.0.0.1");
        log1.setCluster("TestCluster");
        log1.setCost(100L);
        log1.setLogMessage("ER");
        log1.setMenuId("604015020");
        logs.add(log1);

        TraceLogRecord log2 = new TraceLogRecord();
        log2.setTraceId("BES1234567890");
        log2.setSeqNo("1.1");
        log2.setServiceName("TestService");
        log2.setOperationName("testMethod");
        log2.setIp("10.0.0.1");
        log2.setCluster("TestCluster");
        log2.setCost(50L);
        log2.setLogMessage("EBS");
        logs.add(log2);

        return logs;
    }
}
