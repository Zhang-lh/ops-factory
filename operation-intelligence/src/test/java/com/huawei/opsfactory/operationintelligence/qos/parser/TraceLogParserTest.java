/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.parser;

import com.huawei.opsfactory.operationintelligence.qos.model.TraceLogRecord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trace Log Parser Test.
 *
 * @author call-chain
 * @since 2026-05-18
 */
class TraceLogParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testParseBESLog() {
        ObjectNode node = mapper.createObjectNode();
        node.put("TraceID", "BES1234567890");
        node.put("ServerIP", "10.0.0.1");
        node.put("ClusterType", "ClusterA");
        node.put("LogMessage", "ER");
        node.put("Time", "2025-01-01 00:00:00.000");
        node.put("cost", "100ms");
        node.put("url", "/api/v1/test");
        node.put("AppendInfo", "seqNo=1,menuId=604015020");

        TraceLogParser parser = new TraceLogParser(new AppendInfoParser());
        TraceLogRecord record = parser.parse(node);

        assertEquals("BES1234567890", record.getTraceId());
        assertEquals("10.0.0.1", record.getIp());
        assertEquals("ClusterA", record.getCluster());
        assertEquals("/api/v1/test", record.getUrl());
        assertEquals("1", record.getSeqNo());
        assertEquals("604015020", record.getMenuId());
        assertEquals(100L, record.getCost());
    }

    @Test
    void testParseAPILog() {
        ObjectNode node = mapper.createObjectNode();
        node.put("TraceID", "API1234567890");
        node.put("ServerIP", "10.0.0.2");
        node.put("ClusterType", "ClusterB");
        node.put("LogMessage", "ER");
        node.put("serviceName", "TestService");
        node.put("operationName", "testMethod");
        node.put("AppendInfo", "seqNo=1");

        TraceLogParser parser = new TraceLogParser(new AppendInfoParser());
        TraceLogRecord record = parser.parse(node);

        assertEquals("API1234567890", record.getTraceId());
        assertEquals("TestService", record.getServiceName());
        assertEquals("testMethod", record.getOperationName());
        assertNull(record.getUrl());
    }

    @Test
    void testParseMQLog() {
        ObjectNode node = mapper.createObjectNode();
        node.put("TraceID", "MQ1234567890");
        node.put("ServerIP", "10.0.0.3");
        node.put("ClusterType", "ClusterC");
        node.put("LogMessage", "ER");
        node.put("AppendInfo", "seqNo=1,type=producer,topic=testTopic,eventName=testEvent");

        TraceLogParser parser = new TraceLogParser(new AppendInfoParser());
        TraceLogRecord record = parser.parse(node);

        assertEquals("MQ1234567890", record.getTraceId());
        assertEquals("testTopic", record.getTopic());
        assertEquals("testEvent", record.getEventName());
    }

    @Test
    void testParseBPMLog() {
        ObjectNode node = mapper.createObjectNode();
        node.put("TraceID", "BPM1234567890");
        node.put("ServerIP", "10.0.0.4");
        node.put("ClusterType", "ClusterD");
        node.put("LogMessage", "ER");
        node.put("AppendInfo", "seqNo=1,busiCode=TEST001,processName=TestProcess");

        TraceLogParser parser = new TraceLogParser(new AppendInfoParser());
        TraceLogRecord record = parser.parse(node);

        assertEquals("BPM1234567890", record.getTraceId());
        assertEquals("TEST001", record.getBusiCode());
        assertEquals("TestProcess", record.getProcessName());
    }

    @Test
    void testParseJOBLog() {
        ObjectNode node = mapper.createObjectNode();
        node.put("TraceID", "JOB1234567890");
        node.put("ServerIP", "10.0.0.5");
        node.put("ClusterType", "ClusterE");
        node.put("LogMessage", "ER");
        node.put("jobDefinedId", "TEST_JOB_001");
        node.put("AppendInfo", "seqNo=1,jobDefinedId=TEST_JOB_001");

        TraceLogParser parser = new TraceLogParser(new AppendInfoParser());
        TraceLogRecord record = parser.parse(node);

        assertEquals("JOB1234567890", record.getTraceId());
        assertEquals("TEST_JOB_001", record.getJobDefinedId());
    }

    @Test
    void testParseCostWithUnit() {
        ObjectNode node = mapper.createObjectNode();
        node.put("TraceID", "TEST1234567890");
        node.put("LogMessage", "ER");
        node.put("cost", "150ms");

        TraceLogParser parser = new TraceLogParser(new AppendInfoParser());
        TraceLogRecord record = parser.parse(node);

        assertEquals(150L, record.getCost());
    }

    @Test
    void testParseCostWithoutUnit() {
        ObjectNode node = mapper.createObjectNode();
        node.put("TraceID", "TEST1234567890");
        node.put("LogMessage", "ER");
        node.put("cost", "200");

        TraceLogParser parser = new TraceLogParser(new AppendInfoParser());
        TraceLogRecord record = parser.parse(node);

        assertEquals(200L, record.getCost());
    }

    @Test
    void testParseInvalidCost() {
        ObjectNode node = mapper.createObjectNode();
        node.put("TraceID", "TEST1234567890");
        node.put("LogMessage", "ER");
        node.put("cost", "invalid");

        TraceLogParser parser = new TraceLogParser(new AppendInfoParser());
        TraceLogRecord record = parser.parse(node);

        assertNull(record.getCost());
    }

    @Test
    void testParseNullCost() {
        ObjectNode node = mapper.createObjectNode();
        node.put("TraceID", "TEST1234567890");
        node.put("LogMessage", "ER");

        TraceLogParser parser = new TraceLogParser(new AppendInfoParser());
        TraceLogRecord record = parser.parse(node);

        assertNull(record.getCost());
    }

    @Test
    void testIsSuccess() {
        TraceLogParser parser = new TraceLogParser(new AppendInfoParser());
        TraceLogRecord record = new TraceLogRecord();

        record.setLogMessage("ER");
        assertTrue(parser.isSuccess(record));

        record.setLogMessage("END");
        assertTrue(parser.isSuccess(record));

        record.setLogMessage("EBS");
        assertTrue(parser.isSuccess(record));

        record.setLogMessage("EMQ");
        assertTrue(parser.isSuccess(record));

        record.setLogMessage("EBPMP");
        assertTrue(parser.isSuccess(record));

        record.setLogMessage("EBPMA");
        assertTrue(parser.isSuccess(record));

        record.setLogMessage("ERROR");
        assertTrue(parser.isSuccess(record));

        record.setLogMessage("error");
        assertTrue(parser.isSuccess(record));
    }
}