package io.github.mxx1111.clinicalaisafetykit.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record EvaluationRequest(
        @NotBlank @Size(max = 100_000) String prompt,
        @NotBlank @Size(max = 100_000) String response,
        Map<String, Object> metadata) {

    public EvaluationRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
