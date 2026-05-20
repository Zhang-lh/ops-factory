/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.service;

import com.huawei.opsfactory.operationintelligence.qos.model.FlowNode;
import com.huawei.opsfactory.operationintelligence.qos.model.IpStat;
import com.huawei.opsfactory.operationintelligence.qos.model.CallFlow;
import com.huawei.opsfactory.operationintelligence.qos.model.TraceLogRecord;
import com.huawei.opsfactory.operationintelligence.qos.parser.TraceLogParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Call Chain Statistics Calculator.
 * Calculates statistics for call flows and nodes.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@Component
public class CallChainStatistics {

    private static final Logger log = LoggerFactory.getLogger(CallChainStatistics.class);

    private final TraceLogParser parser;

    /**
     * Call Chain Statistics.
     *
     * @param parser the trace log parser
     */
    public CallChainStatistics(TraceLogParser parser) {
        this.parser = parser;
    }

    /**
     * Calculate statistics for a call flow.
     *
     * @param flow the call flow
     * @param byTraceId trace logs grouped by trace ID
     */
    public void calculateStatistics(CallFlow flow,
                                   Map<String, List<TraceLogRecord>> byTraceId) {

        // Calculate success statistics
        calculateSuccessStatistics(flow, byTraceId);

        // Calculate flow-level cost statistics
        calculateFlowCostStatistics(flow, byTraceId);

        // Get all unique seqNo positions
        Set<String> seqNos = byTraceId.values().stream()
            .flatMap(List::stream)
            .map(TraceLogRecord::getSeqNo)
            .collect(Collectors.toSet());

        List<FlowNode> nodes = new ArrayList<>();

        // Build node for each seqNo position
        for (String seqNo : seqNos) {
            List<TraceLogRecord> positionLogs = byTraceId.values().stream()
                .flatMap(List::stream)
                .filter(log -> seqNo.equals(log.getSeqNo()))
                .collect(Collectors.toList());

            FlowNode node = buildNode(seqNo, positionLogs);
            nodes.add(node);
        }

        // Sort nodes by seqNo
        nodes.sort(Comparator.comparing(FlowNode::getSeqNo, this::compareSeqNo));

        flow.setNodes(nodes);
    }

    /**
     * Calculate success statistics for a flow.
     *
     * @param flow the call flow
     * @param byTraceId trace logs grouped by trace ID
     */
    private void calculateSuccessStatistics(CallFlow flow,
                                             Map<String, List<TraceLogRecord>> byTraceId) {
        // Count successful traceIds (all logs in the trace are successful)
        long totalSuccess = byTraceId.values().stream()
            .filter(traceLogs -> traceLogs.stream().allMatch(parser::isSuccess))
            .count();

        flow.setSuccessCount(totalSuccess);

        if (flow.getCallCount() > 0) {
            flow.setSuccessPercent(totalSuccess * 100.0 / flow.getCallCount());
        }
    }

    /**
     * Calculate flow-level cost statistics.
     *
     * @param flow the call flow
     * @param byTraceId trace logs grouped by trace ID
     */
    private void calculateFlowCostStatistics(CallFlow flow,
                                              Map<String, List<TraceLogRecord>> byTraceId) {
        LongSummaryStatistics costStats = new LongSummaryStatistics();

        for (List<TraceLogRecord> traceLogs : byTraceId.values()) {
            // Sum all costs in this trace
            long traceTotalCost = traceLogs.stream()
                .filter(log -> log.getCost() != null)
                .mapToLong(TraceLogRecord::getCost)
                .sum();

            if (traceTotalCost > 0) {
                costStats.accept(traceTotalCost);
            }
        }

        if (costStats.getCount() > 0) {
            flow.setAvgCost((long) costStats.getAverage());
            flow.setMinCost(costStats.getMin());
            flow.setMaxCost(costStats.getMax());
        }
    }

    /**
     * Build a flow node from logs at a specific seqNo position.
     *
     * @param seqNo the seqNo
     * @param logs the trace logs at this position
     * @return the flow node
     */
    private FlowNode buildNode(String seqNo, List<TraceLogRecord> logs) {
        FlowNode node = new FlowNode();
        node.setSeqNo(seqNo);

        // Set node fields from first log
        TraceLogRecord sample = logs.get(0);
        setNodeFieldsFromSample(node, sample);

        // Calculate IP statistics
        node.setIp(calculateIpStatistics(logs));

        // Extract cluster list
        node.setCluster(extractClusters(logs));

        // Calculate node-level cost statistics
        calculateNodeCostStatistics(node, logs);

        return node;
    }

