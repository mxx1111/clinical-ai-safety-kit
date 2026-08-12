package io.github.mxx1111.medagentguard.engine;

import io.github.mxx1111.medagentguard.domain.EvaluationRequest;
import io.github.mxx1111.medagentguard.domain.EvaluationResult;
import io.github.mxx1111.medagentguard.domain.EvaluationStatus;
import io.github.mxx1111.medagentguard.domain.Finding;
import io.github.mxx1111.medagentguard.domain.Severity;
import io.github.mxx1111.medagentguard.rule.EvaluationContext;
import io.github.mxx1111.medagentguard.rule.GuardRule;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class EvaluationEngine {

    public static final String RULE_VERSION = "2026-08-12.1";

    private final List<GuardRule> rules;
    private final Clock clock;

    @Autowired
    public EvaluationEngine(List<GuardRule> rules) {
        this(rules, Clock.systemUTC());
    }

    EvaluationEngine(List<GuardRule> rules, Clock clock) {
        this.rules = rules.stream()
                .sorted(Comparator.comparing(GuardRule::code))
                .toList();
        this.clock = clock;
    }

    public EvaluationResult evaluate(EvaluationRequest request) {
        EvaluationContext context = EvaluationContext.from(request);
        List<Finding> findings = rules.stream()
                .map(rule -> rule.evaluate(context))
                .flatMap(java.util.Optional::stream)
                .toList();

        int score = Math.max(0, 100 - findings.stream()
                .map(Finding::severity)
                .mapToInt(Severity::penalty)
                .sum());

        return new EvaluationResult(
                UUID.randomUUID().toString(),
                statusFor(findings),
                score,
                findings,
                Instant.now(clock),
                RULE_VERSION);
    }

    public List<RuleDescriptor> rules() {
        return rules.stream()
                .map(rule -> new RuleDescriptor(rule.code(), rule.description()))
                .toList();
    }

    private static EvaluationStatus statusFor(List<Finding> findings) {
        if (findings.stream().anyMatch(finding ->
                finding.severity() == Severity.CRITICAL || finding.severity() == Severity.HIGH)) {
            return EvaluationStatus.BLOCK;
        }
        if (!findings.isEmpty()) {
            return EvaluationStatus.WARN;
        }
        return EvaluationStatus.PASS;
    }

    public record RuleDescriptor(String code, String description) {
    }
}
