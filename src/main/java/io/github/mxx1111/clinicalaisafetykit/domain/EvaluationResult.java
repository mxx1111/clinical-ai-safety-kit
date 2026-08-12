package io.github.mxx1111.clinicalaisafetykit.domain;

import java.time.Instant;
import java.util.List;

public record EvaluationResult(
        String evaluationId,
        EvaluationStatus status,
        int score,
        List<Finding> findings,
        Instant evaluatedAt,
        String ruleVersion) {
}
