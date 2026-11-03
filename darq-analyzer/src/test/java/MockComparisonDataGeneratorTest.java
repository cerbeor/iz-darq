import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gov.nist.healthcare.iz.darq.analyzer.model.analysis.AnalysisReport;
import gov.nist.healthcare.iz.darq.analyzer.model.analysis.DataTable;
import gov.nist.healthcare.iz.darq.analyzer.model.analysis.DataTableRow;
import gov.nist.healthcare.iz.darq.analyzer.model.analysis.ReportSectionResult;
import gov.nist.healthcare.iz.darq.analyzer.model.comparison.ReportComparisonResult;
import gov.nist.healthcare.iz.darq.analyzer.model.comparison.RowDifferential;
import gov.nist.healthcare.iz.darq.analyzer.model.template.*;
import gov.nist.healthcare.iz.darq.analyzer.model.template.Comparator;
import gov.nist.healthcare.iz.darq.analyzer.service.comparison.ReportComparisonCsvExportService;
import gov.nist.healthcare.iz.darq.analyzer.service.comparison.ReportComparisonService;
import gov.nist.healthcare.iz.darq.digest.domain.AnalysisType;
import gov.nist.healthcare.iz.darq.digest.domain.Field;
import gov.nist.healthcare.iz.darq.digest.domain.Fraction;
import org.junit.Test;

import java.io.File;
import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Builds a pair of AnalysisReports sharing one template, with deliberately non-zero
 * and varied Fraction results, and writes them out as JSON next to the CSV the
 * comparison produces. The JSON can be POSTed to /api/report/compare/{baseId} (or
 * inserted into Mongo) to exercise the comparison screens by hand.
 *
 * Output: darq-analyzer/src/test/resources/mock-data/ — the files are checked in, so
 * rerunning this test regenerates them and any diff shows what the change altered.
 */
public class MockComparisonDataGeneratorTest {

    private static final String TEMPLATE_ID = "mock-template-1";
    private static final File OUTPUT_DIR = new File("src/test/resources/mock-data");

    /** Caption, threshold comparator/value, base count/total, new count/total. */
    private static final Object[][] MEASURES = {
            // Met -> Met, no change
            {"Measure 1.1 - Patient Completeness", Comparator.GT, 99.0, 10000, 10000, 10000, 10000},
            // Not Met -> Met, improvement
            {"Measure 1.2 - Address Completeness", Comparator.GT, 99.0, 9900, 10000, 9910, 10000},
            // Met -> Not Met, regression
            {"Measure 2.1 - Vaccination Coding", Comparator.GT, 85.0, 9560, 10000, 8400, 10000},
            // Low-end values, both failing
            {"Measure 2.2 - Lot Number Presence", Comparator.GT, 90.0, 1234, 10000, 4567, 10000},
            // "Less than" threshold, both met
            {"Measure 3.1 - Duplicate Patient Rate", Comparator.LT, 5.0, 210, 10000, 175, 10000},
            // Empty denominator - must read as 0%, not as a missing value
            {"Measure 3.2 - Empty Denominator", Comparator.GT, 50.0, 0, 0, 0, 0},
    };

