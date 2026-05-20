/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Append Info Parser.
 * Parses the AppendInfo field format: key=value,key2=value2,...
 *
 * @author call-chain
 * @since 2026-05-14
 */
@Component
public class AppendInfoParser {

    private static final Logger log = LoggerFactory.getLogger(AppendInfoParser.class);

    /**
     * Parse AppendInfo string into a map.
     *
     * @param appendInfo the append info string
     * @return the parsed key-value map
     */
    public Map<String, String> parse(String appendInfo) {
        if (appendInfo == null || appendInfo.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return Arrays.stream(appendInfo.split(","))
            .map(String::trim)
            .filter(s -> s.contains("="))
            .map(s -> s.split("=", 2))
            .filter(parts -> parts.length == 2)
            .collect(Collectors.toMap(
                parts -> parts[0].trim(),
                parts -> parts[1].trim(),
                (a, b) -> a,
                LinkedHashMap::new
            ));
    }

    /**
     * Parse seqNo from AppendInfo.
     *
     * @param appendInfo the append info string
     * @return the seqNo value or null
     */
    public String parseSeqNo(String appendInfo) {
        return parseField(appendInfo, "seqNo");
    }

    /**
     * Parse a specific field from AppendInfo.
     *
     * @param appendInfo the append info string
     * @param fieldName the field name to extract
     * @return the field value or null
     */
    public String parseField(String appendInfo, String fieldName) {
        if (appendInfo == null || fieldName == null) {
            return null;
        }
        Map<String, String> info = parse(appendInfo);
        return info.get(fieldName);
    }

    /**
     * Parse multiple fields from AppendInfo.
     *
     * @param appendInfo the append info string
     * @param fieldNames the list of field names to extract
     * @return map of field names to values
     */
    public Map<String, String> parseFields(String appendInfo, List<String> fieldNames) {
        if (appendInfo == null || fieldNames == null) {
            return new LinkedHashMap<>();
        }
        Map<String, String> info = parse(appendInfo);
        Map<String, String> result = new LinkedHashMap<>();
        for (String fieldName : fieldNames) {
            String value = info.get(fieldName);
            if (value != null) {
                result.put(fieldName, value);
            }
        }
        return result;
    }
}
