package io.github.mxx1111.clinicalaisafetykit.rule;

import io.github.mxx1111.clinicalaisafetykit.domain.Finding;
import io.github.mxx1111.clinicalaisafetykit.domain.Severity;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class MedicationCitationRule implements GuardRule {

    private static final Pattern DOSAGE = Pattern.compile(
            "(?i)\\b\\d+(?:\\.\\d+)?\\s?(?:mg|mcg|g|ml|units?)\\b|\\d+(?:\\.\\d+)?\\s?(?:毫克|微克|克|毫升|单位)");

    /**
     * Phrases that count as pointing at a verifiable source.
     *
     * <p>Deliberately excludes bare "label" and "参考". Both are ordinary words that show up in
     * responses carrying no source at all ("read the label", "仅供参考"), so accepting them cleared
     * almost every dosage. The remaining entries either carry a locator or name a document class.
     */
    private static final List<String> CITATION_SIGNALS = List.of(
            "http://", "https://", "doi:", "guideline", "package insert", "prescribing information",
            "说明书", "指南", "来源", "出处");

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
        // Match on the normalized response so that dosage offsets and citation offsets refer to the
        // same string. The pattern is already case-insensitive, and whitespace collapsing does not
        // change what it matches.
        String response = context.normalizedResponse();
        Matcher dosage = DOSAGE.matcher(response);
        if (!dosage.find()) {
            return Optional.empty();
        }

        // A citation only clears the dosage it accompanies. Requiring proximity, and requiring that
        // the citation is not negated, closes two bypasses: a source mentioned paragraphs away, and
        // "take 500 mg, I cannot give you a guideline reference", which previously read as cited.
        do {
            if (!SignalMatcher.containsUnnegatedNear(
                    response,
                    CITATION_SIGNALS,
                    dosage.start(),
                    dosage.end(),
                    SignalMatcher.DEFAULT_PROXIMITY_WINDOW)) {
                return Optional.of(new Finding(
                        code(),
                        Severity.HIGH,
                        "A specific dosage was provided without a verifiable source.",
                        "Dosage-like expression detected in the response."));
            }
        } while (dosage.find());

        return Optional.empty();
    }
}
