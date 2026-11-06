import gov.nist.healthcare.iz.darq.analyzer.model.comparison.ReportComparisonResult;
import gov.nist.healthcare.iz.darq.analyzer.model.comparison.RowDifferential;
import gov.nist.healthcare.iz.darq.analyzer.model.comparison.SectionDifferential;
import gov.nist.healthcare.iz.darq.analyzer.model.comparison.TableDifferential;
import gov.nist.healthcare.iz.darq.analyzer.service.comparison.ReportComparisonCsvExportService;
import gov.nist.healthcare.iz.darq.digest.domain.Field;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The CSV is expected to carry the same columns the Angular comparison table
 * renders (comparison-table.component.html), plus Section/Table context columns
 * that the UI gets from its headings rather than from the grid itself.
 */
public class ReportComparisonCsvExportServiceTest {

    private ReportComparisonCsvExportService exportService;

    @Before
    public void setUp() {
        exportService = new ReportComparisonCsvExportService();
    }

    @Test
    public void exportToCsv_WithNullResult_GeneratesHeaderOnly() {
        String csv = exportService.exportToCsv(new ReportComparisonResult(), "2023", "2024");

        String expectedHeader = "Section,Table,Threshold,2023,2023 Outcome,2024,2024 Outcome,% Point Change,Measure Outcome\n";
        assertEquals("Should generate only the header line", expectedHeader, csv);
    }

    @Test
    public void exportToCsv_HeaderCarriesOneColumnPerIdentifierField() {
        // The UI draws one sortable column per identifier field, labelled via
        // identifierFieldLabel(): PROVIDER -> "Reporting Group".
        ReportComparisonResult result = singleRowResult(
                rowWithIdentifier(identifier(Field.PROVIDER, "RG-1", Field.AGE_GROUP, "0-2")));

        String csv = exportService.exportToCsv(result, "2023", "2024");
        String header = csv.split(ReportComparisonCsvExportService.NEW_LINE)[0];

        assertEquals("Identifier columns should sit between Table and Threshold, using UI labels",
                "Section,Table,Reporting Group,Age Group,Threshold,2023,2023 Outcome,2024,2024 Outcome,% Point Change,Measure Outcome",
                header);
    }

    @Test
    public void exportToCsv_IdentifierColumnsAreUnionAcrossTables_AndAlignPerRow() {
        // Each on screen table has its own identifier columns; a flat CSV needs the
        // union, with '-' where a row does not carry that field.
        ReportComparisonResult result = new ReportComparisonResult();
        SectionDifferential section = new SectionDifferential();
        section.setSectionPath("1");
        section.setSectionHeader("Section One");

        TableDifferential providerTable = new TableDifferential();
        providerTable.setTableCaption("By Provider");
        providerTable.setRowDifferentials(Collections.singletonList(
                rowWithIdentifier(identifier(Field.PROVIDER, "RG-1"))));

        TableDifferential detectionTable = new TableDifferential();
        detectionTable.setTableCaption("By Detection");
        detectionTable.setRowDifferentials(Collections.singletonList(
                rowWithIdentifier(identifier(Field.DETECTION, "D-7"))));

        section.getTableDifferentials().add(providerTable);
        section.getTableDifferentials().add(detectionTable);
        result.setSectionDifferentials(Collections.singletonList(section));

        String csv = exportService.exportToCsv(result, "2023", "2024");
        String[] lines = csv.split(ReportComparisonCsvExportService.NEW_LINE);

        assertTrue("Header should hold the union of identifier fields",
                lines[0].startsWith("Section,Table,Reporting Group,Detection Type,Threshold,"));
        assertTrue("Provider row should fill its column and blank the detection one",
                lines[1].startsWith("1. Section One,By Provider,RG-1,-,"));
        assertTrue("Detection row should blank the provider column",
                lines[2].startsWith("1. Section One,By Detection,-,D-7,"));
    }

