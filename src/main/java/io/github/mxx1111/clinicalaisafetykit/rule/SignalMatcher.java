package io.github.mxx1111.clinicalaisafetykit.rule;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Deterministic lexical helpers shared by the text-safety rules.
 *
 * <p>Every rule in this project follows the same shape: a risk signal makes a response suspicious,
 * and a mitigating signal clears it. The first implementation cleared a response as soon as a
 * mitigating phrase appeared anywhere in the text. That is unsafe in both directions:
 *
 * <ul>
 *   <li><b>Negation bypass.</b> "This is not an emergency, get some sleep" contains the mitigating
 *       token {@code emergency}, so the CRITICAL escalation rule stayed silent on a response that
 *       actively talks the user out of seeking help.</li>
 *   <li><b>Incidental mention bypass.</b> "Take 500 mg. I cannot give you a guideline reference"
 *       contains {@code guideline}, so the dosage-without-source rule stayed silent.</li>
 * </ul>
 *
 * <p>This class narrows both holes with two deterministic checks: a mitigating phrase only counts
 * when it is not negated, and — where the rule has a concrete anchor such as a dosage — when it
 * appears near that anchor.
 *
 * <p>These are lexical heuristics, not natural-language understanding. They reduce a specific,
 * demonstrated class of bypass. They do not make the rules robust to paraphrase, and
 * {@code benchmarks/synthetic-text-safety-v1.json} tracks the cases that are still missed.
 */
public final class SignalMatcher {

    /**
     * How far back from a mitigating phrase to look for a negation cue.
     *
     * <p>Sized for a clause rather than a sentence. A longer window starts swallowing unrelated
     * negations from a previous sentence and turns safe responses into findings.
     */
    static final int NEGATION_LOOKBEHIND = 32;

    /** How far a mitigating phrase may sit from its anchor and still be treated as related. */
    static final int DEFAULT_PROXIMITY_WINDOW = 160;

    /**
     * Negation cues in English and Chinese.
     *
     * <p>Ordered longest-first is unnecessary here because presence, not identity, is what matters.
     * English cues are matched with word boundaries so that {@code no} does not fire inside
     * {@code normal} or {@code nothing}; Chinese cues are matched as plain substrings because the
     * language has no such boundaries.
     */
    private static final List<String> ENGLISH_NEGATIONS = List.of(
            "not", "no", "never", "without", "cannot", "cant", "dont", "doesnt", "isnt",
            "arent", "wont", "unable", "unlikely", "rather than", "instead of", "no need");

    private static final List<String> CHINESE_NEGATIONS = List.of(
            "不", "没", "无", "非", "别", "勿", "未", "免");

    private SignalMatcher() {
    }

    /**
     * Returns the first occurrence of any phrase that is not preceded by a negation cue.
     *
     * @param normalizedText lower-cased, whitespace-collapsed text
     * @param phrases        lower-cased phrases to look for
     * @return the matched phrase, or empty when every occurrence is negated or absent
     */
    public static Optional<String> firstUnnegated(String normalizedText, List<String> phrases) {
        return firstUnnegatedMatch(normalizedText, phrases).map(Match::phrase);
    }

    /** Returns whether any phrase occurs without being negated. */
    public static boolean containsUnnegated(String normalizedText, List<String> phrases) {
        return firstUnnegatedMatch(normalizedText, phrases).isPresent();
    }

    /**
     * Returns whether any phrase occurs unnegated within {@code window} characters of an anchor.
     *
     * <p>Used when a mitigating signal is only meaningful next to the thing it mitigates — a source
     * citation clears the dosage it accompanies, not a dosage three paragraphs away.
     *
     * @param anchorStart start index of the anchor inside {@code normalizedText}
     * @param anchorEnd   end index of the anchor inside {@code normalizedText}
     */
    public static boolean containsUnnegatedNear(
            String normalizedText, List<String> phrases, int anchorStart, int anchorEnd, int window) {
        int from = Math.max(0, anchorStart - window);
        int to = Math.min(normalizedText.length(), anchorEnd + window);
        String region = normalizedText.substring(from, to);
        return containsUnnegated(region, phrases);
    }

    private static Optional<Match> firstUnnegatedMatch(String normalizedText, List<String> phrases) {
        Match earliest = null;
        for (String phrase : phrases) {
            int index = normalizedText.indexOf(phrase);
            while (index >= 0) {
                if (!isNegated(normalizedText, index)) {
                    if (earliest == null || index < earliest.index()) {
                        earliest = new Match(phrase, index);
                    }
                    break;
                }
                index = normalizedText.indexOf(phrase, index + 1);
            }
        }
        return Optional.ofNullable(earliest);
    }

    /**
     * Returns whether the phrase starting at {@code index} sits inside the scope of a negation.
     *
     * <p>Looks back a bounded window and stops at clause punctuation, so a negation from an earlier
     * clause does not leak forward.
     *
     * <p>Commas count as boundaries, and that matters more than it looks. Without them,
     * "If you are not improving, call emergency services" and "如果不舒服，请立即就医" both carry a
     * negation cue within the lookbehind window, and two entirely correct responses would be
     * reported as unsafe. Over-negating is the failure mode that makes a safety tool unusable.
     */
    private static boolean isNegated(String normalizedText, int index) {
        int from = Math.max(0, index - NEGATION_LOOKBEHIND);
        String before = normalizedText.substring(from, index);

        int lastBoundary = -1;
        for (char terminator :
                new char[] {'.', '!', '?', ';', ',', '。', '！', '？', '；', '，', '、', '\n'}) {
            lastBoundary = Math.max(lastBoundary, before.lastIndexOf(terminator));
        }
        String clause = before.substring(lastBoundary + 1);

        for (String cue : CHINESE_NEGATIONS) {
            if (clause.contains(cue)) {
                return true;
            }
        }
        String stripped = clause.replace("'", "").replace("’", "");
        for (String cue : ENGLISH_NEGATIONS) {
            if (containsWord(stripped, cue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Substring search that requires non-letter boundaries on both sides.
     *
     * <p>Keeps {@code no} from matching inside {@code normal} and {@code not} from matching inside
     * {@code notice}, which would otherwise negate large amounts of ordinary clinical text.
     */
    private static boolean containsWord(String text, String word) {
        int index = text.indexOf(word);
        while (index >= 0) {
            boolean leftFree = index == 0 || !isWordCharacter(text.charAt(index - 1));
            int after = index + word.length();
            boolean rightFree = after >= text.length() || !isWordCharacter(text.charAt(after));
            if (leftFree && rightFree) {
                return true;
            }
            index = text.indexOf(word, index + 1);
        }
        return false;
    }

    private static boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    /** Lower-cases with a fixed locale so that Turkish and similar locales cannot change matching. */
    public static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private record Match(String phrase, int index) {
    }
}
