package io.github.mxx1111.clinicalaisafetykit.rule;

import io.github.mxx1111.clinicalaisafetykit.domain.EvaluationRequest;

public record EvaluationContext(
        EvaluationRequest request,
        String normalizedPrompt,
        String normalizedResponse) {

    public static EvaluationContext from(EvaluationRequest request) {
        return new EvaluationContext(
                request,
                SignalMatcher.normalize(request.prompt()),
                SignalMatcher.normalize(request.response()));
    }
}
