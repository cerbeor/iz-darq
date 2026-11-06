package gov.nist.healthcare.iz.darq.analyzer.service.comparison;

import gov.nist.healthcare.iz.darq.analyzer.model.comparison.ReportComparisonResult;
import gov.nist.healthcare.iz.darq.analyzer.model.comparison.RowDifferential;
import gov.nist.healthcare.iz.darq.analyzer.model.comparison.SectionDifferential;
import gov.nist.healthcare.iz.darq.analyzer.model.comparison.TableDifferential;
import gov.nist.healthcare.iz.darq.digest.domain.Field;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Exports a report comparison as CSV, mirroring the columns rendered by the
 * Angular comparison table (comparison-table.component.html).
 *
 * The on screen table is drawn once per table differential, so its identifier
 * columns vary from table to table. A CSV is a single flat grid, so the export
 * emits the union of every identifier field used anywhere in the comparison,
 * preceded by Section and Table columns that carry the context the UI gets from
 * its section headings and table captions.
 */
@Service
public class ReportComparisonCsvExportService {

    // --- CSV structural constants ---
    public static final String CSV_SEPARATOR = ",";
    public static final String NEW_LINE = "\n";

    // --- Header constants ---
    public static final String HEADER_SECTION = "Section";
    public static final String HEADER_TABLE = "Table";
    public static final String HEADER_THRESHOLD = "Threshold";
    public static final String HEADER_OUTCOME_SUFFIX = " Outcome";
    public static final String HEADER_POINT_CHANGE = "% Point Change";
    public static final String HEADER_MEASURE_OUTCOME = "Measure Outcome";

    // --- Value constants ---
    public static final String STATUS_MET = "Met";
    public static final String STATUS_NOT_MET = "Not Met";
    public static final String OUTCOME_BETTER = "Better";
    public static final String OUTCOME_WORSE = "Worse";
    public static final String OUTCOME_NO_CHANGE = "No Change";
    public static final String MISSING_PREFIX = "Missing in ";
    public static final String EMPTY_VALUE = "-";

    /**
     * Display names for identifier fields, matching `fieldDisplayName` in
     * analysis.values.ts so a column reads the same in the CSV as on screen.
     */
    private static final Map<Field, String> FIELD_DISPLAY_NAME = new EnumMap<>(Field.class);
    static {
        FIELD_DISPLAY_NAME.put(Field.PROVIDER, "REPORTING_GROUP");
        FIELD_DISPLAY_NAME.put(Field.DETECTION, "DETECTION_TYPE");
        FIELD_DISPLAY_NAME.put(Field.TABLE, "FIELD_TABLE");
    }

    /**
     * Generates a CSV formatted string matching the differential report structure.
     *
     * @param comparisonResult   The calculated comparison payload
     * @param baseReportNickname e.g., "2023"
     * @param newReportNickname  e.g., "2024"
     * @return Raw CSV string
     */
    public String exportToCsv(ReportComparisonResult comparisonResult, String baseReportNickname, String newReportNickname) {
        List<Field> identifierFields = collectIdentifierFields(comparisonResult);

        StringBuilder csv = new StringBuilder();

        // 1. Build Header
        csv.append(HEADER_SECTION).append(CSV_SEPARATOR)
                .append(HEADER_TABLE).append(CSV_SEPARATOR);
        for (Field field : identifierFields) {
            csv.append(escape(identifierFieldLabel(field))).append(CSV_SEPARATOR);
        }
        csv.append(HEADER_THRESHOLD).append(CSV_SEPARATOR)
                .append(escape(baseReportNickname)).append(CSV_SEPARATOR)
                .append(escape(baseReportNickname + HEADER_OUTCOME_SUFFIX)).append(CSV_SEPARATOR)
                .append(escape(newReportNickname)).append(CSV_SEPARATOR)
                .append(escape(newReportNickname + HEADER_OUTCOME_SUFFIX)).append(CSV_SEPARATOR)
                .append(HEADER_POINT_CHANGE).append(CSV_SEPARATOR)
                .append(HEADER_MEASURE_OUTCOME).append(NEW_LINE);

        if (comparisonResult == null || comparisonResult.getSectionDifferentials() == null) {
            return csv.toString();
        }

        // 2. Iterate through Differentials
        for (SectionDifferential section : comparisonResult.getSectionDifferentials()) {
            appendSection(csv, section, identifierFields, newReportNickname);
        }

        return csv.toString();
    }

    /**
     * Appends every row of a section and, recursively, of its child sections.
     * Report sections are a tree: top-level sections are frequently only
     * containers whose data tables live in descendant sections, so skipping
     * the recursion would emit a header-only CSV.
     */
    private void appendSection(StringBuilder csv, SectionDifferential section, List<Field> identifierFields, String newReportNickname) {
        if (section == null) return;

        String sectionLabel = sectionLabel(section);

        if (section.getTableDifferentials() != null) {
            for (TableDifferential table : section.getTableDifferentials()) {
                if (table == null || table.getRowDifferentials() == null) continue;

                String caption = tableLabel(table);

                for (RowDifferential row : table.getRowDifferentials()) {
                    appendRow(csv, sectionLabel, caption, row, identifierFields, newReportNickname);
                }
            }
        }

        if (section.getChildren() != null) {
            for (SectionDifferential child : section.getChildren()) {
                appendSection(csv, child, identifierFields, newReportNickname);
            }
        }
    }

