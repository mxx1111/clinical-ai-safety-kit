package io.github.mxx1111.clinicalaisafetykit.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FhirPublicApiIsolationTest {

    private static final List<Class<?>> PUBLIC_FHIR_TYPES = List.of(
            FhirBundleValidationService.class,
            FhirBundleValidationResult.class,
            FhirBundleValidationFinding.class,
            FhirBundleValidationStatus.class,
            FhirRequestException.class,
            FhirValidationCodes.class);

    @Test
    void publicFhirApiDoesNotExposeHapiOrHl7Types() {
        List<String> exposedTypes = PUBLIC_FHIR_TYPES.stream()
                .flatMap(FhirPublicApiIsolationTest::referencedTypes)
                .map(Class::getName)
                .filter(name -> name.startsWith("ca.uhn.") || name.startsWith("org.hl7."))
                .toList();

        assertThat(exposedTypes).isEmpty();
    }

    private static Stream<Class<?>> referencedTypes(Class<?> type) {
        Stream<Class<?>> methodTypes = Stream.of(type.getDeclaredMethods())
                .flatMap(FhirPublicApiIsolationTest::methodTypes);
        Stream<Class<?>> componentTypes = type.isRecord()
                ? Stream.of(type.getRecordComponents()).map(RecordComponent::getType)
                : Stream.empty();
        return Stream.concat(methodTypes, componentTypes);
    }

    private static Stream<Class<?>> methodTypes(Method method) {
        return Stream.concat(Stream.of(method.getReturnType()), Stream.of(method.getParameterTypes()));
    }
}
