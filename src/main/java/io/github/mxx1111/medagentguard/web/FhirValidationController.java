package io.github.mxx1111.medagentguard.web;

import io.github.mxx1111.medagentguard.fhir.FhirBundleValidationResult;
import io.github.mxx1111.medagentguard.fhir.FhirBundleValidationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fhir/r4")
public class FhirValidationController {

    private static final String FHIR_JSON = "application/fhir+json";

    private final FhirBundleValidationService validationService;

    public FhirValidationController(FhirBundleValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping(
            path = "/bundles/validate",
            consumes = {MediaType.APPLICATION_JSON_VALUE, FHIR_JSON},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public FhirBundleValidationResult validate(@RequestBody(required = false) String fhirJson) {
        return validationService.validate(fhirJson);
    }
}
