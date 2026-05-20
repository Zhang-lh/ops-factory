/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.service;

import com.huawei.opsfactory.operationintelligence.qos.model.CallChainTree;
import com.huawei.opsfactory.operationintelligence.qos.model.CallFlow;
import com.huawei.opsfactory.operationintelligence.qos.model.FlowNode;
import com.huawei.opsfactory.operationintelligence.qos.model.TraceLogRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Call Chain Builder.
 * Builds call chain trees from trace log records.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@Component
public class CallChainBuilder {

    private static final Logger log = LoggerFactory.getLogger(CallChainBuilder.class);

    private final CallChainStatistics statisticsCalculator;

    /**
     * Call Chain Builder.
     *
     * @param statisticsCalculator the statistics calculator
     */
    public CallChainBuilder(CallChainStatistics statisticsCalculator) {
        this.statisticsCalculator = statisticsCalculator;
    }

    /**
     * Build call chain tree from trace log records.
     *
     * @param chainType the chain type (BES/API/BPM/JOB)
     * @param conditionType the condition type
     * @param conditionValue the condition value
     * @param logs the trace log records
     * @param totalCount the total count
     * @return the call chain tree
     */
    public CallChainTree build(String chainType,
                              String conditionType,
                              String conditionValue,
                              List<TraceLogRecord> logs,
                              long totalCount) {

        // 1. 按 traceId 分组
        Map<String, List<TraceLogRecord>> byTraceId = logs.stream()
            .filter(log -> log.getTraceId() != null)
            .collect(Collectors.groupingBy(
                TraceLogRecord::getTraceId,
                LinkedHashMap::new,
                Collectors.toList()
            ));

        if (byTraceId.isEmpty()) {
            log.warn("No valid trace logs found for chainType={}", chainType);
            return createEmptyTree(chainType);
        }

        // 2. 按 seqNo 序列签名分组（流程识别）
        Map<String, List<List<TraceLogRecord>>> bySequence = groupBySequence(byTraceId);

        // 3. 构建 Flow 列表
        List<CallFlow> flows = bySequence.entrySet().stream()
            .map(entry -> buildFlow(entry.getKey(), entry.getValue(), totalCount))
            .filter(this::isValidFlow)
            .sorted(Comparator.comparing(CallFlow::getCallCount).reversed())
            .collect(Collectors.toList());

        // 4. 构建 CallChainTree
        CallChainTree tree = new CallChainTree();
        tree.setChainType(chainType);
        tree.setFlows(flows);
        tree.setTotalCount(totalCount);

        log.info("Built call chain tree: chainType={}, flows={}, totalCount={}",
            chainType, flows.size(), totalCount);

        return tree;
    }

    /**
     * Group trace logs by their sequence signature.
     *
     * @param byTraceId trace logs grouped by trace ID
     * @return map of sequence signature to list of trace log groups
     */
    private Map<String, List<List<TraceLogRecord>>> groupBySequence(
        Map<String, List<TraceLogRecord>> byTraceId) {

        Map<String, List<List<TraceLogRecord>>> result = new LinkedHashMap<>();

        for (Map.Entry<String, List<TraceLogRecord>> entry : byTraceId.entrySet()) {
            List<TraceLogRecord> traceLogs = entry.getValue();

            // 排序并生成序列签名
            traceLogs.sort(Comparator.comparing(
                TraceLogRecord::getSeqNo,
                this::compareSeqNo
            ));

            // 验证 seqNo 完整性
            if (!isSeqNoSequenceValid(traceLogs)) {
                log.debug("Skipping traceId={} due to invalid seqNo sequence", entry.getKey());
                continue;
            }

            String signature = generateSequenceSignature(traceLogs);
            result.computeIfAbsent(signature, k -> new ArrayList<>())
                .add(traceLogs);
        }

        return result;
    }

    /**
     * Generate sequence signature for a trace log list.
     *
     * @param logs the trace logs
     * @return the sequence signature
     */
    private String generateSequenceSignature(List<TraceLogRecord> logs) {
        return logs.stream()
            .map(log -> {
                if (log.getUrl() != null) {
                    return "URL:" + log.getUrl();
                } else if (log.getServiceName() != null) {
                    return "SVC:" + log.getServiceName();
                } else if (log.getTopic() != null) {
                    return "MQ:" + log.getTopic();
                } else if (log.getBusiCode() != null) {
                    return "BPM:" + log.getBusiCode();
                } else if (log.getJobDefinedId() != null) {
                    return "JOB:" + log.getJobDefinedId();
                } else {
                    return "UNKNOWN";
                }
            })
            .collect(Collectors.joining("->"));
    }

    /**
     * Compare seqNo values with dot notation.
     *
     * @param s1 first seqNo
     * @param s2 second seqNo
     * @return comparison result
     */
    int compareSeqNo(String s1, String s2) {
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
     * Validate seqNo sequence completeness.
     *
     * @param logs the trace logs
     * @return true if sequence is valid
     */
    private boolean isSeqNoSequenceValid(List<TraceLogRecord> logs) {
        if (logs.isEmpty()) {
            return false;
        }

        // Must have seqNo=1 as starting point
        boolean hasStart = logs.stream().anyMatch(log -> "1".equals(log.getSeqNo()));
        if (!hasStart) {
            return false;
        }

        // Check for major gaps (optional - can be enabled for strict validation)
        // For now, just ensure we have at least one entry point
        return true;
    }

    /**
     * Build a single call flow.
     *
     * @param signature the sequence signature
     * @param traceGroups list of trace log groups sharing the same signature
     * @param totalCount the total count
     * @return the call flow
     */
    private CallFlow buildFlow(String signature,
                              List<List<TraceLogRecord>> traceGroups,
                              long totalCount) {
        CallFlow flow = new CallFlow();
        flow.setFlowId("flow_" + UUID.randomUUID().toString().substring(0, 8));
        flow.setCallCount((long) traceGroups.size());

        // Calculate call ratio
        if (totalCount > 0) {
            flow.setCallRatio(flow.getCallCount() * 100.0 / totalCount);
        }

        // Collect all traceId's logs for statistics
        Map<String, List<TraceLogRecord>> byTraceId = new LinkedHashMap<>();
        for (List<TraceLogRecord> group : traceGroups) {
            String traceId = group.get(0).getTraceId();
            byTraceId.put(traceId, group);
        }

        // Calculate statistics
        statisticsCalculator.calculateStatistics(flow, byTraceId);

        return flow;
    }

    /**
     * Check if a flow is valid (meets minimum criteria).
     *
     * @param flow the call flow
     * @return true if valid
     */
    private boolean isValidFlow(CallFlow flow) {
        // Filter low frequency flows (< 3%)
        if (flow.getCallRatio() != null && flow.getCallRatio() < 3.0) {
            return false;
        }
        return flow.getCallCount() != null && flow.getCallCount() > 0;
    }

    /**
     * Create an empty call chain tree.
     *
     * @param chainType the chain type
     * @return empty call chain tree
     */
    private CallChainTree createEmptyTree(String chainType) {
        CallChainTree tree = new CallChainTree();
        tree.setChainType(chainType);
        tree.setFlows(new ArrayList<>());
        tree.setTotalCount(0L);
        return tree;
    }
}
