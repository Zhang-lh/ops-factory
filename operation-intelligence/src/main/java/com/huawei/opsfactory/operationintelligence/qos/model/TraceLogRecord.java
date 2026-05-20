/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Trace Log Record.
 * Represents a single tracelog entry from the DV system.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TraceLogRecord {
    private String traceId;

    private String seqNo;

    private String ip;

    private String cluster;

    private String logMessage;

    private String logTime;

    private Long cost;

    private String url;

    private String serviceName;

    private String operationName;

    private String topic;

    private String eventName;

    private String menuId;

    private String busiCode;

    private String jobDefinedId;

    private String operatorId;

    private String processName;

    private String elementName;

    private String elementType;

    private String moi;

    /**
     * Gets the trace id.
     *
     * @return the trace id
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * Sets the trace id.
     *
     * @param traceId the trace id
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

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
     * Gets the cluster.
     *
     * @return the cluster
     */
    public String getCluster() {
        return cluster;
    }

    /**
     * Sets the cluster.
     *
     * @param cluster the cluster
     */
    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    /**
     * Gets the log message.
     *
     * @return the log message
     */
    public String getLogMessage() {
        return logMessage;
    }

    /**
     * Sets the log message.
     *
     * @param logMessage the log message
     */
    public void setLogMessage(String logMessage) {
        this.logMessage = logMessage;
    }

    /**
     * Gets the log time.
     *
     * @return the log time
     */
    public String getLogTime() {
        return logTime;
    }

    /**
     * Sets the log time.
     *
     * @param logTime the log time
     */
    public void setLogTime(String logTime) {
        this.logTime = logTime;
    }

    /**
     * Gets the cost.
     *
     * @return the cost
     */
    public Long getCost() {
        return cost;
    }

    /**
     * Sets the cost.
     *
     * @param cost the cost
     */
    public void setCost(Long cost) {
        this.cost = cost;
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
     * Gets the menu id.
     *
     * @return the menu id
     */
    public String getMenuId() {
        return menuId;
    }

    /**
     * Sets the menu id.
     *
     * @param menuId the menu id
     */
    public void setMenuId(String menuId) {
        this.menuId = menuId;
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
     * Gets the job defined id.
     *
     * @return the job defined id
     */
    public String getJobDefinedId() {
        return jobDefinedId;
    }

    /**
     * Sets the job defined id.
     *
     * @param jobDefinedId the job defined id
     */
    public void setJobDefinedId(String jobDefinedId) {
        this.jobDefinedId = jobDefinedId;
    }

    /**
     * Gets the operator id.
     *
     * @return the operator id
     */
    public String getOperatorId() {
        return operatorId;
    }

    /**
     * Sets the operator id.
     *
     * @param operatorId the operator id
     */
    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
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
     * Gets the moi.
     *
     * @return the moi
     */
    public String getMoi() {
        return moi;
    }

    /**
     * Sets the moi.
     *
     * @param moi the moi
     */
    public void setMoi(String moi) {
        this.moi = moi;
    }
}
