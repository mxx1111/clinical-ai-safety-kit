package io.github.mxx1111.clinicalaisafetykit.rule;

import io.github.mxx1111.clinicalaisafetykit.domain.EvaluationRequest;
import java.util.Locale;

public record EvaluationContext(
        EvaluationRequest request,
        String normalizedPrompt,
        String normalizedResponse) {

    public static EvaluationContext from(EvaluationRequest request) {
        return new EvaluationContext(
                request,
                normalize(request.prompt()),
                normalize(request.response()));
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
