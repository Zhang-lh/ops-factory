/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.service;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.dv.DvClient;
import com.huawei.opsfactory.operationintelligence.qos.model.CallChainTree;
import com.huawei.opsfactory.operationintelligence.qos.model.ChainTypeConfig;
import com.huawei.opsfactory.operationintelligence.qos.model.TraceLogRecord;
import com.huawei.opsfactory.operationintelligence.qos.parser.TimeSplitStrategy;
import com.huawei.opsfactory.operationintelligence.qos.store.CallChainStore;
import com.huawei.opsfactory.operationintelligence.qos.store.ChainTypeConfigStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Call Chain Service Test.
 *
 * @author call-chain
 * @since 2026-05-18
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CallChainServiceTest {

    @Mock
    private OperationIntelligenceProperties properties;

    @Mock
    private DvClient dvClient;

    @Mock
    private CallChainBuilder chainBuilder;

    @Mock
    private CallChainStore chainStore;

    @Mock
    private ChainTypeConfigStore configStore;

    @Mock
    private TimeSplitStrategy timeSplitStrategy;

    private CallChainService callChainService;

    @BeforeEach
    void setUp() {
        callChainService = new CallChainService(properties, dvClient, chainBuilder,
            chainStore, configStore, timeSplitStrategy);
    }

    @Test
    void testQueryCallChainSuccess() {
        OperationIntelligenceProperties.CallChain callChain = new OperationIntelligenceProperties.CallChain();
        callChain.setQuerySize(100);
        when(properties.getCallChain()).thenReturn(callChain);
        when(configStore.loadAll()).thenReturn(List.of());
        when(dvClient.fetchTraceLogEntries(anyString(), anyString(), anyString(), anyList(), any(), anyLong(), anyLong(), anyInt()))
            .thenReturn(List.of());

        CallChainTree tree = new CallChainTree();
        tree.setChainType(null);
        tree.setFlows(new ArrayList<>());
        tree.setTotalCount(0L);
        when(chainBuilder.build(anyString(), anyString(), anyString(), anyList(), anyLong()))
            .thenReturn(tree);

        Mono<CallChainTree> result = callChainService.queryCallChain(
            "DigitalCRM.sit",
            List.of(Map.of("conditionKey", "menuId", "conditionValue", "604015020")),
            1746057600000L,
            1746662400000L
        );

        CallChainTree actual = result.block();
        assertNotNull(actual);
    }

    @Test
    void testImportChainTypeConfigs() {
        String content = "BES|Business Execution System|menuId|url|null|null\n"
            + "API|External Interface|serviceName|url,serviceName|null|null";

        int count = callChainService.importChainTypeConfigs(content);

        assertEquals(2, count);
    }

    @Test
    void testImportChainTypeConfigsWithComments() {
        String content = "# Comment line\n"
            + "BES|Business Execution System|menuId|url|null|null\n"
            + "\n"
            + "API|External Interface|serviceName|url,serviceName|null|null";

        int count = callChainService.importChainTypeConfigs(content);

        assertEquals(2, count);
    }
}