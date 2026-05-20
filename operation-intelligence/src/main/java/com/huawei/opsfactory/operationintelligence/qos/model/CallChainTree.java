/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Call Chain Tree.
 * Represents the complete call chain tree for a specific chain type and condition.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CallChainTree {
    private String chainType;

    private List<Condition> conditions;

    private Long totalCount;

    private QueryTimeRange queryTimeRange;

    private List<CallFlow> flows;

    /**
     * Gets the chain type.
     *
     * @return the chain type (BES/API/BPM/JOB)
     */
    public String getChainType() {
        return chainType;
    }

    /**
     * Sets the chain type.
     *
     * @param chainType the chain type
     */
    public void setChainType(String chainType) {
        this.chainType = chainType;
    }

    /**
     * Gets the conditions.
     *
     * @return the conditions list
     */
    public List<Condition> getConditions() {
        return conditions;
    }

    /**
     * Sets the conditions.
     *
     * @param conditions the conditions list
     */
    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    /**
     * Gets the total count.
     *
     * @return the total count
     */
    public Long getTotalCount() {
        return totalCount;
    }

    /**
     * Sets the total count.
     *
     * @param totalCount the total count
     */
    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    /**
     * Gets the query time range.
     *
     * @return the query time range
     */
    public QueryTimeRange getQueryTimeRange() {
        return queryTimeRange;
    }

    /**
     * Sets the query time range.
     *
     * @param queryTimeRange the query time range
     */
    public void setQueryTimeRange(QueryTimeRange queryTimeRange) {
        this.queryTimeRange = queryTimeRange;
    }

    /**
     * Gets the flows.
     *
     * @return the flows
     */
    public List<CallFlow> getFlows() {
        return flows;
    }

    /**
     * Sets the flows.
     *
     * @param flows the flows
     */
    public void setFlows(List<CallFlow> flows) {
        this.flows = flows;
    }

    /**
     * Condition.
     * Represents a single condition in the conditions array.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Condition {
        private String conditionKey;

        private String conditionValue;

        /**
         * Gets the condition key.
         *
         * @return the condition key (menuId/serviceName/AppendInfo/jobDefinedId)
         */
        public String getConditionKey() {
            return conditionKey;
        }

        /**
         * Sets the condition key.
         *
         * @param conditionKey the condition key
         */
        public void setConditionKey(String conditionKey) {
            this.conditionKey = conditionKey;
        }

        /**
         * Gets the condition value.
         *
         * @return the condition value
         */
        public String getConditionValue() {
            return conditionValue;
        }

        /**
         * Sets the condition value.
         *
         * @param conditionValue the condition value
         */
        public void setConditionValue(String conditionValue) {
            this.conditionValue = conditionValue;
        }
    }

    /**
     * Query Time Range.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueryTimeRange {
        private String startTime;

        private String endTime;

        /**
         * Gets the start time.
         *
         * @return the start time
         */
        public String getStartTime() {
            return startTime;
        }

        /**
         * Sets the start time.
         *
         * @param startTime the start time
         */
        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        /**
         * Gets the end time.
         *
         * @return the end time
         */
        public String getEndTime() {
            return endTime;
        }

        /**
         * Sets the end time.
         *
         * @param endTime the end time
         */
        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }
    }
}
