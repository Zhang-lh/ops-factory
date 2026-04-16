# Customer Cross-Overview — Comprehensive Quality Report

综合运营质量报告生成器，面向 2B 企业管理层和客户，整合四大 ITIL 流程输出 10-Sheet XLSX 工作簿。

## Quick Start

```bash
# Install dependencies
pip install openpyxl python-dotenv openai matplotlib numpy

# Generate both EN and ZH reports (native Excel charts)
python scripts/generate_report.py

# English only, matplotlib charts
python scripts/generate_report.py --language en --chart-engine matplotlib

# Skip AI insights
python scripts/generate_report.py --language zh --no-ai
```

## CLI Options

| Flag | Values | Default | Description |
|------|--------|---------|-------------|
| `--language` / `-l` | `en`, `zh`, `both` | `both` | Output language |
| `--chart-engine` | `native`, `matplotlib` | `native` | Chart rendering engine |
| `--no-ai` | — | off | Skip AI insight generation |

## Data Files

Place source data in the `data/` directory:

| File | Required | ITIL Process |
|------|----------|-------------|
| `Incidents-exported.xlsx` | Yes | Incident Management |
| `Changes-exported.xlsx` | No | Change Management |
| `Requests-exported.xlsx` | No | Service Request |
| `Problems-exported.xlsx` | No | Problem Management |

See [reference.md](reference.md) for field-level data contracts.

## Output

### Filename Convention

```
Comprehensive_Quality_Report_{TIMESTAMP}_{LANG}_{ENGINE}.xlsx
```

Example: `Comprehensive_Quality_Report_20260131_001455_EN_native_chart.xlsx`

### 10-Sheet Workbook

| # | EN Tab Name | ZH Tab Name | Prefix | Content |
|---|-------------|-------------|--------|---------|
| 1 | Executive Summary | 执行摘要 | — | Health score, KPI cards, risk radar, trends |
| 2 | INC_Analysis | INC_事件分析 | INC_ | Incident volume, priority, category, MTTR |
| 3 | INC_SLA | INC_SLA分析 | INC_ | SLA compliance gauges, violations, heatmap |
| 4 | CHG_Analysis | CHG_变更分析 | CHG_ | Change success rate, type distribution, failures |
| 5 | SRQ_Analysis | SRQ_请求分析 | SRQ_ | Request fulfillment, CSAT, department analysis |
| 6 | PRO_Analysis | PRO_问题分析 | PRO_ | Problem closure, RCA, high-impact list |
| 7 | CRO_Cross-Process | CRO_跨流程关联 | CRO_ | Change→Incident, Problem→Incident correlations |
| 8 | CRO_Personnel | CRO_人员与效率 | CRO_ | Top 10, performance matrix, skill heatmap |
| 9 | CRO_Time Analysis | CRO_时间维度 | CRO_ | Monthly trends, weekday/hour distribution |
| 10 | Action Plan | 行动计划 | — | Prioritized actions from all AI insights |

**Tab prefix convention**: `INC_` (Incident), `CHG_` (Change), `SRQ_` (Service Request), `PRO_` (Problem), `CRO_` (Cross-domain).

## Chart Engines

### Native (`--chart-engine native`)

Uses openpyxl's built-in chart objects. Best compatibility — charts render natively in Excel/WPS/LibreOffice with no image dependencies. Does not support scatter quadrant charts (Performance Matrix returns `None`).

### Matplotlib (`--chart-engine matplotlib`)

Renders high-quality PNG images embedded into Excel cells. Supports all chart types including:

- SLA dual gauges (response + resolution) with arc + track ring
- Performance Matrix scatter (Volume vs MTTR with quadrant labels)
- Donut charts (instead of solid pies)
- Single-hue blue heatmaps

Design philosophy: executive-grade, white canvas, muted palette, left-aligned titles. Restrained and premium, not flashy.

Images are proportionally scaled to target height (`CHART_HEIGHT_CM`) preserving aspect ratio, using `1 cm ≈ 37.8 px` conversion to prevent overlap in Excel.

## AI Insights

17 insight generators covering every sheet, powered by OpenAI-compatible API (configurable via `.env`). Each insight follows the format:

- **Finding** — one-sentence key observation
- **Root Cause** — data-driven analysis
- **Recommendation** — actionable measure
- **Expected Impact** — quantified improvement estimate

Results are cached for 30 days. Use `--no-ai` to skip.

## Configuration

Create `.env` in the project root:

```bash
OPENAI_API_KEY=your_api_key
OPENAI_BASE_URL=https://api.deepseek.com/v1
OPENAI_MODEL=deepseek-chat
```

## Architecture

```
scripts/
├── generate_report.py        # CLI entry point
├── config.py                 # Paths, weights, thresholds
├── analyzer.py               # Data analysis (ComprehensiveAnalyzer)
├── insight_generator.py      # AI insight generation (17 types)
├── xlsx_builder.py           # 10-sheet workbook assembly
├── xlsx_analyzer.py          # Detail analysis for each sheet
├── xlsx_theme.py             # Styles, fonts, colors, formatting
├── xlsx_chart_native.py      # Native Excel chart engine
└── xlsx_chart_matplotlib.py  # Matplotlib PNG chart engine
```
