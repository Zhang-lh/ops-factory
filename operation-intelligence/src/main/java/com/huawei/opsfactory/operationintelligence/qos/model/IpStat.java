/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * IP Statistics.
 * Represents statistics for a single IP address.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IpStat {
    private String ip;

    private Long successCount;

    private Long callCount;

    private Double successPercent;

    private Long avgCost;

    private Long minCost;

    private Long maxCost;

    /**
     * Gets the ip.
     *
     * @return the ip
     */
    public String getIp() {
        return ip;
    }

    /**
     * Sets the ip.
     *
     * @param ip the ip
     */
    public void setIp(String ip) {
        this.ip = ip;
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
}
