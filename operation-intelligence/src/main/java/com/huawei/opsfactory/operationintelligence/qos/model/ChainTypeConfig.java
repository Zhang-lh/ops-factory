/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Chain Type Config.
 * Represents configuration for a call chain type.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChainTypeConfig {
    private String chainType;

    private String description;

    private String conditionKey;

    private String extractFields;

    private String classifyField;

    private String subClassifyField;

    private String conditionKeyOnAppendInfo;

    private Boolean enabled;

    private Boolean saveRawLog;

    /**
     * Gets the chain type.
     *
     * @return the chain type
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
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the condition key.
     *
     * @return the condition key
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
     * Gets the extract fields.
     *
     * @return the extract fields
     */
    public String getExtractFields() {
        return extractFields;
    }

    /**
     * Sets the extract fields.
     *
     * @param extractFields the extract fields
     */
    public void setExtractFields(String extractFields) {
        this.extractFields = extractFields;
    }

    /**
     * Gets the classify field.
     *
     * @return the classify field
     */
    public String getClassifyField() {
        return classifyField;
    }

    /**
     * Sets the classify field.
     *
     * @param classifyField the classify field
     */
    public void setClassifyField(String classifyField) {
        this.classifyField = classifyField;
    }

    /**
     * Gets the sub classify field.
     *
     * @return the sub classify field
     */
    public String getSubClassifyField() {
        return subClassifyField;
    }

    /**
     * Sets the sub classify field.
     *
     * @param subClassifyField the sub classify field
     */
    public void setSubClassifyField(String subClassifyField) {
        this.subClassifyField = subClassifyField;
    }

    /**
     * Gets the condition key on append info.
     *
     * @return the condition key on append info
     */
    public String getConditionKeyOnAppendInfo() {
        return conditionKeyOnAppendInfo;
    }

    /**
     * Sets the condition key on append info.
     *
     * @param conditionKeyOnAppendInfo the condition key on append info
     */
    public void setConditionKeyOnAppendInfo(String conditionKeyOnAppendInfo) {
        this.conditionKeyOnAppendInfo = conditionKeyOnAppendInfo;
    }

    /**
     * Gets the enabled.
     *
     * @return the enabled
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Sets the enabled.
     *
     * @param enabled the enabled
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the save raw log.
     *
     * @return the save raw log
     */
    public Boolean getSaveRawLog() {
        return saveRawLog;
    }

    /**
     * Sets the save raw log.
     *
     * @param saveRawLog the save raw log
     */
    public void setSaveRawLog(Boolean saveRawLog) {
        this.saveRawLog = saveRawLog;
    }
}
