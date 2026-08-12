package io.github.mxx1111.clinicalaisafetykit.domain;

import java.util.Objects;

public record Finding(
        String ruleCode,
        Severity severity,
        String message,
        String evidence) {

    public Finding {
        Objects.requireNonNull(ruleCode, "ruleCode must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(message, "message must not be null");
        evidence = evidence == null ? "" : evidence;
    }
}
