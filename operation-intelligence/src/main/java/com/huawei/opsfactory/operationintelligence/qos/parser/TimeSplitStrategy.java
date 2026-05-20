/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.parser;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Time Split Strategy.
 * Handles time range splitting when DV query results exceed the limit.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@Component
public class TimeSplitStrategy {

    private static final Logger log = LoggerFactory.getLogger(TimeSplitStrategy.class);

    private final OperationIntelligenceProperties.CallChain.TimeSplit config;

    private static final int DV_MAX_RESULTS = 10000;

    /**
     * Time Split Strategy.
     *
     * @param properties the operation intelligence properties
     */
    public TimeSplitStrategy(OperationIntelligenceProperties properties) {
        this.config = properties.getCallChain().getTimeSplit();
    }

    /**
     * Split time range if total number exceeds DV limit.
     *
     * @param startTime the start time in milliseconds
     * @param endTime the end time in milliseconds
     * @param totalNumber the total number of results
     * @return list of time ranges
     */
    public List<TimeRange> splitIfNeeded(long startTime, long endTime, int totalNumber) {
        if (totalNumber < DV_MAX_RESULTS) {
            return List.of(new TimeRange(startTime, endTime));
        }

        log.info("DV query returned {} results (max limit), splitting time range", totalNumber);
        return split(startTime, endTime, config.getInitialMinutes() * 60 * 1000);
    }

    /**
     * Split time range with specified interval.
     *
     * @param startTime the start time
     * @param endTime the end time
     * @param splitMs the split interval in milliseconds
     * @return list of time ranges
     */
    public List<TimeRange> split(long startTime, long endTime, long splitMs) {
        List<TimeRange> ranges = new ArrayList<>();

        for (long t = startTime; t < endTime; t += splitMs) {
            long rangeEnd = Math.min(t + splitMs, endTime);
            ranges.add(new TimeRange(t, rangeEnd));
        }

        if (ranges.size() > 1) {
            log.info("Split time range into {} parts with {}ms interval", ranges.size(), splitMs);
        }

        return ranges;
    }

    /**
     * Get the next degraded split interval.
     *
     * @param currentSplitMs the current split interval
     * @return the next degraded interval or the current one if no more degradation available
     */
    public long getNextDegradeSplitMs(long currentSplitMs) {
        List<Long> degrades = config.getDegradeMinutes();
        long currentMinutes = currentSplitMs / 60000;

        for (Long degrade : degrades) {
            if (currentMinutes > degrade) {
                long degradedMs = degrade * 60 * 1000;
                log.info("Degrading split interval from {}ms to {}ms", currentSplitMs, degradedMs);
                return degradedMs;
            }
        }

        log.warn("No further degradation available, using current split interval: {}ms", currentSplitMs);
        return currentSplitMs;
    }

    /**
     * Check if further degradation is possible.
     *
     * @param currentSplitMs the current split interval
     * @return true if further degradation is possible
     */
    public boolean canDegradeFurther(long currentSplitMs) {
        List<Long> degrades = config.getDegradeMinutes();
        long currentMinutes = currentSplitMs / 60000;

        for (Long degrade : degrades) {
            if (currentMinutes > degrade) {
                return true;
            }
        }
        return false;
    }

    /**
     * Time Range record.
     *
     * @param startTime the start time in milliseconds
     * @param endTime the end time in milliseconds
     */
    public record TimeRange(long startTime, long endTime) {
        /**
         * Get the duration of this time range.
         *
         * @return the duration in milliseconds
         */
        public long duration() {
            return endTime - startTime;
        }

        /**
         * Check if this time range is valid.
         *
         * @return true if start time is before end time
         */
        public boolean isValid() {
            return startTime < endTime;
        }
    }
}
