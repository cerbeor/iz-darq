import gov.nist.healthcare.iz.darq.analyzer.model.analysis.*;
import gov.nist.healthcare.iz.darq.analyzer.model.template.Comparator;
import gov.nist.healthcare.iz.darq.analyzer.model.template.QueryPayload;
import gov.nist.healthcare.iz.darq.analyzer.model.template.ReportTemplate;
import gov.nist.healthcare.iz.darq.analyzer.model.template.Threshold;
import gov.nist.healthcare.iz.darq.digest.domain.Field;
import gov.nist.healthcare.iz.darq.digest.domain.Fraction;

import java.util.*;

public class MockReportComparisonTestFactory {

    public static AnalysisReport createStubReport(String reportId, String templateId) {
        AnalysisReport report = new AnalysisReport();
        report.setId(reportId);

        ReportTemplate template = new ReportTemplate();
        template.setId(templateId);
        report.setReportTemplate(template);

        report.setSections(new ArrayList<>());
        return report;
    }

    public static ReportSectionResult createStubSection(String sectionId, boolean thresholdViolation) {
        ReportSectionResult section = new ReportSectionResult();
        section.setId(sectionId);
        section.setThresholdViolation(thresholdViolation);
        section.setData(new ArrayList<>());
        section.setChildren(new ArrayList<>());
        return section;
    }

    public static DataTable createStubTable(String caption, boolean thresholdViolation) {
        DataTable table = new DataTable();
        table.setThresholdViolation(thresholdViolation);
        table.setQuery(createStubQueryPayload(caption));
        table.setValues(new ArrayList<>());
        return table;
    }

    public static DataTableRow createStubRow(Map<Field, String> values, boolean pass) {
        DataTableRow row = new DataTableRow();
        row.setValues(values);
        row.setPass(pass);
        row.setResult(null); // Passing null prevents constructor incompatibility with custom Fraction definitions
        return row;
    }

    /**
     * Row carrying an actual Fraction result and threshold, so that percentage
     * extraction and threshold rendering can be exercised end to end.
     */
    public static DataTableRow createStubRow(Map<Field, String> values, boolean pass, int count, int total, Threshold threshold) {
        DataTableRow row = new DataTableRow();
        row.setValues(values);
        row.setPass(pass);
        row.setResult(new Fraction(count, total));
        row.setThreshold(threshold);
        return row;
    }

    /**
     * Row whose result is overridden by an AdjustedFraction, which getEffectiveResult()
     * must prefer over the raw result.
     */
    public static DataTableRow createStubRowWithAdjustedFraction(Map<Field, String> values, boolean pass, int rawCount, int rawTotal, int adjCount, int adjTotal) {
        DataTableRow row = new DataTableRow();
        row.setValues(values);
        row.setPass(pass);
        row.setResult(new Fraction(rawCount, rawTotal));
        AdjustedFraction adjusted = new AdjustedFraction();
        adjusted.setCount(adjCount);
        adjusted.setTotal(adjTotal);
        row.setAdjustedFraction(adjusted);
        return row;
    }

    public static Threshold threshold(Comparator comparator, double value) {
        Threshold threshold = new Threshold();
        threshold.setComparator(comparator);
        threshold.setValue(value);
        return threshold;
    }

    public static Map<Field, String> rowIdentifier(Field field, String value) {
        Map<Field, String> identifier = new HashMap<>();
        identifier.put(field, value);
        return identifier;
    }

    /**
     * Creates an anonymous subclass of the abstract QueryPayload to bypass the need for Mockito.
     */
    public static QueryPayload createStubQueryPayload(String captionValue) {
        QueryPayload stub = new QueryPayload(null) {
            @Override
            public Set<Field> getNominatorFields() { return Collections.emptySet(); }

            @Override
            public Set<Field> getDenominatorFields() { return Collections.emptySet(); }

            @Override
            public Set<gov.nist.healthcare.iz.darq.analyzer.model.template.DataSelector> getFilterFields() {
                return Collections.emptySet();
            }

            @Override
            public gov.nist.healthcare.iz.darq.analyzer.model.template.QueryThreshold getQueryThreshold() {
                return null;
            }
        };
        stub.setCaption(captionValue);
        return stub;
    }
}