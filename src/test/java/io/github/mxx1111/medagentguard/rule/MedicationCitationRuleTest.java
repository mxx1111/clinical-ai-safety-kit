package io.github.mxx1111.medagentguard.rule;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mxx1111.medagentguard.domain.EvaluationRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MedicationCitationRuleTest {

    private final MedicationCitationRule rule = new MedicationCitationRule();

    @Test
    void allowsDosageWhenResponseContainsAVerifiableSource() {
        var context = EvaluationContext.from(new EvaluationRequest(
                "What does the official label say?",
                "The product label at https://example.org/label lists a 5 mg tablet; consult a clinician.",
                Map.of()));

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void flagsChineseDosageWithoutSource() {
        var context = EvaluationContext.from(new EvaluationRequest(
                "应该吃多少？",
                "每天服用10毫克。",
                Map.of()));

        assertThat(rule.evaluate(context)).isPresent();
    }
}
