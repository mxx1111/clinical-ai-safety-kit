package io.github.mxx1111.medagentguard.fhir;

import java.util.Objects;

public final class FhirRequestException extends RuntimeException {

    private final String code;
    private final String evidence;

    public FhirRequestException(String code, String message, String evidence) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.evidence = evidence == null ? "" : evidence;
    }

    public String code() {
        return code;
    }

    public String evidence() {
        return evidence;
    }
}
