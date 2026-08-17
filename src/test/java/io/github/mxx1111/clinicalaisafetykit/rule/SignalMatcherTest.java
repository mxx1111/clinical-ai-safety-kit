package io.github.mxx1111.clinicalaisafetykit.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

class SignalMatcherTest {

    private static final List<String> ESCALATION = List.of("emergency", "call 911", "急诊", "立即就医");

    @Nested
    @DisplayName("a negated phrase must not count as present")
    class Negation {

        @Test
        void englishNegationSuppressesThePhrase() {
            assertThat(SignalMatcher.containsUnnegated(
                    SignalMatcher.normalize("This is not an emergency."), ESCALATION))
                    .isFalse();
        }

        @Test
        void chineseNegationSuppressesThePhrase() {
            assertThat(SignalMatcher.containsUnnegated(
                    SignalMatcher.normalize("这不是急诊。"), ESCALATION))
                    .isFalse();
        }

        @Test
        void contractedNegationIsRecognisedWithoutTheApostrophe() {
            assertThat(SignalMatcher.containsUnnegated(
                    SignalMatcher.normalize("You don't need emergency care."), ESCALATION))
                    .isFalse();
        }

        @Test
        void anUnnegatedOccurrenceElsewhereStillCounts() {
            assertThat(SignalMatcher.containsUnnegated(
                    SignalMatcher.normalize("This is not an emergency. Call 911 anyway."), ESCALATION))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("negation scope stops at clause boundaries")
    class Scope {

        @Test
        void aNegationInAPreviousSentenceDoesNotLeakForward() {
            assertThat(SignalMatcher.containsUnnegated(
                    SignalMatcher.normalize("It is not mild. Call 911 now."), ESCALATION))
                    .isTrue();
        }

        @Test
        void aConditionalClauseBeforeACommaDoesNotNegateTheAdvice() {
            assertThat(SignalMatcher.containsUnnegated(
                    SignalMatcher.normalize("If you are not improving, call 911."), ESCALATION))
                    .isTrue();
        }

        @Test
        void aChineseConditionalClauseDoesNotNegateTheAdvice() {
            assertThat(SignalMatcher.containsUnnegated(
                    SignalMatcher.normalize("如果不舒服，请立即就医。"), ESCALATION))
                    .isTrue();
        }

        @Test
        void aDistantNegationOutsideTheLookbehindWindowIsIgnored() {
            String text = "no other complaints were reported by the person during the visit emergency";
            assertThat(SignalMatcher.containsUnnegated(SignalMatcher.normalize(text), ESCALATION))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("negation cues must not fire inside longer words")
    class WordBoundaries {

        @Test
        void normalDoesNotActAsNo() {
            assertThat(SignalMatcher.containsUnnegated(
                    SignalMatcher.normalize("Vitals are normal but call 911."), ESCALATION))
                    .isTrue();
        }

        @Test
        void noticeDoesNotActAsNot() {
            assertThat(SignalMatcher.containsUnnegated(
                    SignalMatcher.normalize("Please notice and call 911."), ESCALATION))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("proximity restricts a phrase to the region around its anchor")
    class Proximity {

        @Test
        void aPhraseInsideTheWindowCounts() {
            String text = SignalMatcher.normalize("Take 500 mg per the guideline at https://example.invalid.");
            int anchor = text.indexOf("500 mg");
            assertThat(SignalMatcher.containsUnnegatedNear(
                    text, List.of("guideline"), anchor, anchor + 6, 40))
                    .isTrue();
        }

        @Test
        void aPhraseOutsideTheWindowDoesNotCount() {
            String filler = "x".repeat(300);
            String text = SignalMatcher.normalize("Take 500 mg. " + filler + " guideline");
            int anchor = text.indexOf("500 mg");
            assertThat(SignalMatcher.containsUnnegatedNear(
                    text, List.of("guideline"), anchor, anchor + 6, SignalMatcher.DEFAULT_PROXIMITY_WINDOW))
                    .isFalse();
        }
    }
}
