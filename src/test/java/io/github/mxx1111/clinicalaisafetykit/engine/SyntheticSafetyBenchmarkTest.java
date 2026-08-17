package io.github.mxx1111.clinicalaisafetykit.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mxx1111.clinicalaisafetykit.domain.EvaluationRequest;
import io.github.mxx1111.clinicalaisafetykit.domain.EvaluationStatus;
import io.github.mxx1111.clinicalaisafetykit.rule.DiagnosticCertaintyRule;
import io.github.mxx1111.clinicalaisafetykit.rule.EmergencyEscalationRule;
import io.github.mxx1111.clinicalaisafetykit.rule.MedicationCitationRule;
import io.github.mxx1111.clinicalaisafetykit.rule.PrivacyEchoRule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs the synthetic benchmark and publishes an honest scorecard.
 *
 * <p>Three case categories, three different obligations:
 *
 * <ul>
 *   <li><b>baseline</b> and <b>adversarial</b> cases must match their expected outcome exactly.
 *       These are ordinary regression protection.</li>
 *   <li><b>known-gap</b> cases must match {@code currentStatus} — what the engine really does — and
 *       must still differ from {@code expectedStatus}, the correct answer. That keeps the published
 *       limitation list truthful in both directions: the build fails if a gap silently regresses,
 *       and it also fails when someone fixes a gap without promoting the case out of the list.</li>
 * </ul>
 *
 * <p>The published report states a detection rate and a false-positive rate rather than a single
 * pass percentage. A pass percentage over cases this project wrote, about vocabulary this project
 * chose, would say almost nothing about whether the rules work.
 */
class SyntheticSafetyBenchmarkTest {

    private static final Path DATASET = Path.of("benchmarks", "synthetic-text-safety-v1.json");
    private static final Path REPORT_DIRECTORY = Path.of("target", "benchmark-results");
    private static final Path JSON_REPORT = REPORT_DIRECTORY.resolve("synthetic-text-safety-v1.json");
    private static final Path MARKDOWN_REPORT = REPORT_DIRECTORY.resolve("synthetic-text-safety-v1.md");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CATEGORY_KNOWN_GAP = "known-gap";
    private static final String CATEGORY_BASELINE = "baseline";

    @Test
    void matchesEveryExpectedSyntheticOutcomeAndWritesReports() throws IOException {
        BenchmarkDataset dataset = JSON.readValue(DATASET, BenchmarkDataset.class);
        EvaluationEngine engine = new EvaluationEngine(
                List.of(
                        new EmergencyEscalationRule(),
                        new MedicationCitationRule(),
                        new PrivacyEchoRule(),
                        new DiagnosticCertaintyRule()),
                Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC));

        List<BenchmarkCaseResult> caseResults = new ArrayList<>();
        for (BenchmarkCase benchmarkCase : dataset.cases()) {
            var result = engine.evaluate(new EvaluationRequest(
                    benchmarkCase.prompt(),
                    benchmarkCase.response(),
                    Map.of("source", "synthetic-benchmark", "caseId", benchmarkCase.id())));
            List<String> actualRuleCodes = result.findings().stream()
                    .map(finding -> finding.ruleCode())
                    .sorted()
                    .toList();
            List<String> expectedRuleCodes = sorted(benchmarkCase.expectedRuleCodes());
            boolean matchesCorrectAnswer = result.status() == benchmarkCase.expectedStatus()
                    && actualRuleCodes.equals(expectedRuleCodes);

            caseResults.add(new BenchmarkCaseResult(
                    benchmarkCase.id(),
                    benchmarkCase.language(),
                    benchmarkCase.ruleUnderTest(),
                    benchmarkCase.category(),
                    benchmarkCase.expectedStatus(),
                    result.status(),
                    expectedRuleCodes,
                    actualRuleCodes,
                    matchesCorrectAnswer,
                    benchmarkCase.gapReason()));
        }

        writeReports(buildReport(dataset, caseResults));

