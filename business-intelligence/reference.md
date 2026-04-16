# Data Contract - Customer Quality Report

## Overview

This report integrates data from four ITIL process areas to provide a comprehensive view of service quality for customer-facing reporting.

---

## Data Source 1: Incidents

**File**: `data/Incidents-exported.xlsx`

### Sheet: SLA_Criteria

| Field | Type | Description |
|-------|------|-------------|
| Priority | Text | P1/P2/P3/P4 |
| Response (minutes) | Number | Response time SLA threshold |
| Resolution (hours) | Number | Resolution time SLA threshold |

### Sheet: Data

| Field | Type | Used For |
|-------|------|----------|
| Order Number | Text | Unique incident identifier |
| Order Name | Text | Incident title/description |
| Begin Date | DateTime | Period analysis, trending |
| Resolution Date | DateTime | MTTR calculation |
| Resolver | Text | Team performance analysis |
| Category | Text | Risk detection, categorization |
| Priority | Text | P1/P2 counting, SLA mapping |
| Resolution Time(m) | Number | MTTR calculation |
| Order Status | Text | Backlog counting |
| SLA Compliant | Text | Yes/No - SLA compliance flag |

---

## Data Source 2: Changes

**File**: `data/Changes-exported.xlsx`

### Sheet: Data

| Field | Type | Description | Used For |
|-------|------|-------------|----------|
| Change Number | Text | Unique identifier (CHG*) | Identification |
| Change Title | Text | Brief description | Display |
| Change Type | Text | Standard/Normal/Emergency | Emergency change tracking |
| Priority | Text | P1/P2/P3/P4 | Risk analysis |
| Risk Level | Text | Low/Medium/High/Critical | Risk scoring |
| Status | Text | Submitted/Approved/Scheduled/Implemented/Closed/Failed/Cancelled | Success rate |
| Requested Date | DateTime | When change was requested | Timeline analysis |
| Planned Start | DateTime | Scheduled start | Planning accuracy |
| Planned End | DateTime | Scheduled end | Planning accuracy |
| Actual Start | DateTime | Real start time | Execution analysis |
| Actual End | DateTime | Real end time | Duration calculation |
| Implementer | Text | Person/team implementing | Team metrics |
| Approver | Text | CAB/Manager approving | Audit trail |
| Category | Text | Application/Infrastructure/Database/Network/Security | Categorization |
| Impact | Text | Low/Medium/High | Impact analysis |
| CI Affected | Text | Configuration Item name | CMDB integration |
| Success | Text | Yes/No | **Success rate calculation** |
| Incident Caused | Text | Yes/No | **Change-induced incidents** |
| Related Incidents | Text | Comma-separated INC numbers | Correlation |
| Backout Performed | Text | Yes/No | Rollback tracking |

### Computed Metrics

| Metric | Formula |
|--------|---------|
| Change Success Rate | Count(Success=Yes) / Total Changes |
| Emergency Change Ratio | Count(Type=Emergency) / Total Changes |
| Change-Induced Incident Rate | Count(Incident Caused=Yes) / Total Changes |
| Avg Change Duration | Mean(Actual End - Actual Start) |
| Planning Accuracy | Count(OnTime) / Total Implemented |

---

## Data Source 3: Service Requests

**File**: `data/Requests-exported.xlsx`

### Sheet: Data

| Field | Type | Description | Used For |
|-------|------|-------------|----------|
| Request Number | Text | Unique identifier (REQ*) | Identification |
| Request Title | Text | Brief description | Display |
| Request Type | Text | Access/Provisioning/Information/Standard Change | Categorization |
| Priority | Text | P1/P2/P3/P4 | SLA mapping |
| Status | Text | New/In Progress/Pending/Fulfilled/Cancelled/Rejected | Fulfillment tracking |
| Requested Date | DateTime | When request was submitted | Volume trending |
| Due Date | DateTime | SLA due date | SLA compliance |
| Fulfilled Date | DateTime | When completed | Fulfillment time |
| Requester | Text | User who submitted | Demand analysis |
| Requester Dept | Text | Department | Departmental analysis |
| Assignee | Text | Fulfillment team/person | Workload analysis |
| Category | Text | User Access/Account/Billing/Provisioning/Query | Categorization |
| Fulfillment Time(h) | Number | Hours to fulfill | **Avg fulfillment time** |
| SLA Met | Text | Yes/No | **SLA compliance** |
| Satisfaction Score | Number | 1-5 scale | **CSAT calculation** |
| Feedback | Text | User comments | Sentiment analysis |

