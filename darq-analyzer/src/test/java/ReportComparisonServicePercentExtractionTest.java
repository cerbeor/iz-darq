import gov.nist.healthcare.iz.darq.analyzer.model.analysis.AnalysisReport;
import gov.nist.healthcare.iz.darq.analyzer.model.analysis.DataTable;
import gov.nist.healthcare.iz.darq.analyzer.model.analysis.DataTableRow;
import gov.nist.healthcare.iz.darq.analyzer.model.analysis.ReportSectionResult;
import gov.nist.healthcare.iz.darq.analyzer.model.comparison.ReportComparisonResult;
import gov.nist.healthcare.iz.darq.analyzer.model.comparison.RowDifferential;
import gov.nist.healthcare.iz.darq.analyzer.model.template.Comparator;
import gov.nist.healthcare.iz.darq.analyzer.service.comparison.ReportComparisonService;
import gov.nist.healthcare.iz.darq.digest.domain.Field;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Guards the Fraction -> percentage extraction in ReportComparisonService, which used to
 * be a stub returning 0.0 for every row regardless of the underlying report values.
 */
public class ReportComparisonServicePercentExtractionTest {

    private static final double DELTA = 0.0001;

    private ReportComparisonService comparisonService;

    @Before
    public void setUp() {
        comparisonService = new ReportComparisonService();
    }

    /**
     * Builds a one-section / one-table comparison out of a single base row and new row.
     */
    private RowDifferential compareSingleRow(DataTableRow baseRow, DataTableRow newRow) {
        AnalysisReport baseReport = MockReportComparisonTestFactory.createStubReport("report-base", "template-1");
        ReportSectionResult baseSection = MockReportComparisonTestFactory.createStubSection("section-1", false);
        DataTable baseTable = MockReportComparisonTestFactory.createStubTable("Measure 1.1", false);
        baseTable.getValues().add(baseRow);
        baseSection.getData().add(baseTable);
        baseReport.setSections(Collections.singletonList(baseSection));

        AnalysisReport newReport = MockReportComparisonTestFactory.createStubReport("report-new", "template-1");
        ReportSectionResult newSection = MockReportComparisonTestFactory.createStubSection("section-1", false);
        DataTable newTable = MockReportComparisonTestFactory.createStubTable("Measure 1.1", false);
        newTable.getValues().add(newRow);
        newSection.getData().add(newTable);
        newReport.setSections(Collections.singletonList(newSection));

        ReportComparisonResult result = comparisonService.compare(baseReport, newReport);
        List<RowDifferential> rows = result.getSectionDifferentials().get(0)
                .getTableDifferentials().get(0)
                .getRowDifferentials();
        assertEquals("Expected exactly one matched row differential", 1, rows.size());
        return rows.get(0);
    }

    @Test
    public void compare_WithNonZeroFractions_ExtractsActualPercentages() {
        DataTableRow baseRow = MockReportComparisonTestFactory.createStubRow(
                MockReportComparisonTestFactory.rowIdentifier(Field.PROVIDER, "IIS-A"), false,
                9560, 10000, MockReportComparisonTestFactory.threshold(Comparator.GT, 99));
        DataTableRow newRow = MockReportComparisonTestFactory.createStubRow(
                MockReportComparisonTestFactory.rowIdentifier(Field.PROVIDER, "IIS-A"), true,
                9920, 10000, MockReportComparisonTestFactory.threshold(Comparator.GT, 99));

        RowDifferential diff = compareSingleRow(baseRow, newRow);

        assertEquals(95.6, diff.getBasePercent(), DELTA);
        assertEquals(99.2, diff.getNewPercent(), DELTA);
    }

