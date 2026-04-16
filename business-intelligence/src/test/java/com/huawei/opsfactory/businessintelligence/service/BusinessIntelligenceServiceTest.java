package com.huawei.opsfactory.businessintelligence.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.opsfactory.businessintelligence.config.BusinessIntelligenceRuntimeProperties;
import com.huawei.opsfactory.businessintelligence.datasource.BiDataProvider;
import com.huawei.opsfactory.businessintelligence.datasource.BiRawData;
import com.huawei.opsfactory.businessintelligence.model.BiModels.Snapshot;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class BusinessIntelligenceServiceTest {

    @Test
    void shouldCacheOverviewWhenCacheEnabled() {
        AtomicInteger loads = new AtomicInteger();
        BusinessIntelligenceService service = new BusinessIntelligenceService(new CountingProvider(loads), runtimeProperties(true));

        Snapshot first = service.getOverview(null, null);
        Snapshot second = service.getOverview(null, null);

        assertEquals(1, loads.get());
        assertEquals(first, second);
        assertEquals(8, first.tabs().size());
        assertNotNull(first.tabContents().get("executive-summary"));
    }

    @Test
    void shouldRefreshAndExportWorkbook() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        BusinessIntelligenceService service = new BusinessIntelligenceService(new CountingProvider(loads), runtimeProperties(true));

        Snapshot refreshed = service.refresh(null, null);
        byte[] workbookBytes = service.exportCurrentWorkbook();

        assertEquals(1, loads.get());
        assertTrue(workbookBytes.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(workbookBytes))) {
            assertEquals("执行摘要", workbook.getSheetAt(0).getSheetName());
            assertEquals("SLA分析", workbook.getSheetAt(1).getSheetName());
        }

        assertArrayEquals(
            refreshed.tabs().stream().map(tab -> tab.id()).toArray(),
            service.getOverview(null, null).tabs().stream().map(tab -> tab.id()).toArray()
        );
    }

    private static BusinessIntelligenceRuntimeProperties runtimeProperties(boolean cacheEnabled) {
        BusinessIntelligenceRuntimeProperties properties = new BusinessIntelligenceRuntimeProperties();
        properties.setCacheEnabled(cacheEnabled);
        return properties;
    }

    private static final class CountingProvider implements BiDataProvider {

        private final AtomicInteger loads;

        private CountingProvider(AtomicInteger loads) {
            this.loads = loads;
        }

        @Override
        public BiRawData load() {
            loads.incrementAndGet();
            return new BiRawData(
                List.of(
                    Map.of(
                        "Order Number", "INC-001",
                        "Order Name", "Database unavailable",
                        "Priority", "P1",
                        "Order Status", "Open",
                        "Resolver", "Alice",
                        "Category", "Database",
                        "SLA Compliant", "No"
                    ),
                    Map.of(
                        "Order Number", "INC-002",
                        "Order Name", "Network alert",
                        "Priority", "P2",
                        "Order Status", "Resolved",
                        "Resolver", "Bob",
                        "Category", "Network",
                        "SLA Compliant", "Yes"
                    )
                ),
                List.of(
                    Map.of(
                        "Priority", "P1",
                        "Response (minutes)", "15",
                        "Resolution (hours)", "4"
                    )
                ),
                List.of(
                    Map.of(
                        "Change Number", "CHG-001",
                        "Change Title", "Database patch",
                        "Change Type", "Emergency",
                        "Status", "Failed",
                        "Success", "No",
                        "Incident Caused", "Yes",
                        "Backout Performed", "Yes",
                        "Implementer", "Carol",
                        "Related Incidents", "INC-001"
                    )
                ),
                List.of(
                    Map.of(
                        "Request Number", "REQ-001",
                        "Request Type", "Access",
                        "Status", "Fulfilled",
                        "SLA Met", "Yes",
                        "Assignee", "Dora",
                        "Requester Dept", "Finance",
                        "Satisfaction Score", "4.5"
                    )
                ),
                List.of(
                    Map.of(
                        "Problem Number", "PRB-001",
                        "Problem Title", "Recurring database saturation",
                        "Status", "Under Investigation",
                        "Known Error", "Yes",
                        "Root Cause", "Capacity issue",
                        "Root Cause Category", "Technical Defect",
                        "Related Incidents", "3",
                        "Resolver", "Evan"
                    )
                )
            );
        }
    }
}
