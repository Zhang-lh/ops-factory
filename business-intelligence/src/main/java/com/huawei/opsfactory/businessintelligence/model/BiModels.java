package com.huawei.opsfactory.businessintelligence.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class BiModels {

    private BiModels() {
    }

    public record MetricCard(
        String id,
        String label,
        String value,
        String tone
    ) {
    }

    public record ChartDatum(
        String label,
        double value
    ) {
    }

    public record ChartSection(
        String id,
        String title,
        String type,
        List<ChartDatum> items,
        ChartConfig config
    ) {
        // Backward compatible constructor
        public ChartSection(String id, String title, String type, List<ChartDatum> items) {
            this(id, title, type, items, null);
        }
    }

    public record ChartConfig(
        List<String> series,       // Series names for multi-series charts
        Map<String, List<ChartDatum>> seriesData,  // Data for each series
        List<String> colors,       // Custom colors
        String xAxisLabel,
        String yAxisLabel
    ) {
    }

    public record TableSection(
        String id,
        String title,
        List<String> columns,
        List<List<String>> rows
    ) {
    }

    public record TabMeta(
        String id,
        String label
    ) {
    }

    public record ExecutiveHero(
        String score,
        String grade,
        String summary,
        String changeHint,
        String periodLabel
    ) {
    }

    public record ProcessHealth(
        String id,
        String label,
        String score,
        String tone,
        String summary
    ) {
    }

    public record RiskSummary(
        int critical,
        int warning,
        int attention,
        List<ExecutiveRisk> topRisks
    ) {
    }

    public record ExecutiveRisk(
        String id,
        String priority,
        String title,
        String impact,
        String process,
        String value
    ) {
    }

    public record TrendPoint(
        String label,
        double score,
        double signal
    ) {
    }

    public record TrendSection(
        String title,
        String subtitle,
        List<TrendPoint> points
    ) {
    }

    public record ExecutiveSummary(
        ExecutiveHero hero,
        List<ProcessHealth> processHealths,
        RiskSummary riskSummary,
        TrendSection trend
    ) {
    }

    public record SlaHero(
        String summary,
        String overallComplianceRate,
        String responseComplianceRate,
        String resolutionComplianceRate,
        long breachedCount,
        String highPriorityComplianceRate
    ) {
    }

    public record SlaDimensionCard(
        String title,
        String complianceRate,
        String averageDuration,
        String p90Duration,
        long breachedCount,
        String tone,
        String assessment
    ) {
    }

    public record SlaPriorityRow(
        String priority,
        long totalCount,
        String responseComplianceRate,
        String resolutionComplianceRate,
        long breachedCount,
        String averageResolutionDuration
    ) {
    }

    public record SlaComparisonDatum(
        String priority,
        double responseComplianceRate,
        double resolutionComplianceRate
    ) {
    }

    public record SlaComparisonChart(
        String title,
        List<SlaComparisonDatum> items
    ) {
    }

    public record SlaRiskRow(
        String label,
        long totalCount,
        String responseComplianceRate,
        String resolutionComplianceRate,
        long breachedCount,
        String averageResolutionDuration
    ) {
    }

    public record SlaTrendPoint(
        String period,
        double overallComplianceRate,
        double responseComplianceRate,
        double resolutionComplianceRate,
        long breachedCount
    ) {
    }

    public record SlaViolationBreakdown(
        long responseBreached,
        long resolutionBreached,
        long bothBreached
    ) {
    }

    public record SlaViolationSample(
        String orderNumber,
        String orderName,
        String priority,
        String category,
        String resolver,
        String responseDuration,
        String resolutionDuration,
        String violationType
    ) {
    }

    public record SlaAnalysisSummary(
        SlaHero hero,
        SlaDimensionCard response,
        SlaDimensionCard resolution,
        List<SlaPriorityRow> priorityRows,
        SlaComparisonChart priorityComparison,
        List<SlaRiskRow> categoryRisks,
        List<SlaRiskRow> resolverRisks,
        List<SlaTrendPoint> trends,
        SlaViolationBreakdown violationBreakdown,
        List<SlaViolationSample> violationSamples
    ) {
    }

    public record TabContent(
        String id,
        String label,
        String description,
        ExecutiveSummary executiveSummary,
        SlaAnalysisSummary slaAnalysis,
        List<MetricCard> cards,
        List<ChartSection> charts,
        List<TableSection> tables
    ) {
    }

    public record Snapshot(
        Instant refreshedAt,
        List<TabMeta> tabs,
        Map<String, TabContent> tabContents
    ) {
    }
}
