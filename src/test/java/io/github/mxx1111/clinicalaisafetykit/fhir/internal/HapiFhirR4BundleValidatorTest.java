package io.github.mxx1111.clinicalaisafetykit.fhir.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import io.github.mxx1111.clinicalaisafetykit.fhir.FhirBundleValidationStatus;
import io.github.mxx1111.clinicalaisafetykit.fhir.FhirRequestException;
import io.github.mxx1111.clinicalaisafetykit.fhir.FhirValidationCodes;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HapiFhirR4BundleValidatorTest {

    private HapiFhirR4BundleValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HapiFhirR4BundleValidator(
                FhirContext.forR4Cached(),
                Clock.fixed(Instant.parse("2026-08-12T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void acceptsAValidSyntheticCollectionBundle() throws IOException {
        var result = validator.validate(fixture("valid-collection-bundle.json"));

        assertThat(result.status()).isEqualTo(FhirBundleValidationStatus.VALID);
        assertThat(result.fhirVersion()).isEqualTo("R4");
        assertThat(result.resourceType()).isEqualTo("Bundle");
        assertThat(result.bundleType()).isEqualTo("collection");
        assertThat(result.entryCount()).isEqualTo(1);
        assertThat(result.findings()).isEmpty();
        assertThat(result.validatedAt()).isEqualTo(Instant.parse("2026-08-12T03:00:00Z"));
    }

    @Test
    void reportsAMissingBundleTypeAsADeterministicFinding() throws IOException {
        var result = validator.validate(fixture("invalid-missing-bundle-type.json"));

        assertThat(result.status()).isEqualTo(FhirBundleValidationStatus.INVALID);
        assertThat(result.findings())
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(FhirValidationCodes.MISSING_BUNDLE_TYPE);
                    assertThat(finding.path()).isEqualTo("Bundle.type");
                });
    }

    @Test
    void reportsMissingTransactionRequestsWithoutEchoingResourceData() throws IOException {
        var result = validator.validate(fixture("invalid-transaction-missing-request.json"));

        assertThat(result.status()).isEqualTo(FhirBundleValidationStatus.INVALID);
        assertThat(result.findings())
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(FhirValidationCodes.MISSING_ENTRY_REQUEST);
                    assertThat(finding.path()).isEqualTo("Bundle.entry[0].request");
                });
        assertThat(result.toString())
                .doesNotContain("SYNTHETIC-DO-NOT-ECHO")
                .doesNotContain("synthetic-private-patient");
    }

    @Test
    void reportsAnIncompleteTransactionRequest() {
        String incompleteRequest = """
                {
                  "resourceType": "Bundle",
                  "type": "transaction",
                  "entry": [{"request": {}}]
                }
                """;

        var result = validator.validate(incompleteRequest);

        assertThat(result.status()).isEqualTo(FhirBundleValidationStatus.INVALID);
        assertThat(result.findings())
                .singleElement()
                .satisfies(finding ->
                        assertThat(finding.code()).isEqualTo(FhirValidationCodes.MISSING_ENTRY_REQUEST));
    }

    @Test
    void reportsADocumentBundleWithoutACompositionRoot() throws IOException {
        var result = validator.validate(fixture("invalid-document-missing-composition.json"));

        assertThat(result.status()).isEqualTo(FhirBundleValidationStatus.INVALID);
        assertThat(result.findings())
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(FhirValidationCodes.INVALID_DOCUMENT_ROOT);
                    assertThat(finding.path()).isEqualTo("Bundle.entry[0].resource");
                });
        assertThat(result.toString()).doesNotContain("synthetic-wrong-document-root");
    }

    @Test
    void rejectsMalformedJsonWithAStablePrivacySafeError() throws IOException {
        String malformed = fixture("invalid-malformed-json.json");

        assertThatThrownBy(() -> validator.validate(malformed))
                .isInstanceOfSatisfying(FhirRequestException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(FhirValidationCodes.MALFORMED_JSON);
                    assertThat(exception.getMessage()).doesNotContain(malformed);
                    assertThat(exception.evidence()).contains("intentionally omitted");
                });
    }

    @Test
    void rejectsANonBundleWithoutEchoingItsIdentifier() throws IOException {
        assertThatThrownBy(() -> validator.validate(fixture("invalid-non-bundle-patient.json")))
                .isInstanceOfSatisfying(FhirRequestException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(FhirValidationCodes.UNSUPPORTED_RESOURCE);
                    assertThat(exception.evidence()).isEqualTo("Detected resource type: Patient");
                    assertThat(exception.toString()).doesNotContain("SYNTHETIC-NON-BUNDLE-SECRET");
                });
    }

    @Test
    void rejectsAnOversizedPayloadBeforeParsing() {
        String oversized = "x".repeat(HapiFhirR4BundleValidator.MAX_PAYLOAD_CHARACTERS + 1);

        assertThatThrownBy(() -> validator.validate(oversized))
                .isInstanceOfSatisfying(FhirRequestException.class, exception ->
                        assertThat(exception.code()).isEqualTo(FhirValidationCodes.PAYLOAD_TOO_LARGE));
    }

    private static String fixture(String name) throws IOException {
        try (InputStream stream = HapiFhirR4BundleValidatorTest.class
                .getResourceAsStream("/fhir/r4/" + name)) {
            if (stream == null) {
                throw new IOException("Missing fixture: " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