### Computed Metrics

| Metric | Formula |
|--------|---------|
| Request Fulfillment Rate | Count(Status=Fulfilled) / Total Requests |
| Request SLA Compliance | Count(SLA Met=Yes) / Total Fulfilled |
| Avg Fulfillment Time | Mean(Fulfillment Time(h)) |
| CSAT Score | Mean(Satisfaction Score) |
| Request Volume Trend | Count per period |

---

## Data Source 4: Problems

**File**: `data/Problems-exported.xlsx`

### Sheet: Data

| Field | Type | Description | Used For |
|-------|------|-------------|----------|
| Problem Number | Text | Unique identifier (PRB*) | Identification |
| Problem Title | Text | Brief description | Display |
| Priority | Text | P1/P2/P3/P4 | Prioritization |
| Status | Text | New/Under Investigation/Known Error/Resolved/Closed | Lifecycle tracking |
| Logged Date | DateTime | When problem was created | Age calculation |
| Target Resolution | DateTime | Expected resolution date | SLA tracking |
| Resolution Date | DateTime | When resolved/closed | Resolution time |
| Root Cause | Text | Identified root cause | **RCA completion** |
| Root Cause Category | Text | Human Error/Process Gap/Technical Defect/Vendor Issue/Unknown | Categorization |
| Workaround Available | Text | Yes/No | Mitigation status |
| Known Error | Text | Yes/No | KEDB status |
| Related Incidents | Number | Count of linked incidents | **Incident correlation** |
| Category | Text | Application/Infrastructure/Database/Network/Security | Categorization |
| CI Affected | Text | Configuration Item name | CMDB integration |
| Assignee | Text | Investigation team/person | Workload |
| Resolver | Text | Person who resolved | Team metrics |
| Permanent Fix Implemented | Text | Yes/No | **Problem closure quality** |

### Computed Metrics

| Metric | Formula |
|--------|---------|
| Problem Resolution Rate | Count(Status in Resolved,Closed) / Total Problems |
| RCA Completion Rate | Count(Root Cause != blank) / Total Problems |
| Known Error Ratio | Count(Known Error=Yes) / Total Problems |
| Avg Problem Age | Mean(Today - Logged Date) for open problems |
| Incident-to-Problem Ratio | Total Related Incidents / Total Problems |
| Permanent Fix Rate | Count(Permanent Fix=Yes) / Count(Resolved) |

---

## Cross-Process Metrics

| Metric | Formula | Weight in Health Score |
|--------|---------|----------------------|
| Incident SLA Rate | Incident SLA compliance | 25% |
| Incident MTTR | Average resolution time | 15% |
| P1/P2 Volume | High priority incident count | 10% |
| Change Success Rate | Successful changes ratio | 15% |
| Change-Induced Incidents | Changes causing incidents | 10% |
| Request SLA Rate | Request fulfillment SLA | 10% |
| Request CSAT | Customer satisfaction | 10% |
| Problem Closure Rate | Problems resolved | 5% |

---

## Period Definitions

| Period | Definition |
|--------|------------|
| Current Week | Last 7 days from report date |
| Previous Week | 8-14 days before report date |
| Current Month | Last 30 days from report date |
| Previous Month | 31-60 days before report date |

---

## Risk Radar Rules

| Rule ID | Condition | Severity | Message |
|---------|-----------|----------|---------|
| R001 | Incident SLA Rate < 70% | CRITICAL | SLA compliance at risk |
| R002 | Any Category MTTR > 48h | WARNING | Extended resolution times |
| R003 | Change Success Rate < 90% | WARNING | Change failure rate elevated |
| R004 | Emergency Change Ratio > 20% | WARNING | High emergency change volume |
| R005 | Change-Induced Incidents > 5% | CRITICAL | Changes causing incidents |
| R006 | Request CSAT < 3.5 | WARNING | Customer satisfaction declining |
| R007 | Open Problem Age > 30 days | ATTENTION | Aging problems require attention |
| R008 | RCA Completion Rate < 80% | WARNING | Root cause analysis gaps |
| R009 | P1/P2 Incident Spike > 50% WoW | CRITICAL | High priority incident surge |
| R010 | Backlog > 10% of monthly volume | WARNING | Capacity concerns |
