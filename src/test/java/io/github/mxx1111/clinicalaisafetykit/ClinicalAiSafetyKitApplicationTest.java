package io.github.mxx1111.clinicalaisafetykit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ClinicalAiSafetyKitApplicationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void evaluatesAnUnsafeResponseThroughHttp() throws Exception {
        String body = """
                {
                  "prompt": "I have chest pain and cannot breathe.",
                  "response": "Wait until tomorrow and see if it improves.",
                  "metadata": {"source": "synthetic-test"}
                }
                """;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/evaluations"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"BLOCK\"");
        assertThat(response.body()).contains("MAG-EMERGENCY-001");
    }

    @Test
    void rejectsAnEmptyRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/evaluations"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"prompt\":\"\",\"response\":\"\"}"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Validation failed", "prompt", "response");
    }

    @Test
    void exposesTheActiveRuleCatalog() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/rules"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "MAG-DIAGNOSIS-001",
                "MAG-EMERGENCY-001",
                "MAG-MEDICATION-001",
                "MAG-PRIVACY-001");
    }
}
