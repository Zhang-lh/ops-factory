/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Flow Node.
 * Represents a single node in a call flow with its statistics.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowNode {
    private String seqNo;

    private String url;

    private String serviceName;

    private String operationName;

    private String topic;

    private String eventName;

    private String busiCode;

    private String processName;

    private String elementName;

    private String elementType;

    private List<IpStat> ip;

    private List<String> cluster;

    private Long avgCost;

    private Long minCost;

    private Long maxCost;

    /**
     * Gets the seq no.
     *
     * @return the seq no
     */
    public String getSeqNo() {
        return seqNo;
    }

    /**
     * Sets the seq no.
     *
     * @param seqNo the seq no
     */
    public void setSeqNo(String seqNo) {
        this.seqNo = seqNo;
    }

    /**
     * Gets the url.
     *
     * @return the url
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the url.
     *
     * @param url the url
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Gets the service name.
     *
     * @return the service name
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Sets the service name.
     *
     * @param serviceName the service name
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * Gets the operation name.
     *
     * @return the operation name
     */
    public String getOperationName() {
        return operationName;
    }

    /**
     * Sets the operation name.
     *
     * @param operationName the operation name
     */
    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    /**
     * Gets the topic.
     *
     * @return the topic
     */
    public String getTopic() {
        return topic;
    }

    /**
     * Sets the topic.
     *
     * @param topic the topic
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * Gets the event name.
     *
     * @return the event name
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * Sets the event name.
     *
     * @param eventName the event name
     */
    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    /**
     * Gets the busi code.
     *
     * @return the busi code
     */
    public String getBusiCode() {
        return busiCode;
    }

    /**
     * Sets the busi code.
     *
     * @param busiCode the busi code
     */
    public void setBusiCode(String busiCode) {
        this.busiCode = busiCode;
    }

    /**
     * Gets the process name.
     *
     * @return the process name
     */
    public String getProcessName() {
        return processName;
    }

    /**
     * Sets the process name.
     *
     * @param processName the process name
     */
    public void setProcessName(String processName) {
        this.processName = processName;
    }

    /**
     * Gets the element name.
     *
     * @return the element name
     */
    public String getElementName() {
        return elementName;
    }

    /**
     * Sets the element name.
     *
     * @param elementName the element name
     */
    public void setElementName(String elementName) {
        this.elementName = elementName;
    }

    /**
     * Gets the element type.
     *
     * @return the element type
     */
    public String getElementType() {
        return elementType;
    }

    /**
     * Sets the element type.
     *
     * @param elementType the element type
     */
    public void setElementType(String elementType) {
        this.elementType = elementType;
    }

    /**
     * Gets the ip.
     *
     * @return the ip statistics list
     */
    public List<IpStat> getIp() {
        return ip;
    }

    /**
     * Sets the ip.
     *
     * @param ip the ip statistics list
     */
    public void setIp(List<IpStat> ip) {
        this.ip = ip;
    }

    /**
     * Gets the cluster.
     *
     * @return the cluster list
     */
    public List<String> getCluster() {
        return cluster;
    }

    /**
     * Sets the cluster.
     *
     * @param cluster the cluster list
     */
    public void setCluster(List<String> cluster) {
        this.cluster = cluster;
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