    /**
     * Set node fields from sample trace log record.
     *
     * @param node the flow node
     * @param sample the sample record
     */
    private void setNodeFieldsFromSample(FlowNode node, TraceLogRecord sample) {
        if (sample.getUrl() != null) {
            node.setUrl(sample.getUrl());
        } else if (sample.getServiceName() != null) {
            node.setServiceName(sample.getServiceName());
            node.setOperationName(sample.getOperationName());
        } else if (sample.getTopic() != null) {
            node.setTopic(sample.getTopic());
            node.setEventName(sample.getEventName());
        }

        // BPM-specific fields
        if (sample.getBusiCode() != null) {
            node.setBusiCode(sample.getBusiCode());
        }
        if (sample.getProcessName() != null) {
            node.setProcessName(sample.getProcessName());
        }
        if (sample.getElementName() != null) {
            node.setElementName(sample.getElementName());
        }
        if (sample.getElementType() != null) {
            node.setElementType(sample.getElementType());
        }
    }

    /**
     * Calculate IP statistics for logs at a seqNo position.
     *
     * @param logs the trace logs
     * @return list of IP statistics
     */
    private List<IpStat> calculateIpStatistics(List<TraceLogRecord> logs) {
        Map<String, IpStatAccumulator> byIp = new LinkedHashMap<>();

        for (TraceLogRecord log : logs) {
            String ip = log.getIp();
            if (ip == null || ip.isEmpty()) {
                continue;
            }

            IpStatAccumulator acc = byIp.computeIfAbsent(ip, k -> new IpStatAccumulator(ip));
            acc.totalCalls++;

            if (parser.isSuccess(log)) {
                acc.successCalls++;
            }

            if (log.getCost() != null) {
                acc.totalCost += log.getCost();
                acc.costCount++;
                if (log.getCost() < acc.minCost) {
                    acc.minCost = log.getCost();
                }
                if (log.getCost() > acc.maxCost) {
                    acc.maxCost = log.getCost();
                }
            }
        }

        return byIp.values().stream()
            .map(IpStatAccumulator::toIpStat)
            .collect(Collectors.toList());
    }

    /**
     * Extract unique cluster values from logs.
     *
     * @param logs the trace logs
     * @return list of cluster values
     */
    private List<String> extractClusters(List<TraceLogRecord> logs) {
        return logs.stream()
            .map(TraceLogRecord::getCluster)
            .filter(cluster -> cluster != null && !cluster.isEmpty())
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * Calculate node-level cost statistics.
     *
     * @param node the flow node
     * @param logs the trace logs
     */
    private void calculateNodeCostStatistics(FlowNode node, List<TraceLogRecord> logs) {
        LongSummaryStatistics costStats = logs.stream()
            .filter(log -> log.getCost() != null)
            .mapToLong(TraceLogRecord::getCost)
            .summaryStatistics();

        if (costStats.getCount() > 0) {
            node.setAvgCost((long) costStats.getAverage());
            node.setMinCost(costStats.getMin());
            node.setMaxCost(costStats.getMax());
        }
    }

    /**
     * Compare seqNo values.
     *
     * @param s1 first seqNo
     * @param s2 second seqNo
     * @return comparison result
     */
    private int compareSeqNo(String s1, String s2) {
        if (s1 == null) s1 = "0";
        if (s2 == null) s2 = "0";

        String[] parts1 = s1.split("\\.");
        String[] parts2 = s2.split("\\.");

        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int v1 = i < parts1.length ? parseSeqNoPart(parts1[i]) : 0;
            int v2 = i < parts2.length ? parseSeqNoPart(parts2[i]) : 0;
            if (v1 != v2) {
                return Integer.compare(v1, v2);
            }
        }
        return 0;
    }

    /**
     * Parse a single seqNo part to integer.
     *
     * @param part the seqNo part
     * @return the integer value
     */
    private int parseSeqNoPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Accumulator for IP statistics calculation.
     */
    private static class IpStatAccumulator {
        private final String ip;
        private int totalCalls = 0;
        private int successCalls = 0;
        private long totalCost = 0;
        private int costCount = 0;
        private long minCost = Long.MAX_VALUE;
        private long maxCost = Long.MIN_VALUE;

        IpStatAccumulator(String ip) {
            this.ip = ip;
        }

        IpStat toIpStat() {
            IpStat stat = new IpStat();
            stat.setIp(ip);
            stat.setCallCount((long) totalCalls);
            stat.setSuccessCount((long) successCalls);

            if (totalCalls > 0) {
                stat.setSuccessPercent(successCalls * 100.0 / totalCalls);
            }

            if (costCount > 0) {
                stat.setAvgCost(totalCost / costCount);
                stat.setMinCost(minCost);
                stat.setMaxCost(maxCost);
            }

            return stat;
        }
    }
}