    private AnalysisReport buildReport(String id, String name, boolean useNewValues) {
        AnalysisReport report = new AnalysisReport();
        report.setId(id);
        report.setName(name);
        report.setDescription("Mock report for comparison UI testing");
        report.setPublished(true);

        ReportTemplate template = new ReportTemplate();
        template.setId(TEMPLATE_ID);
        template.setName("Mock Comparison Template");
        report.setReportTemplate(template);

        ReportSectionResult section = new ReportSectionResult();
        section.setId("section-1");
        section.setPath("1");
        section.setPosition(0);
        section.setHeader("Data Quality Measures");
        section.setChildren(new ArrayList<>());

        List<DataTable> tables = new ArrayList<>();
        boolean anyViolation = false;

        for (Object[] measure : MEASURES) {
            String caption = (String) measure[0];
            Comparator comparator = (Comparator) measure[1];
            double thresholdValue = (Double) measure[2];
            int count = (Integer) measure[useNewValues ? 5 : 3];
            int total = (Integer) measure[useNewValues ? 6 : 4];

            Threshold threshold = new Threshold();
            threshold.setComparator(comparator);
            threshold.setValue(thresholdValue);

            Fraction result = new Fraction(count, total);
            boolean pass = comparator == Comparator.GT
                    ? result.percent() > thresholdValue
                    : result.percent() < thresholdValue;

            DataTableRow row = new DataTableRow();
            Map<Field, String> identifier = new HashMap<>();
            identifier.put(Field.PROVIDER, "IIS-A");
            row.setValues(identifier);
            row.setResult(result);
            row.setThreshold(threshold);
            row.setPass(pass);

            DataTable table = new DataTable();
            table.setQuery(buildQuery(caption, threshold));
            table.setNominator(Collections.singleton(Field.PROVIDER));
            table.setDenominator(Collections.emptySet());
            table.setValues(new ArrayList<>(Collections.singletonList(row)));
            table.setThresholdViolation(!pass);
            anyViolation |= !pass;

            tables.add(table);
        }

        section.setData(tables);
        section.setThresholdViolation(anyViolation);
        report.setSections(new ArrayList<>(Collections.singletonList(section)));
        return report;
    }

    private SimpleViewQuery buildQuery(String caption, Threshold threshold) {
        SimpleViewQuery query = new SimpleViewQuery();
        query.setCaption(caption);
        query.setType(AnalysisType.V);
        query.setNominator(Field.PROVIDER);
        GlobalThreshold globalThreshold = new GlobalThreshold();
        globalThreshold.setGoal(threshold);
        query.setThreshold(globalThreshold);
        return query;
    }

    @Test
    public void generateMockComparisonData() throws Exception {
        AnalysisReport baseReport = buildReport("mock-report-2023", "Mock IIS Report 2023", false);
        AnalysisReport newReport = buildReport("mock-report-2024", "Mock IIS Report 2024", true);

        ReportComparisonResult comparison = new ReportComparisonService().compare(baseReport, newReport);
        String csv = new ReportComparisonCsvExportService().exportToCsv(comparison, "2023", "2024");

        // The whole point of the fixture: percentages must reflect the Fractions, not 0.0
        List<RowDifferential> rows = new ArrayList<>();
        comparison.getSectionDifferentials().get(0).getTableDifferentials()
                .forEach(table -> rows.addAll(table.getRowDifferentials()));
        assertEquals(MEASURES.length, rows.size());
        assertTrue("At least one base percentage must be non-zero",
                rows.stream().anyMatch(r -> r.getBasePercent() != null && r.getBasePercent() != 0.0));
        assertFalse("No measure other than the empty-denominator one may report 0%",
                rows.subList(0, MEASURES.length - 1).stream()
                        .anyMatch(r -> r.getBasePercent() == null || r.getBasePercent() == 0.0));

        OUTPUT_DIR.mkdirs();
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(OUTPUT_DIR, "base-report-2023.json"), baseReport);
        mapper.writeValue(new File(OUTPUT_DIR, "new-report-2024.json"), newReport);

        // The POST /api/report/compare/{baseId} endpoint takes a report as a request
        // body, so the fixture has to survive a Jackson round trip. Read-only derived
        // properties ("public") are serialized but have no setter, so the reader
        // ignores unknown fields the way Spring Boot's default mapper does.
        ObjectMapper reader = new ObjectMapper();
        reader.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        AnalysisReport reparsed = reader.readValue(
                mapper.writeValueAsString(newReport), AnalysisReport.class);
        assertEquals(MEASURES.length, reparsed.getSections().get(0).getData().size());
        assertTrue("Round tripped fixture must keep its Fraction results",
                reparsed.getSections().get(0).getData().stream()
                        .allMatch(t -> t.getValues().get(0).getEffectiveResult() != null));
        mapper.writeValue(new File(OUTPUT_DIR, "expected-comparison.json"), comparison);
        java.nio.file.Files.write(new File(OUTPUT_DIR, "expected-export.csv").toPath(), csv.getBytes("UTF-8"));

        System.out.println("Mock comparison data written to " + OUTPUT_DIR.getAbsolutePath());
        System.out.println(csv);
    }

    private static void assertEquals(int expected, int actual) {
        org.junit.Assert.assertEquals(expected, actual);
    }
}
