package com.huawei.opsfactory.businessintelligence.datasource;

import java.util.List;
import java.util.Map;

public record BiRawData(
    List<Map<String, String>> incidents,
    List<Map<String, String>> incidentSlaCriteria,
    List<Map<String, String>> changes,
    List<Map<String, String>> requests,
    List<Map<String, String>> problems
) {
}

