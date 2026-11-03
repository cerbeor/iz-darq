package gov.nist.healthcare.iz.darq.controller.route;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.healthcare.iz.darq.analyzer.model.analysis.AnalysisReport;
import gov.nist.healthcare.iz.darq.analyzer.service.comparison.ReportComparisonService;
import gov.nist.healthcare.iz.darq.service.impl.AnalysisReportService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises GET /api/report/compare end to end through the web layer: controller to
 * ReportComparisonService to the serialized JSON response.
 *
 * This guards the regression where ReportComparisonService.extractPercent was a stub
 * returning 0.0, so every basePercent/newPercent in the payload read 0.0 regardless of
 * the underlying Fraction. A unit test on the service alone cannot catch a percentage
 * that survives the service but is then dropped on the wire, which is what this covers.
 *
 * Reports are loaded from the checked-in fixtures in mock-data/, shared from
 * darq-analyzer's test-jar and produced by MockComparisonDataGeneratorTest.
 */
public class ReportComparisonEndpointTest {

    private static final double DELTA = 0.001;

    /** JSON path to the single row of the n-th measure table in section 1. */
    private static String row(int tableIndex) {
        return "$.sectionDifferentials[0].tableDifferentials[" + tableIndex + "].rowDifferentials[0]";
    }

    private MockMvc mockMvc;
    private AnalysisReportService analysisReportService;

    @Before
    public void setUp() throws Exception {
        AnalysisReport baseReport = loadFixture("/mock-data/base-report-2023.json");
        AnalysisReport newReport = loadFixture("/mock-data/new-report-2024.json");

        analysisReportService = mock(AnalysisReportService.class);
        when(analysisReportService.findOne("mock-report-2023")).thenReturn(baseReport);
        when(analysisReportService.findOne("mock-report-2024")).thenReturn(newReport);

        ReportController controller = new ReportController();
        ReflectionTestUtils.setField(controller, "analysisReportService", analysisReportService);
        ReflectionTestUtils.setField(controller, "reportComparisonService", new ReportComparisonService());

        // Standalone setup: no Spring context, no Mongo, and the @PreAuthorize chain is
        // not applied, which keeps the test focused on the comparison payload.
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private AnalysisReport loadFixture(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // Derived read-only properties ("public") serialize but have no setter.
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture on the test classpath: " + resource);
            }
            return mapper.readValue(in, AnalysisReport.class);
        }
    }

    private org.springframework.test.web.servlet.ResultActions compare() throws Exception {
        return mockMvc.perform(get("/api/report/compare")
                        .param("id", "mock-report-2023")
                        .param("id", "mock-report-2024"))
                .andExpect(status().isOk());
    }

    @Test
    public void compare_ReturnsActualPercentages_NotZero() throws Exception {
        compare()
                .andExpect(jsonPath("$.sectionDifferentials", hasSize(1)))
                .andExpect(jsonPath("$.sectionDifferentials[0].tableDifferentials", hasSize(6)))
                // Measure 1.1 - both reports at 100%
                .andExpect(jsonPath(row(0) + ".basePercent").value(closeTo(100.0, DELTA)))
                .andExpect(jsonPath(row(0) + ".newPercent").value(closeTo(100.0, DELTA)))
                // Measure 1.2 - 99.00% -> 99.10%, an improvement that crosses the threshold
                .andExpect(jsonPath(row(1) + ".basePercent").value(closeTo(99.0, DELTA)))
                .andExpect(jsonPath(row(1) + ".newPercent").value(closeTo(99.1, DELTA)))
                // Measure 2.1 - 95.60% -> 84.00%, a regression
                .andExpect(jsonPath(row(2) + ".basePercent").value(closeTo(95.6, DELTA)))
                .andExpect(jsonPath(row(2) + ".newPercent").value(closeTo(84.0, DELTA)))
                // Measure 2.2 - low-end values, well away from any round number
                .andExpect(jsonPath(row(3) + ".basePercent").value(closeTo(12.34, DELTA)))
                .andExpect(jsonPath(row(3) + ".newPercent").value(closeTo(45.67, DELTA)))
                // Measure 3.1 - a "less than" threshold
                .andExpect(jsonPath(row(4) + ".basePercent").value(closeTo(2.1, DELTA)))
                .andExpect(jsonPath(row(4) + ".newPercent").value(closeTo(1.75, DELTA)));
    }

    @Test
    public void compare_OnlyTheEmptyDenominatorMeasureReportsZero() throws Exception {
        org.springframework.test.web.servlet.ResultActions result = compare();

        // Measures 1.1 through 3.1 all have data, so a 0.0 there is the regression
        for (int table = 0; table < 5; table++) {
            result.andExpect(jsonPath(row(table) + ".basePercent").value(not(closeTo(0.0, DELTA))))
                  .andExpect(jsonPath(row(table) + ".newPercent").value(not(closeTo(0.0, DELTA))));
        }

        // Measure 3.2 has an empty denominator, so 0.0 is the correct answer there
        result.andExpect(jsonPath(row(5) + ".basePercent").value(closeTo(0.0, DELTA)))
              .andExpect(jsonPath(row(5) + ".newPercent").value(closeTo(0.0, DELTA)));
    }

    @Test
    public void compare_SerializesAggregatesDerivedFromRealValues() throws Exception {
        // Measure 2.1 spans 84.00% and 95.60%
        compare()
                .andExpect(jsonPath(row(2) + ".minPercent").value(closeTo(84.0, DELTA)))
                .andExpect(jsonPath(row(2) + ".meanPercent").value(closeTo(89.8, DELTA)))
                .andExpect(jsonPath(row(2) + ".maxPercent").value(closeTo(95.6, DELTA)))
                // Met in the base report, not met in the new one
                .andExpect(jsonPath(row(2) + ".percentOfReportsPassing").value(closeTo(50.0, DELTA)));
    }

    @Test
    public void compare_SerializesThresholdAndPassFlags() throws Exception {
        compare()
                .andExpect(jsonPath(row(2) + ".thresholdText").value(">85%"))
                .andExpect(jsonPath(row(2) + ".basePass").value(true))
                .andExpect(jsonPath(row(2) + ".newPass").value(false))
                .andExpect(jsonPath(row(2) + ".passStatusChanged").value(true))
                // A "less than" threshold must render with the right comparator
                .andExpect(jsonPath(row(4) + ".thresholdText").value("<5%"));
    }
}