        assertActiveRulesAreCoveredInBothLanguages(dataset, engine);
        assertTrackedBehaviourIsUnchanged(dataset, caseResults);
        assertKnownGapsAreStillGaps(dataset, caseResults);
        assertPublishedEvidenceHidesRawInput(dataset);
    }

    /** baseline and adversarial cases are ordinary regressions: they must be answered correctly. */
    private static void assertTrackedBehaviourIsUnchanged(
            BenchmarkDataset dataset, List<BenchmarkCaseResult> caseResults) {
        Set<String> gapIds = idsWithCategory(dataset, CATEGORY_KNOWN_GAP);
        assertThat(caseResults)
                .filteredOn(result -> !gapIds.contains(result.id()))
                .filteredOn(result -> !result.matchesCorrectAnswer())
                .as("baseline and adversarial mismatches; inspect %s", JSON_REPORT)
                .isEmpty();
    }

    /**
     * A known gap must behave exactly as documented, and must still be wrong.
     *
     * <p>The second half is what keeps the limitation list from rotting. Improving a rule is
     * supposed to break this test, so the contributor has to move the case into the adversarial set
     * and shrink the published gap list in the same change.
     */
    private static void assertKnownGapsAreStillGaps(
            BenchmarkDataset dataset, List<BenchmarkCaseResult> caseResults) {
        Map<String, BenchmarkCaseResult> byId = caseResults.stream()
                .collect(Collectors.toMap(BenchmarkCaseResult::id, result -> result));

        for (BenchmarkCase benchmarkCase : dataset.cases()) {
            if (!CATEGORY_KNOWN_GAP.equals(benchmarkCase.category())) {
                continue;
            }
            BenchmarkCaseResult result = byId.get(benchmarkCase.id());

            assertThat(benchmarkCase.currentStatus())
                    .as("known-gap case %s must document currentStatus", benchmarkCase.id())
                    .isNotNull();
            assertThat(benchmarkCase.gapReason())
                    .as("known-gap case %s must explain the gap", benchmarkCase.id())
                    .isNotBlank();

            assertThat(result.actualStatus())
                    .as("documented current behaviour drifted for %s", benchmarkCase.id())
                    .isEqualTo(benchmarkCase.currentStatus());
            assertThat(result.actualRuleCodes())
                    .as("documented current rule codes drifted for %s", benchmarkCase.id())
                    .isEqualTo(sorted(benchmarkCase.currentRuleCodes()));

            assertThat(result.matchesCorrectAnswer())
                    .as("%s now answers correctly; promote it out of known-gap and update the "
                            + "limitation list in README.md and README.zh-CN.md", benchmarkCase.id())
                    .isFalse();
        }
    }

    /** Every active rule keeps a safe and an unsafe baseline example in both languages. */
    private static void assertActiveRulesAreCoveredInBothLanguages(
            BenchmarkDataset dataset, EvaluationEngine engine) {
        Set<String> activeRuleCodes = engine.rules().stream()
                .map(EvaluationEngine.RuleDescriptor::code)
                .collect(Collectors.toSet());
        assertThat(dataset.cases().stream().map(BenchmarkCase::ruleUnderTest).collect(Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(activeRuleCodes);
        assertThat(dataset.cases().stream().map(BenchmarkCase::id).distinct())
                .as("case ids must be unique")
                .hasSize(dataset.cases().size());

        for (String ruleCode : activeRuleCodes) {
            for (String language : List.of("en", "zh-CN")) {
                List<BenchmarkCase> coverage = dataset.cases().stream()
                        .filter(item -> CATEGORY_BASELINE.equals(item.category()))
                        .filter(item -> item.ruleUnderTest().equals(ruleCode))
                        .filter(item -> item.language().equals(language))
                        .toList();
                assertThat(coverage.stream().filter(item -> item.expectedRuleCodes().isEmpty()))
                        .as("safe baseline for %s in %s", ruleCode, language)
                        .hasSize(1);
                assertThat(coverage.stream()
                                .filter(item -> item.expectedRuleCodes().equals(List.of(ruleCode))))
                        .as("unsafe baseline for %s in %s", ruleCode, language)
                        .hasSize(1);
            }
        }
    }

    private static void assertPublishedEvidenceHidesRawInput(BenchmarkDataset dataset)
            throws IOException {
        String publishedEvidence = Files.readString(JSON_REPORT) + Files.readString(MARKDOWN_REPORT);
        dataset.cases().forEach(benchmarkCase -> assertThat(publishedEvidence)
                .as("published evidence must not include raw benchmark input for %s", benchmarkCase.id())
                .doesNotContain(benchmarkCase.prompt(), benchmarkCase.response()));
    }

    private static BenchmarkReport buildReport(
            BenchmarkDataset dataset, List<BenchmarkCaseResult> caseResults) {
        List<BenchmarkCaseResult> unsafeCases = caseResults.stream()
                .filter(result -> !result.expectedRuleCodes().isEmpty())
                .toList();
        List<BenchmarkCaseResult> safeCases = caseResults.stream()
                .filter(result -> result.expectedRuleCodes().isEmpty())
                .toList();

        // Detection counts a case only when the engine returns the exact expected rule set. Flagging
        // an unsafe response for the wrong reason is not a detection.
        long detected = unsafeCases.stream().filter(BenchmarkCaseResult::matchesCorrectAnswer).count();
        long falsePositives = safeCases.stream()
                .filter(result -> !result.actualRuleCodes().isEmpty())
                .count();
        long knownGaps = caseResults.stream()
                .filter(result -> CATEGORY_KNOWN_GAP.equals(result.category()))
                .count();

        return new BenchmarkReport(
                dataset.benchmarkId(),
                dataset.benchmarkVersion(),
                EvaluationEngine.RULE_VERSION,
                caseResults.size(),
                unsafeCases.size(),
                (int) detected,
                unsafeCases.isEmpty() ? 0.0 : (double) detected / unsafeCases.size(),
                safeCases.size(),
                (int) falsePositives,
                safeCases.isEmpty() ? 0.0 : (double) falsePositives / safeCases.size(),
                (int) knownGaps,
                dataset.limitations(),
                caseResults);
    }

    private static void writeReports(BenchmarkReport report) throws IOException {
        Files.createDirectories(REPORT_DIRECTORY);
        String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
        Files.writeString(JSON_REPORT, json, StandardCharsets.UTF_8);
        Files.writeString(MARKDOWN_REPORT, toMarkdown(report), StandardCharsets.UTF_8);
    }

    private static String toMarkdown(BenchmarkReport report) {
        StringBuilder markdown = new StringBuilder()
                .append("# Synthetic Text Safety Benchmark\n\n")
                .append("Deterministic agreement with expected rule outcomes on synthetic input. ")
                .append("It does not measure clinical validity or real-world model quality.\n\n")
                .append("- Benchmark: `").append(report.benchmarkId()).append("`\n")
                .append("- Dataset version: `").append(report.benchmarkVersion()).append("`\n")
                .append("- Rule version: `").append(report.ruleVersion()).append("`\n")
                .append("- Detection rate on unsafe cases: **").append(report.detectedUnsafeCases())
                .append("/").append(report.unsafeCases()).append("** (")
                .append(percentage(report.detectionRate())).append(")\n")
                .append("- False positives on safe cases: **").append(report.falsePositiveCases())
                .append("/").append(report.safeCases()).append("** (")
                .append(percentage(report.falsePositiveRate())).append(")\n")
                .append("- Documented known gaps: **").append(report.knownGapCases()).append("**\n\n")
                .append("| Case | Language | Rule | Category | Expected | Actual | Expected rules | ")
                .append("Actual rules | Correct |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");

        report.cases().stream()
                .sorted(Comparator.comparing(BenchmarkCaseResult::category)
                        .thenComparing(BenchmarkCaseResult::id))
                .forEach(result -> markdown
                        .append("| `").append(result.id()).append("` | ")
                        .append(result.language()).append(" | ")
                        .append("`").append(result.ruleUnderTest()).append("` | ")
                        .append(result.category()).append(" | ")
                        .append(result.expectedStatus()).append(" | ")
                        .append(result.actualStatus()).append(" | ")
                        .append(formatRuleCodes(result.expectedRuleCodes())).append(" | ")
                        .append(formatRuleCodes(result.actualRuleCodes())).append(" | ")
                        .append(result.matchesCorrectAnswer() ? "yes" : "no").append(" |\n"));

        List<BenchmarkCaseResult> gaps = report.cases().stream()
                .filter(result -> CATEGORY_KNOWN_GAP.equals(result.category()))
                .sorted(Comparator.comparing(BenchmarkCaseResult::id))
                .toList();
        if (!gaps.isEmpty()) {
            markdown.append("\n## Known gaps\n\n")
                    .append("Cases the current rules answer incorrectly. Published deliberately.\n\n");
            gaps.forEach(gap -> markdown
                    .append("- `").append(gap.id()).append("` (")
                    .append(gap.ruleUnderTest()).append("): ")
                    .append(gap.gapReason()).append("\n"));
        }

        markdown.append("\n## Limitations\n\n");
        report.limitations().forEach(limitation -> markdown.append("- ").append(limitation).append("\n"));
        return markdown.toString();
    }

    private static String percentage(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100);
    }

    private static List<String> sorted(List<String> ruleCodes) {
        return ruleCodes == null ? List.of() : ruleCodes.stream().sorted().toList();
    }

    private static Set<String> idsWithCategory(BenchmarkDataset dataset, String category) {
        return dataset.cases().stream()
                .filter(item -> category.equals(item.category()))
                .map(BenchmarkCase::id)
                .collect(Collectors.toSet());
    }

    private static String formatRuleCodes(List<String> ruleCodes) {
        return ruleCodes.isEmpty() ? "—" : String.join(", ", ruleCodes.stream()
                .map(code -> "`" + code + "`")
                .toList());
    }

    record BenchmarkDataset(
            String benchmarkId,
            String benchmarkVersion,
            String description,
            Map<String, String> categories,
            List<String> limitations,
            List<BenchmarkCase> cases) {
    }

    record BenchmarkCase(
            String id,
            String language,
            String ruleUnderTest,
            String category,
            String prompt,
            String response,
            EvaluationStatus expectedStatus,
            List<String> expectedRuleCodes,
            EvaluationStatus currentStatus,
            List<String> currentRuleCodes,
            String gapReason) {
    }

    record BenchmarkCaseResult(
            String id,
            String language,
            String ruleUnderTest,
            String category,
            EvaluationStatus expectedStatus,
            EvaluationStatus actualStatus,
            List<String> expectedRuleCodes,
            List<String> actualRuleCodes,
            boolean matchesCorrectAnswer,
            String gapReason) {
    }
}
