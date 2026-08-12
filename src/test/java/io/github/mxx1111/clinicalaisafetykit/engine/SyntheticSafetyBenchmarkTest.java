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

class SyntheticSafetyBenchmarkTest {

    private static final Path DATASET = Path.of("benchmarks", "synthetic-text-safety-v1.json");
    private static final Path REPORT_DIRECTORY = Path.of("target", "benchmark-results");
    private static final Path JSON_REPORT = REPORT_DIRECTORY.resolve("synthetic-text-safety-v1.json");
    private static final Path MARKDOWN_REPORT = REPORT_DIRECTORY.resolve("synthetic-text-safety-v1.md");
    private static final ObjectMapper JSON = new ObjectMapper();

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
            List<String> expectedRuleCodes = benchmarkCase.expectedRuleCodes().stream()
                    .sorted()
                    .toList();
            boolean passed = result.status() == benchmarkCase.expectedStatus()
                    && actualRuleCodes.equals(expectedRuleCodes);
            caseResults.add(new BenchmarkCaseResult(
                    benchmarkCase.id(),
                    benchmarkCase.language(),
                    benchmarkCase.ruleUnderTest(),
                    benchmarkCase.expectedStatus(),
                    result.status(),
                    expectedRuleCodes,
                    actualRuleCodes,
                    passed));
        }

        int passedCases = (int) caseResults.stream().filter(BenchmarkCaseResult::passed).count();
        BenchmarkReport report = new BenchmarkReport(
                dataset.benchmarkId(),
                dataset.benchmarkVersion(),
                EvaluationEngine.RULE_VERSION,
                caseResults.size(),
                passedCases,
                caseResults.size() - passedCases,
                caseResults.isEmpty() ? 0.0 : (double) passedCases / caseResults.size(),
                dataset.limitations(),
                caseResults);

        writeReports(report);

        assertThat(dataset.cases()).hasSize(16);
        assertCompleteBilingualCoverage(dataset, engine);
        assertThat(caseResults)
                .filteredOn(result -> !result.passed())
                .as("benchmark mismatches; inspect %s", JSON_REPORT)
                .isEmpty();
        String publishedEvidence = Files.readString(JSON_REPORT) + Files.readString(MARKDOWN_REPORT);
        dataset.cases().forEach(benchmarkCase -> assertThat(publishedEvidence)
                .as("published evidence must not include raw benchmark input for %s", benchmarkCase.id())
                .doesNotContain(benchmarkCase.prompt(), benchmarkCase.response()));
    }

    private static void assertCompleteBilingualCoverage(BenchmarkDataset dataset, EvaluationEngine engine) {
        Set<String> activeRuleCodes = engine.rules().stream()
                .map(EvaluationEngine.RuleDescriptor::code)
                .collect(Collectors.toSet());
        assertThat(dataset.cases().stream().map(BenchmarkCase::ruleUnderTest).collect(Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(activeRuleCodes);
        assertThat(dataset.cases().stream().map(BenchmarkCase::id).distinct()).hasSize(dataset.cases().size());

        for (String ruleCode : activeRuleCodes) {
            for (String language : List.of("en", "zh-CN")) {
                List<BenchmarkCase> coverage = dataset.cases().stream()
                        .filter(benchmarkCase -> benchmarkCase.ruleUnderTest().equals(ruleCode))
                        .filter(benchmarkCase -> benchmarkCase.language().equals(language))
                        .toList();
                assertThat(coverage)
                        .as("safe and unsafe coverage for %s in %s", ruleCode, language)
                        .hasSize(2);
                assertThat(coverage.stream().filter(item -> item.expectedRuleCodes().isEmpty())).hasSize(1);
                assertThat(coverage.stream().filter(item -> item.expectedRuleCodes().equals(List.of(ruleCode))))
                        .hasSize(1);
            }
        }
    }

    private static void writeReports(BenchmarkReport report) throws IOException {
        Files.createDirectories(REPORT_DIRECTORY);
        String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
        Files.writeString(JSON_REPORT, json, StandardCharsets.UTF_8);
        Files.writeString(MARKDOWN_REPORT, toMarkdown(report), StandardCharsets.UTF_8);
    }

    private static String toMarkdown(BenchmarkReport report) {
        StringBuilder markdown = new StringBuilder()
                .append("# Synthetic Text Safety Benchmark v1\n\n")
                .append("This report measures exact agreement with expected deterministic rule outcomes. ")
                .append("It does not measure clinical validity or real-world model quality.\n\n")
                .append("- Benchmark: `").append(report.benchmarkId()).append("`\n")
                .append("- Dataset version: `").append(report.benchmarkVersion()).append("`\n")
                .append("- Rule version: `").append(report.ruleVersion()).append("`\n")
                .append("- Exact matches: **").append(report.passedCases()).append("/")
                .append(report.totalCases()).append("** (")
                .append(String.format(Locale.ROOT, "%.1f%%", report.exactMatchRate() * 100)).append(")\n\n")
                .append("| Case | Language | Rule under test | Expected status | Actual status | Expected rules | Actual rules | Result |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");

        report.cases().stream()
                .sorted(Comparator.comparing(BenchmarkCaseResult::id))
                .forEach(result -> markdown
                        .append("| `").append(result.id()).append("` | ")
                        .append(result.language()).append(" | ")
                        .append("`").append(result.ruleUnderTest()).append("` | ")
                        .append(result.expectedStatus()).append(" | ")
                        .append(result.actualStatus()).append(" | ")
                        .append(formatRuleCodes(result.expectedRuleCodes())).append(" | ")
                        .append(formatRuleCodes(result.actualRuleCodes())).append(" | ")
                        .append(result.passed() ? "PASS" : "FAIL").append(" |\n"));

        markdown.append("\n## Limitations\n\n");
        report.limitations().forEach(limitation -> markdown.append("- ").append(limitation).append("\n"));
        return markdown.toString();
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
            List<String> limitations,
            List<BenchmarkCase> cases) {
    }

    record BenchmarkCase(
            String id,
            String language,
            String ruleUnderTest,
            String prompt,
            String response,
            EvaluationStatus expectedStatus,
            List<String> expectedRuleCodes) {
    }

    record BenchmarkCaseResult(
            String id,
            String language,
            String ruleUnderTest,
            EvaluationStatus expectedStatus,
            EvaluationStatus actualStatus,
            List<String> expectedRuleCodes,
            List<String> actualRuleCodes,
            boolean passed) {
    }

    record BenchmarkReport(
            String benchmarkId,
            String benchmarkVersion,
            String ruleVersion,
            int totalCases,
            int passedCases,
            int failedCases,
            double exactMatchRate,
            List<String> limitations,
            List<BenchmarkCaseResult> cases) {
    }
}
