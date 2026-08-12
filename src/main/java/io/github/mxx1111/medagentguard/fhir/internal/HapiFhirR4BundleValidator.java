package io.github.mxx1111.medagentguard.fhir.internal;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import io.github.mxx1111.medagentguard.domain.Severity;
import io.github.mxx1111.medagentguard.fhir.FhirBundleValidationFinding;
import io.github.mxx1111.medagentguard.fhir.FhirBundleValidationResult;
import io.github.mxx1111.medagentguard.fhir.FhirBundleValidationService;
import io.github.mxx1111.medagentguard.fhir.FhirBundleValidationStatus;
import io.github.mxx1111.medagentguard.fhir.FhirRequestException;
import io.github.mxx1111.medagentguard.fhir.FhirValidationCodes;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.springframework.stereotype.Component;

@Component
public final class HapiFhirR4BundleValidator implements FhirBundleValidationService {

    public static final int MAX_PAYLOAD_CHARACTERS = 1_000_000;
    public static final String VALIDATOR_VERSION = "fhir-r4-2026-08-12.1";

    private static final Set<Bundle.BundleType> REQUEST_REQUIRED_TYPES = Set.of(
            Bundle.BundleType.BATCH,
            Bundle.BundleType.TRANSACTION,
            Bundle.BundleType.HISTORY);

    private final FhirContext fhirContext;
    private final Clock clock;

    public HapiFhirR4BundleValidator() {
        this(FhirContext.forR4Cached(), Clock.systemUTC());
    }

    HapiFhirR4BundleValidator(FhirContext fhirContext, Clock clock) {
        this.fhirContext = fhirContext;
        this.clock = clock;
    }

    @Override
    public FhirBundleValidationResult validate(String fhirJson) {
        validatePayload(fhirJson);
        IBaseResource resource = parse(fhirJson);
        if (!(resource instanceof Bundle bundle)) {
            String resourceType = fhirContext.getResourceDefinition(resource).getName();
            throw new FhirRequestException(
                    FhirValidationCodes.UNSUPPORTED_RESOURCE,
                    "The submitted FHIR R4 resource must be a Bundle.",
                    "Detected resource type: " + resourceType);
        }

        List<FhirBundleValidationFinding> findings = new ArrayList<>();
        validateBundleType(bundle, findings);
        validateEntryRequests(bundle, findings);
        validateDocumentRoot(bundle, findings);
        findings.sort(Comparator
                .comparing(FhirBundleValidationFinding::code)
                .thenComparing(FhirBundleValidationFinding::path));

        return new FhirBundleValidationResult(
                UUID.randomUUID().toString(),
                findings.isEmpty() ? FhirBundleValidationStatus.VALID : FhirBundleValidationStatus.INVALID,
                "R4",
                "Bundle",
                bundle.hasType() ? bundle.getType().toCode() : null,
                bundle.getEntry().size(),
                List.copyOf(findings),
                Instant.now(clock),
                VALIDATOR_VERSION);
    }

    private static void validatePayload(String fhirJson) {
        if (fhirJson == null || fhirJson.isBlank()) {
            throw malformedJson();
        }
        if (fhirJson.length() > MAX_PAYLOAD_CHARACTERS) {
            throw new FhirRequestException(
                    FhirValidationCodes.PAYLOAD_TOO_LARGE,
                    "The submitted FHIR payload exceeds the validation limit.",
                    "Maximum payload size: " + MAX_PAYLOAD_CHARACTERS + " characters.");
        }
    }

    private IBaseResource parse(String fhirJson) {
        try {
            return fhirContext.newJsonParser().parseResource(fhirJson);
        } catch (DataFormatException | IllegalArgumentException exception) {
            throw malformedJson();
        }
    }

    private static FhirRequestException malformedJson() {
        return new FhirRequestException(
                FhirValidationCodes.MALFORMED_JSON,
                "The request body is not a parseable FHIR R4 JSON resource.",
                "Parser details are intentionally omitted to avoid echoing submitted data.");
    }

    private static void validateBundleType(
            Bundle bundle,
            List<FhirBundleValidationFinding> findings) {
        if (!bundle.hasType()) {
            findings.add(new FhirBundleValidationFinding(
                    FhirValidationCodes.MISSING_BUNDLE_TYPE,
                    Severity.HIGH,
                    "Bundle.type",
                    "FHIR R4 Bundle.type is required.",
                    "The Bundle does not declare a type."));
        }
    }

    private static void validateEntryRequests(
            Bundle bundle,
            List<FhirBundleValidationFinding> findings) {
        if (!bundle.hasType() || !REQUEST_REQUIRED_TYPES.contains(bundle.getType())) {
            return;
        }

        for (int index = 0; index < bundle.getEntry().size(); index++) {
            Bundle.BundleEntryComponent entry = bundle.getEntry().get(index);
            if (!entry.hasRequest()
                    || !entry.getRequest().hasMethod()
                    || !entry.getRequest().hasUrl()) {
                findings.add(new FhirBundleValidationFinding(
                        FhirValidationCodes.MISSING_ENTRY_REQUEST,
                        Severity.HIGH,
                        "Bundle.entry[" + index + "].request",
                        "Each batch, transaction, or history entry requires request.method and request.url.",
                        "Required request metadata is incomplete; resource contents are omitted."));
            }
        }
    }

    private static void validateDocumentRoot(
            Bundle bundle,
            List<FhirBundleValidationFinding> findings) {
        if (!bundle.hasType() || bundle.getType() != Bundle.BundleType.DOCUMENT) {
            return;
        }

        if (bundle.getEntry().isEmpty()
                || !(bundle.getEntryFirstRep().getResource() instanceof Composition)) {
            findings.add(new FhirBundleValidationFinding(
                    FhirValidationCodes.INVALID_DOCUMENT_ROOT,
                    Severity.HIGH,
                    "Bundle.entry[0].resource",
                    "A document Bundle must begin with a Composition resource.",
                    "The expected Composition root is absent; resource contents are omitted."));
        }
    }
}
