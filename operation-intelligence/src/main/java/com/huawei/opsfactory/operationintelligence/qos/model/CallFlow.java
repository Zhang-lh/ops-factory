/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Call Flow.
 * Represents a single call flow with its nodes and statistics.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CallFlow {
    private String flowId;

    private Long callCount;

    private Double callRatio;

    private Long successCount;

    private Double successPercent;

    private Long avgCost;

    private Long minCost;

    private Long maxCost;

    private List<FlowNode> nodes;

    /**
     * Gets the flow id.
     *
     * @return the flow id
     */
    public String getFlowId() {
        return flowId;
    }

    /**
     * Sets the flow id.
     *
     * @param flowId the flow id
     */
    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    /**
     * Gets the call count.
     *
     * @return the call count
     */
    public Long getCallCount() {
        return callCount;
    }

    /**
     * Sets the call count.
     *
     * @param callCount the call count
     */
    public void setCallCount(Long callCount) {
        this.callCount = callCount;
    }

    /**
     * Gets the call ratio.
     *
     * @return the call ratio (percentage)
     */
    public Double getCallRatio() {
        return callRatio;
    }

    /**
     * Sets the call ratio.
     *
     * @param callRatio the call ratio
     */
    public void setCallRatio(Double callRatio) {
        this.callRatio = callRatio;
    }

    /**
     * Gets the success count.
     *
     * @return the success count
     */
    public Long getSuccessCount() {
        return successCount;
    }

    /**
     * Sets the success count.
     *
     * @param successCount the success count
     */
    public void setSuccessCount(Long successCount) {
        this.successCount = successCount;
    }

    /**
     * Gets the success percent.
     *
     * @return the success percent
     */
    public Double getSuccessPercent() {
        return successPercent;
    }

    /**
     * Sets the success percent.
     *
     * @param successPercent the success percent
     */
    public void setSuccessPercent(Double successPercent) {
        this.successPercent = successPercent;
    }

    /**
     * Gets the avg cost.
     *
     * @return the avg cost
     */
    public Long getAvgCost() {
        return avgCost;
    }

    /**
     * Sets the avg cost.
     *
     * @param avgCost the avg cost
     */
    public void setAvgCost(Long avgCost) {
        this.avgCost = avgCost;
    }

    /**
     * Gets the min cost.
     *
     * @return the min cost
     */
    public Long getMinCost() {
        return minCost;
    }

    /**
     * Sets the min cost.
     *
     * @param minCost the min cost
     */
    public void setMinCost(Long minCost) {
        this.minCost = minCost;
    }

    /**
     * Gets the max cost.
     *
     * @return the max cost
     */
    public Long getMaxCost() {
        return maxCost;
    }

    /**
     * Sets the max cost.
     *
     * @param maxCost the max cost
     */
    public void setMaxCost(Long maxCost) {
        this.maxCost = maxCost;
    }

    /**
     * Gets the nodes.
     *
     * @return the nodes
     */
    public List<FlowNode> getNodes() {
        return nodes;
    }

    /**
     * Sets the nodes.
     *
     * @param nodes the nodes
     */
    public void setNodes(List<FlowNode> nodes) {
        this.nodes = nodes;
    }
}
