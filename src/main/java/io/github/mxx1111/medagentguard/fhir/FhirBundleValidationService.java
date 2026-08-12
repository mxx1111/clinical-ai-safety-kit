package io.github.mxx1111.medagentguard.fhir;

public interface FhirBundleValidationService {

    FhirBundleValidationResult validate(String fhirJson);
}
