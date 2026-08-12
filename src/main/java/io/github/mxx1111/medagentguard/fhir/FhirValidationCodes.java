package io.github.mxx1111.medagentguard.fhir;

public final class FhirValidationCodes {

    public static final String MALFORMED_JSON = "MAG-FHIR-PARSE-001";
    public static final String UNSUPPORTED_RESOURCE = "MAG-FHIR-RESOURCE-001";
    public static final String PAYLOAD_TOO_LARGE = "MAG-FHIR-SIZE-001";
    public static final String MISSING_BUNDLE_TYPE = "MAG-FHIR-BUNDLE-TYPE-001";
    public static final String MISSING_ENTRY_REQUEST = "MAG-FHIR-BUNDLE-REQUEST-001";
    public static final String INVALID_DOCUMENT_ROOT = "MAG-FHIR-BUNDLE-DOCUMENT-001";

    private FhirValidationCodes() {
    }
}
