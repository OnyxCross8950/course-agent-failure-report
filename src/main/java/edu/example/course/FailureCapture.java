package edu.example.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FailureCapture implements CourseDelivery.CaptureReporter {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String key;

    public FailureCapture(ObjectMapper json, @Value("${infrai.base-url}") String baseUrl,
                          @Value("${infrai.api-key}") String key) {
        this.json = json;
        this.baseUrl = baseUrl;
        this.key = key;
    }

    public void capture(String courseId, String learnerId, String runId, String step, Exception cause) {
        Map<String, Object> payload = Map.of(
                "title", "Course delivery agent step failed",
                "message", cause.toString(),
                "level", "error",
                "fingerprint", new String[] {"course-delivery", step},
                "exception", cause.toString(),
                "context", Map.of("courseId", courseId, "learnerId", learnerId, "runId", runId, "step", step));
        try {
            String body = json.writeValueAsString(payload);
            for (int attempt = 0; attempt < 4; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/errors/capture"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", runId + ":" + step)
                        .method("POST", HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    long seconds = response.headers().firstValue("Retry-After")
                            .flatMap(value -> {
                                try { return java.util.Optional.of(Long.parseLong(value)); }
                                catch (NumberFormatException ignored) { return java.util.Optional.<Long>empty(); }
                            }).orElse(1L << attempt);
                    Thread.sleep(Math.max(1, seconds) * 1000);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    throw new CaptureRejectedException(response.statusCode(), envelope.path("error").toString());
                }
                if (response.statusCode() >= 500) {
                    throw new IllegalStateException("Capture request failed: HTTP " + response.statusCode());
                }
                return;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Capture transport failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Capture interrupted", e);
        }
    }

    public static class CaptureRejectedException extends RuntimeException {
        private final int status;
        public CaptureRejectedException(int status, String error) {
            super(error);
            this.status = status;
        }
        public int status() { return status; }
    }
}
