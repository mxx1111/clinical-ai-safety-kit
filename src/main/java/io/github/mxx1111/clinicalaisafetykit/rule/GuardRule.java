package io.github.mxx1111.clinicalaisafetykit.rule;

import io.github.mxx1111.clinicalaisafetykit.domain.Finding;
import java.util.Optional;

public interface GuardRule {

    String code();

    String description();

    Optional<Finding> evaluate(EvaluationContext context);
}
