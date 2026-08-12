package io.github.mxx1111.medagentguard.fhir;

import io.github.mxx1111.medagentguard.domain.Severity;
import java.util.Objects;

public record FhirBundleValidationFinding(
        String code,
        Severity severity,
        String path,
        String message,
        String evidence) {

    public FhirBundleValidationFinding {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(message, "message must not be null");
        evidence = evidence == null ? "" : evidence;
    }
}
