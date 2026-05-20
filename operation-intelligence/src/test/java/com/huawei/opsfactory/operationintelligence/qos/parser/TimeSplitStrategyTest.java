/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.parser;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Time Split Strategy Test.
 *
 * @author call-chain
 * @since 2026-05-18
 */
class TimeSplitStrategyTest {

    private TimeSplitStrategy strategy;

    @BeforeEach
    void setUp() {
        OperationIntelligenceProperties properties = new OperationIntelligenceProperties();
        OperationIntelligenceProperties.CallChain callChain = new OperationIntelligenceProperties.CallChain();
        OperationIntelligenceProperties.CallChain.TimeSplit timeSplit = new OperationIntelligenceProperties.CallChain.TimeSplit();
        timeSplit.setInitialMinutes(30);
        timeSplit.setDegradeMinutes(List.of(15L, 5L, 1L));
        callChain.setTimeSplit(timeSplit);
        properties.setCallChain(callChain);

        strategy = new TimeSplitStrategy(properties);
    }

    @Test
    void testSplitIfNeededNoSplitNeeded() {
        long startTime = 1000000L;
        long endTime = 2000000L;

        List<TimeSplitStrategy.TimeRange> ranges = strategy.splitIfNeeded(startTime, endTime, 5000);

        assertEquals(1, ranges.size());
        assertEquals(startTime, ranges.get(0).startTime());
        assertEquals(endTime, ranges.get(0).endTime());
    }

    @Test
    void testSplitIfNeededSplitNeeded() {
        long startTime = 1000000L;
        long endTime = 10000000L; // 9 seconds, enough to split with 30 minute initial split

        List<TimeSplitStrategy.TimeRange> ranges = strategy.splitIfNeeded(startTime, endTime, 10001);

        assertTrue(ranges.size() > 1);
    }

    @Test
    void testSplit() {
        long startTime = 1000000L;
        long endTime = 4000000L;
        long splitMs = 1000000L;

        List<TimeSplitStrategy.TimeRange> ranges = strategy.split(startTime, endTime, splitMs);

        assertEquals(3, ranges.size());
        assertEquals(1000000L, ranges.get(0).startTime());
        assertEquals(2000000L, ranges.get(0).endTime());
        assertEquals(2000000L, ranges.get(1).startTime());
        assertEquals(3000000L, ranges.get(1).endTime());
        assertEquals(3000000L, ranges.get(2).startTime());
        assertEquals(4000000L, ranges.get(2).endTime());
    }

    @Test
    void testGetNextDegradeSplitMs() {
        long currentSplitMs = 1800000L; // 30 minutes

        long nextSplit = strategy.getNextDegradeSplitMs(currentSplitMs);

        assertEquals(900000L, nextSplit); // 15 minutes
    }

    @Test
    void testGetNextDegradeSplitMsNoFurtherDegradation() {
        long currentSplitMs = 60000L; // 1 minute (minimum in config)

        long nextSplit = strategy.getNextDegradeSplitMs(currentSplitMs);

        assertEquals(60000L, nextSplit);
    }

    @Test
    void testCanDegradeFurther() {
        long currentSplitMs = 1800000L; // 30 minutes

        assertTrue(strategy.canDegradeFurther(currentSplitMs));
    }

    @Test
    void testCannotDegradeFurther() {
        long currentSplitMs = 60000L; // 1 minute

        assertFalse(strategy.canDegradeFurther(currentSplitMs));
    }

    @Test
    void testTimeRangeDuration() {
        TimeSplitStrategy.TimeRange range = new TimeSplitStrategy.TimeRange(1000000L, 2000000L);

        assertEquals(1000000L, range.duration());
    }

    @Test
    void testTimeRangeIsValid() {
        TimeSplitStrategy.TimeRange validRange = new TimeSplitStrategy.TimeRange(1000000L, 2000000L);
        TimeSplitStrategy.TimeRange invalidRange = new TimeSplitStrategy.TimeRange(2000000L, 1000000L);

        assertTrue(validRange.isValid());
        assertFalse(invalidRange.isValid());
    }
}