package io.github.mxx1111.medagentguard.rule;

import io.github.mxx1111.medagentguard.domain.Finding;
import java.util.Optional;

public interface GuardRule {

    String code();

    String description();

    Optional<Finding> evaluate(EvaluationContext context);
}