    @Test
    public void compare_WithNonZeroFractions_DerivesAggregatesFromRealValues() {
        DataTableRow baseRow = MockReportComparisonTestFactory.createStubRow(
                MockReportComparisonTestFactory.rowIdentifier(Field.AGE_GROUP, "0-2"), false,
                30, 100, null);
        DataTableRow newRow = MockReportComparisonTestFactory.createStubRow(
                MockReportComparisonTestFactory.rowIdentifier(Field.AGE_GROUP, "0-2"), true,
                50, 100, null);

        RowDifferential diff = compareSingleRow(baseRow, newRow);

        assertEquals(30.0, diff.getMinPercent(), DELTA);
        assertEquals(40.0, diff.getMeanPercent(), DELTA);
        assertEquals(50.0, diff.getMaxPercent(), DELTA);
        assertEquals(50.0, diff.getPercentOfReportsPassing(), DELTA);
    }

    @Test
    public void compare_WithAdjustedFraction_UsesEffectiveResult() {
        DataTableRow baseRow = MockReportComparisonTestFactory.createStubRowWithAdjustedFraction(
                MockReportComparisonTestFactory.rowIdentifier(Field.DETECTION, "D-1"), false, 10, 100, 75, 100);
        DataTableRow newRow = MockReportComparisonTestFactory.createStubRow(
                MockReportComparisonTestFactory.rowIdentifier(Field.DETECTION, "D-1"), true, 80, 100, null);

        RowDifferential diff = compareSingleRow(baseRow, newRow);

        assertEquals("Adjusted fraction must win over the raw result", 75.0, diff.getBasePercent(), DELTA);
        assertEquals(80.0, diff.getNewPercent(), DELTA);
    }

    @Test
    public void compare_WithZeroDenominator_YieldsZeroRatherThanNull() {
        DataTableRow baseRow = MockReportComparisonTestFactory.createStubRow(
                MockReportComparisonTestFactory.rowIdentifier(Field.GENDER, "F"), false, 0, 0, null);
        DataTableRow newRow = MockReportComparisonTestFactory.createStubRow(
                MockReportComparisonTestFactory.rowIdentifier(Field.GENDER, "F"), false, 0, 0, null);

        RowDifferential diff = compareSingleRow(baseRow, newRow);

        assertNotNull(diff.getBasePercent());
        assertEquals(0.0, diff.getBasePercent(), DELTA);
        assertEquals(0.0, diff.getNewPercent(), DELTA);
    }

    @Test
    public void compare_WithMissingResult_YieldsNullSoAggregatesSkipIt() {
        DataTableRow baseRow = MockReportComparisonTestFactory.createStubRow(
                MockReportComparisonTestFactory.rowIdentifier(Field.CODE, "08"), true);
        DataTableRow newRow = MockReportComparisonTestFactory.createStubRow(
                MockReportComparisonTestFactory.rowIdentifier(Field.CODE, "08"), true, 90, 100, null);

        RowDifferential diff = compareSingleRow(baseRow, newRow);

        assertNull("A row with no result must not report 0%", diff.getBasePercent());
        assertEquals(90.0, diff.getNewPercent(), DELTA);
        // Only the new report contributed an observation
        assertEquals(90.0, diff.getMinPercent(), DELTA);
        assertEquals(90.0, diff.getMeanPercent(), DELTA);
        assertEquals(90.0, diff.getMaxPercent(), DELTA);
        assertEquals(100.0, diff.getPercentOfReportsPassing(), DELTA);
    }

    @Test
    public void compare_RendersThresholdText() {
        DataTableRow baseRow = MockReportComparisonTestFactory.createStubRow(
                MockReportComparisonTestFactory.rowIdentifier(Field.PROVIDER, "IIS-B"), true,
                995, 1000, MockReportComparisonTestFactory.threshold(Comparator.GT, 99));
        DataTableRow newRow = MockReportComparisonTestFactory.createStubRow(
                MockReportComparisonTestFactory.rowIdentifier(Field.PROVIDER, "IIS-B"), true,
                997, 1000, MockReportComparisonTestFactory.threshold(Comparator.GT, 99));

        RowDifferential diff = compareSingleRow(baseRow, newRow);

        assertEquals(">99%", diff.getThresholdText());
        assertEquals(99.5, diff.getBasePercent(), DELTA);
        assertEquals(99.7, diff.getNewPercent(), DELTA);
    }
}
