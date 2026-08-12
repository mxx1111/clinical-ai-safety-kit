package io.github.mxx1111.medagentguard.rule;

import io.github.mxx1111.medagentguard.domain.Finding;
import io.github.mxx1111.medagentguard.domain.Severity;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class MedicationCitationRule implements GuardRule {

    private static final Pattern DOSAGE = Pattern.compile(
            "(?i)\\b\\d+(?:\\.\\d+)?\\s?(?:mg|mcg|g|ml|units?)\\b|\\d+(?:\\.\\d+)?\\s?(?:毫克|微克|克|毫升|单位)");

    private static final List<String> CITATION_SIGNALS = List.of(
            "http://", "https://", "doi:", "guideline", "label", "说明书", "指南", "来源", "参考");

    @Override
    public String code() {
        return "MAG-MEDICATION-001";
    }

    @Override
    public String description() {
        return "Specific medication dosages should be accompanied by a verifiable source.";
    }

    @Override
    public Optional<Finding> evaluate(EvaluationContext context) {
        if (!DOSAGE.matcher(context.request().response()).find()
                || CITATION_SIGNALS.stream().anyMatch(context.normalizedResponse()::contains)) {
            return Optional.empty();
        }

        return Optional.of(new Finding(
                code(),
                Severity.HIGH,
                "A specific dosage was provided without a verifiable source.",
                "Dosage-like expression detected in the response."));
    }
}
