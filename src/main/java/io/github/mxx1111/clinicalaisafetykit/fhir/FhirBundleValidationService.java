package io.github.mxx1111.clinicalaisafetykit.fhir;

public interface FhirBundleValidationService {

    FhirBundleValidationResult validate(String fhirJson);
}