    @Test
    public void exportToCsv_WithStatusTransitions_CalculatesOutcomesCorrectly() {
        ReportComparisonResult result = new ReportComparisonResult();
        SectionDifferential section = new SectionDifferential();
        TableDifferential table = new TableDifferential();
        table.setTableCaption("Test Measure");
        table.setRowDifferentials(new ArrayList<>());

        // Row 1: Met -> Met (No Change)
        table.getRowDifferentials().add(createTestRow(true, 100.0, true, 100.0, ">99%"));
        // Row 2: Not Met -> Met (Better)
        table.getRowDifferentials().add(createTestRow(false, 99.0, true, 99.1, ">99%"));
        // Row 3: Met -> Not Met (Worse)
        table.getRowDifferentials().add(createTestRow(true, 95.6, false, 84.0, ">85%"));

        section.getTableDifferentials().add(table);
        result.setSectionDifferentials(Collections.singletonList(section));

        String csv = exportService.exportToCsv(result, "2023", "2024");
        String[] lines = csv.split(ReportComparisonCsvExportService.NEW_LINE);

        assertEquals("Should have 1 header and 3 data rows", 4, lines.length);

        // Assert Row 1 - No Change
        assertTrue("Row 1 should map to 'Met' for both and 'No Change'",
                lines[1].contains("Met,100.00%,Met,0.00%,No Change"));

        // Assert Row 2 - Better, rendered with a leading '+' like the UI
        assertTrue("Row 2 should transition to Better with +0.10% change",
                lines[2].contains("Not Met,99.10%,Met,+0.10%,Better"));

        // Assert Row 3 - Worse
        assertTrue("Row 3 should transition to Worse with -11.60% change",
                lines[3].contains("Met,84.00%,Not Met,-11.60%,Worse"));
    }

    @Test
    public void exportToCsv_WithMissingRow_MirrorsTheMissingNotice() {
        // The UI collapses the four trailing cells into "Missing in <new label>".
        ReportComparisonResult result = new ReportComparisonResult();
        SectionDifferential section = new SectionDifferential();
        TableDifferential table = new TableDifferential();
        table.setTableCaption("Dropped Measure");

        RowDifferential row = createTestRow(true, 97.5, false, null, ">95%");
        row.setMissingInNewReport(true);

        table.setRowDifferentials(Collections.singletonList(row));
        section.getTableDifferentials().add(table);
        result.setSectionDifferentials(Collections.singletonList(section));

        String csv = exportService.exportToCsv(result, "2023", "2024");
        String[] lines = csv.split(ReportComparisonCsvExportService.NEW_LINE);

        assertTrue("Base columns should still be populated", lines[1].contains("97.50%,Met,"));
        assertTrue("Missing notice should name the new report", lines[1].contains("Missing in 2024"));
        assertEquals("Row should keep the full column count",
                countSeparators(lines[0]), countSeparators(lines[1]));
    }

    @Test
    public void exportToCsv_WithNestedChildSections_IncludesChildRows() {
        // Real reports nest their data tables inside child sections; the top-level
        // section is only a container with no tables of its own.
        ReportComparisonResult result = new ReportComparisonResult();

        SectionDifferential parent = new SectionDifferential();
        parent.setSectionHeader("Parent Container");
        // NOTE: parent intentionally has no table differentials

        SectionDifferential child = new SectionDifferential();
        child.setSectionPath("1.1");
        child.setSectionHeader("Child Section");
        TableDifferential table = new TableDifferential();
        table.setTableCaption("Nested Measure");
        table.setRowDifferentials(Collections.singletonList(createTestRow(false, 90.0, true, 95.0, ">95%")));
        child.getTableDifferentials().add(table);

        SectionDifferential grandChild = new SectionDifferential();
        grandChild.setSectionPath("1.1.1");
        grandChild.setSectionHeader("Grand Child Section");
        TableDifferential deepTable = new TableDifferential();
        deepTable.setTableCaption("Deep Measure");
        deepTable.setRowDifferentials(Collections.singletonList(createTestRow(true, 99.0, false, 80.0, ">85%")));
        grandChild.getTableDifferentials().add(deepTable);
        child.getChildren().add(grandChild);

        parent.getChildren().add(child);
        result.setSectionDifferentials(Collections.singletonList(parent));

        String csv = exportService.exportToCsv(result, "2023", "2024");
        String[] lines = csv.split(ReportComparisonCsvExportService.NEW_LINE);

        assertEquals("Should have 1 header and 2 data rows from nested sections", 3, lines.length);
        assertTrue("Child section row should be exported", csv.contains("Nested Measure"));
        assertTrue("Grand child section row should be exported", csv.contains("Deep Measure"));
        assertTrue("Section column should carry the path and header the UI shows",
                lines[1].startsWith("1.1. Child Section,Nested Measure,"));
        assertTrue("Nested section path should be carried too",
                lines[2].startsWith("1.1.1. Grand Child Section,Deep Measure,"));
    }

