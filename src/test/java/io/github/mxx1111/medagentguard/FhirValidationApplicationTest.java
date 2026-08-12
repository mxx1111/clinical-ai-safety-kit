package io.github.mxx1111.medagentguard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FhirValidationApplicationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void acceptsAValidBundleWithTheFhirJsonMediaType() throws Exception {
        HttpResponse<String> response = validate("valid-collection-bundle.json", "application/fhir+json");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/json");
        assertThat(response.body())
                .contains("\"status\":\"VALID\"")
                .contains("\"fhirVersion\":\"R4\"")
                .contains("\"resourceType\":\"Bundle\"")
                .contains("\"bundleType\":\"collection\"");
    }

    @Test
    void returnsInvalidFindingsWithoutEchoingIdentifiers() throws Exception {
        HttpResponse<String> response = validate(
                "invalid-transaction-missing-request.json", "application/json");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"status\":\"INVALID\"")
                .contains("MAG-FHIR-BUNDLE-REQUEST-001")
                .doesNotContain("SYNTHETIC-DO-NOT-ECHO")
                .doesNotContain("synthetic-private-patient");
    }

    @Test
    void rejectsMalformedJsonWithAStableErrorCode() throws Exception {
        HttpResponse<String> response = validate("invalid-malformed-json.json", "application/fhir+json");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body())
                .contains("\"code\":\"MAG-FHIR-PARSE-001\"")
                .contains("Parser details are intentionally omitted")
                .doesNotContain("resourceType");
    }

    @Test
    void rejectsAnEmptyBodyWithTheSameStableErrorCode() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/fhir/r4/bundles/validate"))
                .header("Content-Type", "application/fhir+json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body())
                .contains("\"code\":\"MAG-FHIR-PARSE-001\"")
                .contains("Parser details are intentionally omitted")
                .doesNotContain("resourceType");
    }

    @Test
    void rejectsANonBundleWithoutEchoingIdentifiers() throws Exception {
        HttpResponse<String> response = validate("invalid-non-bundle-patient.json", "application/json");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body())
                .contains("\"code\":\"MAG-FHIR-RESOURCE-001\"")
                .contains("Detected resource type: Patient")
                .doesNotContain("SYNTHETIC-NON-BUNDLE-SECRET")
                .doesNotContain("synthetic-not-a-bundle");
    }

    private HttpResponse<String> validate(String fixture, String contentType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/fhir/r4/bundles/validate"))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(readFixture(fixture)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String readFixture(String name) throws IOException {
        try (InputStream stream = FhirValidationApplicationTest.class
                .getResourceAsStream("/fhir/r4/" + name)) {
            if (stream == null) {
                throw new IOException("Missing fixture: " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