    private void appendRow(StringBuilder csv, String sectionLabel, String caption, RowDifferential row,
                           List<Field> identifierFields, String newReportNickname) {

        csv.append(escape(sectionLabel)).append(CSV_SEPARATOR)
                .append(escape(caption)).append(CSV_SEPARATOR);

        // One column per identifier field; the UI renders '-' for a field a row does not carry.
        Map<Field, String> identifier = row.getRowIdentifier();
        for (Field field : identifierFields) {
            String value = identifier != null ? identifier.get(field) : null;
            csv.append(escape(value != null && !value.isEmpty() ? value : EMPTY_VALUE)).append(CSV_SEPARATOR);
        }

        // The UI shows '-' for an absent threshold.
        String threshold = row.getThresholdText();
        csv.append(escape(threshold != null && !threshold.isEmpty() ? threshold : EMPTY_VALUE)).append(CSV_SEPARATOR);

        csv.append(formatPercent(row.getBasePercent())).append(CSV_SEPARATOR)
                .append(row.isBasePass() ? STATUS_MET : STATUS_NOT_MET).append(CSV_SEPARATOR);

        if (row.isMissingInNewReport()) {
            // The UI replaces the four trailing cells with a "Missing in <new>" notice.
            csv.append(escape(MISSING_PREFIX + newReportNickname)).append(CSV_SEPARATOR)
                    .append(CSV_SEPARATOR)
                    .append(CSV_SEPARATOR)
                    .append(NEW_LINE);
            return;
        }

        csv.append(formatPercent(row.getNewPercent())).append(CSV_SEPARATOR)
                .append(row.isNewPass() ? STATUS_MET : STATUS_NOT_MET).append(CSV_SEPARATOR)
                .append(formatPointChange(pointChange(row))).append(CSV_SEPARATOR)
                .append(outcome(row))
                .append(NEW_LINE);
    }

    /**
     * Union of the identifier fields used by any row, in first appearance order,
     * so that each one gets a stable column across the whole export.
     */
    private List<Field> collectIdentifierFields(ReportComparisonResult comparisonResult) {
        Set<Field> fields = new LinkedHashSet<>();
        if (comparisonResult != null && comparisonResult.getSectionDifferentials() != null) {
            for (SectionDifferential section : comparisonResult.getSectionDifferentials()) {
                collectIdentifierFields(section, fields);
            }
        }
        return new ArrayList<>(fields);
    }

    private void collectIdentifierFields(SectionDifferential section, Set<Field> fields) {
        if (section == null) return;

        if (section.getTableDifferentials() != null) {
            for (TableDifferential table : section.getTableDifferentials()) {
                if (table == null || table.getRowDifferentials() == null) continue;
                for (RowDifferential row : table.getRowDifferentials()) {
                    if (row.getRowIdentifier() != null) {
                        fields.addAll(row.getRowIdentifier().keySet());
                    }
                }
            }
        }

        if (section.getChildren() != null) {
            for (SectionDifferential child : section.getChildren()) {
                collectIdentifierFields(child, fields);
            }
        }
    }

    /**
     * "1.2. Vaccination Detections", matching the heading the UI renders.
     */
    private String sectionLabel(SectionDifferential section) {
        String header = section.getSectionHeader() != null ? section.getSectionHeader() : "";
        String path = section.getSectionPath();
        return (path != null && !path.isEmpty()) ? path + ". " + header : header;
    }

    /**
     * Label for the Table column. The caption is an optional, user authored field
     * on the query ({@code QueryPayload.caption}, defaulted to an empty string by
     * the template editor), so most real templates leave it blank. On screen a
     * blank caption is harmless because each table is its own visual grid under
     * its section heading, but the CSV flattens every table into one sheet, so a
     * blank here would leave rows with no way to tell which table they came from.
     * Fall back to the table's position within its section, which is always set.
     */
    String tableLabel(TableDifferential table) {
        String caption = table.getTableCaption();
        if (caption != null && !caption.trim().isEmpty()) {
            return caption;
        }
        return "Table " + (table.getTableIndex() + 1);
    }

    /**
     * PROVIDER -> "Reporting Group", mirroring identifierFieldLabel() in
     * report-comparison.service.ts.
     */
    String identifierFieldLabel(Field field) {
        String name = FIELD_DISPLAY_NAME.containsKey(field) ? FIELD_DISPLAY_NAME.get(field) : field.name();
        StringBuilder label = new StringBuilder();
        for (String part : name.split("_")) {
            if (part.isEmpty()) continue;
            if (label.length() > 0) label.append(" ");
            label.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return label.toString();
    }

    private MeasureChange outcomeOf(RowDifferential row) {
        if (!row.isBasePass() && row.isNewPass()) return MeasureChange.BETTER;
        if (row.isBasePass() && !row.isNewPass()) return MeasureChange.WORSE;
        return MeasureChange.NO_CHANGE;
    }

    private String outcome(RowDifferential row) {
        switch (outcomeOf(row)) {
            case BETTER: return OUTCOME_BETTER;
            case WORSE: return OUTCOME_WORSE;
            default: return OUTCOME_NO_CHANGE;
        }
    }

    private enum MeasureChange { BETTER, WORSE, NO_CHANGE }

    private double pointChange(RowDifferential row) {
        return (row.getNewPercent() != null && row.getBasePercent() != null)
                ? (row.getNewPercent() - row.getBasePercent())
                : 0.0;
    }

    private String escape(String value) {
        if (value == null) return "";
        // If value contains a comma, quote, or newline, wrap it in quotes and double internal quotes
        if (value.contains(CSV_SEPARATOR) || value.contains("\"") || value.contains(NEW_LINE)) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * The UI renders percents through the '1.2-2' number pipe, and shows a bare
     * '%' for an absent value rather than hiding the cell.
     */
    private String formatPercent(Double value) {
        if (value == null) return EMPTY_VALUE;
        return String.format("%.2f%%", value);
    }

    /**
     * The point change column is rendered with an explicit '+' when positive.
     */
    private String formatPointChange(double value) {
        return String.format("%s%.2f%%", value > 0 ? "+" : "", value);
    }
}
