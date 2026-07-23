package org.example.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.config.ConfigReader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * A minimal HTTP client for the Trimio backend, used where a rule is enforced at the API rather
 * than in the UI.
 *
 * <p>The motivating case is the staff-only web portal. The backend decides whether a request came
 * from a browser purely from headers — an {@code Origin} or {@code Sec-Fetch-Site} on the request
 * (see {@code backend/services/webPortalAccess.js}) — and refuses phone-only roles with
 * <b>403 {@code WEB_PORTAL_NOT_AVAILABLE}</b>. That is testable <em>only</em> at this level: from a
 * real browser you cannot omit {@code Origin}, and from the app you cannot add it. So the pair of
 * assertions "a browser-shaped client login is refused" and "a native-shaped one still succeeds"
 * needs a client that controls its own headers, which is this.
 *
 * <p>Deliberately dependency-free (JDK {@code HttpClient} + the Jackson already on the classpath) —
 * adding RestAssured for four calls would not earn its keep.
 */
public final class ApiClient {

    private static final Logger LOG = LogManager.getLogger(ApiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient http;

    public ApiClient() {
        this(ConfigReader.get("api.baseUrl", "http://localhost:3000"));
    }

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** One HTTP response: status plus the parsed JSON body (null when the body isn't JSON). */
    public record Response(int status, String body, JsonNode json) {

        /** The {@code error_code} field, or "" when absent. */
        public String errorCode() {
            return json == null ? "" : json.path("error_code").asText("");
        }

        /** The {@code error} / {@code message} text, or "" when absent. */
        public String errorMessage() {
            if (json == null) {
                return "";
            }
            String error = json.path("error").asText("");
            return error.isBlank() ? json.path("message").asText("") : error;
        }

        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }

    /**
     * POSTs JSON.
     *
     * @param path    e.g. {@code "/auth/login"}
     * @param payload request body, serialised as JSON
     * @param headers extra headers — pass {@code Origin} to make the request look like a browser,
     *                or nothing at all to look like the native app
     */
    public Response postJson(String path, Map<String, Object> payload, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)));
            headers.forEach(builder::header);

            HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            LOG.info("API POST {} -> {}", path, response.statusCode());
            return new Response(response.statusCode(), response.body(), parse(response.body()));
        } catch (Exception e) {
            throw new IllegalStateException("POST " + path + " failed: " + e.getMessage(), e);
        }
    }

    /** GETs a path with optional headers. */
    public Response get(String path, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            headers.forEach(builder::header);

            HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            LOG.info("API GET {} -> {}", path, response.statusCode());
            return new Response(response.statusCode(), response.body(), parse(response.body()));
        } catch (Exception e) {
            throw new IllegalStateException("GET " + path + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Logs in the way a <em>browser</em> would — with an {@code Origin} header, which is exactly the
     * signal {@code webPortalAccess.isBrowserRequest()} keys on.
     */
    public Response loginAsBrowser(String email, String password, String origin) {
        return postJson("/auth/login",
                Map.of("email", email, "password", password),
                Map.of("Origin", origin, "Sec-Fetch-Site", "same-origin"));
    }

    /**
     * Logs in the way the <em>mobile app</em> would — no {@code Origin}, no fetch-metadata — so the
     * portal block must not apply.
     */
    public Response loginAsNativeClient(String email, String password) {
        return postJson("/auth/login", Map.of("email", email, "password", password), Map.of());
    }

    /** True when the backend is reachable — used to skip API tests cleanly rather than fail them. */
    public boolean isReachable() {
        try {
            get("/", Map.of());
            return true;
        } catch (RuntimeException e) {
            LOG.warn("Backend at {} is not reachable: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    private static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            return null;  // non-JSON body (HTML error page, plain text) — the caller has the raw text
        }
    }
}
