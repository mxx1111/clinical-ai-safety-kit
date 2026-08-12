package io.github.mxx1111.clinicalaisafetykit.fhir;

import java.time.Instant;
import java.util.List;

public record FhirBundleValidationResult(
        String validationId,
        FhirBundleValidationStatus status,
        String fhirVersion,
        String resourceType,
        String bundleType,
        int entryCount,
        List<FhirBundleValidationFinding> findings,
        Instant validatedAt,
        String validatorVersion) {
}