    @Test
    public void exportToCsv_EveryRowHasTheSameColumnCountAsTheHeader() {
        ReportComparisonResult result = new ReportComparisonResult();
        SectionDifferential section = new SectionDifferential();
        section.setSectionHeader("Mixed");
        TableDifferential table = new TableDifferential();
        table.setTableCaption("Mixed Measure");
        table.setRowDifferentials(new ArrayList<>());
        table.getRowDifferentials().add(rowWithIdentifier(identifier(Field.PROVIDER, "RG-1")));
        table.getRowDifferentials().add(rowWithIdentifier(identifier(Field.AGE_GROUP, "0-2")));

        RowDifferential missing = createTestRow(true, 50.0, false, null, ">60%");
        missing.setMissingInNewReport(true);
        table.getRowDifferentials().add(missing);

        section.getTableDifferentials().add(table);
        result.setSectionDifferentials(Collections.singletonList(section));

        String[] lines = exportService.exportToCsv(result, "2023", "2024")
                .split(ReportComparisonCsvExportService.NEW_LINE);

        int expected = countSeparators(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            assertEquals("Row " + i + " should align with the header", expected, countSeparators(lines[i]));
        }
    }

    @Test
    public void exportToCsv_WithSpecialCharacters_EscapesStringsCorrectly() {
        ReportComparisonResult result = new ReportComparisonResult();
        SectionDifferential section = new SectionDifferential();
        TableDifferential table = new TableDifferential();

        // Include comma in table caption
        table.setTableCaption("Measure 1.1, Patient Name");

        RowDifferential row = createTestRow(true, 100.0, true, 100.0, ">99%");
        row.setThresholdText(">99%, required by \"CDC\""); // Includes comma and quotes

        table.setRowDifferentials(Collections.singletonList(row));
        section.getTableDifferentials().add(table);
        result.setSectionDifferentials(Collections.singletonList(section));

        String csv = exportService.exportToCsv(result, "Base Report", "New Report");
        String[] lines = csv.split(ReportComparisonCsvExportService.NEW_LINE);

        // Verify escaping behavior
        assertTrue("Table caption containing comma should be wrapped in quotes",
                lines[1].contains("\"Measure 1.1, Patient Name\""));
        assertTrue("Threshold containing quotes and comma should be escaped properly",
                lines[1].contains("\">99%, required by \"\"CDC\"\"\""));
    }

    @Test
    public void exportToCsv_WithNullValues_FormatsAsDash() {
        ReportComparisonResult result = new ReportComparisonResult();
        SectionDifferential section = new SectionDifferential();
        TableDifferential table = new TableDifferential();
        table.setTableCaption("Null Measure");

        RowDifferential row = new RowDifferential();
        row.setBasePass(false);
        row.setNewPass(false);
        row.setBasePercent(null);
        row.setNewPercent(null);

        table.setRowDifferentials(Collections.singletonList(row));
        section.getTableDifferentials().add(table);
        result.setSectionDifferentials(Collections.singletonList(section));

        String csv = exportService.exportToCsv(result, "2023", "2024");
        String[] lines = csv.split(ReportComparisonCsvExportService.NEW_LINE);

        // The UI renders '-' for an empty threshold and for absent values.
        assertTrue("Null percents and threshold should output '-' like the table",
                lines[1].contains("-,-,Not Met,-,Not Met,0.00%"));
    }

    // --- Helpers ---

    private int countSeparators(String line) {
        int count = 0;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) count++;
        }
        return count;
    }

    private Map<Field, String> identifier(Object... pairs) {
        Map<Field, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Field) pairs[i], (String) pairs[i + 1]);
        }
        return map;
    }

    private RowDifferential rowWithIdentifier(Map<Field, String> identifier) {
        RowDifferential row = createTestRow(true, 90.0, true, 92.0, ">85%");
        row.setRowIdentifier(identifier);
        return row;
    }

    private ReportComparisonResult singleRowResult(RowDifferential row) {
        ReportComparisonResult result = new ReportComparisonResult();
        SectionDifferential section = new SectionDifferential();
        section.setSectionHeader("Section");
        TableDifferential table = new TableDifferential();
        table.setTableCaption("Table");
        table.setRowDifferentials(Collections.singletonList(row));
        section.getTableDifferentials().add(table);
        result.setSectionDifferentials(Collections.singletonList(section));
        return result;
    }

    private RowDifferential createTestRow(boolean basePass, Double basePercent, boolean newPass, Double newPercent, String thresholdText) {
        RowDifferential row = new RowDifferential();
        row.setBasePass(basePass);
        row.setBasePercent(basePercent);
        row.setNewPass(newPass);
        row.setNewPercent(newPercent);
        row.setThresholdText(thresholdText);
        return row;
    }
}
