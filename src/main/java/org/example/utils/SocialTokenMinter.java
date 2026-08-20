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
 * Mints a genuine Firebase ID token for a throwaway test identity.
 *
 * <p><b>Why this exists.</b> {@code /auth/registerGoogleUser} no longer trusts the {@code email}
 * and {@code providerUserId} in the request body. {@code services/auth/socialIdentity.js} verifies
 * the {@code providerToken} travelling with them, and with {@code SOCIAL_AUTH_ENFORCED=true} an
 * unverifiable token is refused with <b>401 {@code SOCIAL_TOKEN_INVALID}</b>. That control is the
 * whole point: before it, anyone who knew an address could POST it and be treated as that person,
 * including switching an existing account's role.
 *
 * <p>So a test has two options: switch the control off and test a configuration nobody ships, or
 * present a real token. This does the latter, using Firebase's own REST endpoint the way any
 * client does — {@code accounts:signUp} creates an email/password identity and hands back a real
 * ID token carrying that email. The backend then verifies it through its normal path, so the
 * tests exercise the production code rather than a bypass of it.
 *
 * <p><b>The email must match.</b> {@code identityMatchesClaim} refuses {@code token_has_no_email}
 * and {@code email_mismatch}; only a differing subject is tolerated (Firebase's uid is not the
 * Google {@code sub}). So the minted identity's email is the one the test must claim.
 *
 * <p>Self-contained: the JDK HTTP client plus the Jackson already on the classpath. Needs only
 * {@code firebase.webApiKey} — no service account, no firebase-admin, no shelling out to Node.
 * When the key is absent {@link #isConfigured()} is false and the social tests skip with an
 * explanation rather than failing; an unconfigured machine is not a defect.
 *
 * <p>Each call creates a REAL account in the Firebase project, so callers should use a unique
 * throwaway address per run (see {@link org.example.data.TestAccounts}).
 */
public final class SocialTokenMinter {

    private static final Logger LOG = LogManager.getLogger(SocialTokenMinter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SIGNUP_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=";

    /**
     * A minted identity.
     *
     * @param uid     Firebase {@code localId} — the token's subject
     * @param email   the address the token carries; the request must claim exactly this
     * @param idToken the value to send as {@code providerToken}
     */
    public record SocialIdentity(String uid, String email, String idToken) { }

    private SocialTokenMinter() {
        // static utility
    }

    public static String webApiKey() {
        return ConfigReader.get("firebase.webApiKey", "");
    }

    /** True when a Firebase web API key is configured, so tokens can be minted. */
    public static boolean isConfigured() {
        return !webApiKey().isBlank();
    }

    /**
     * Creates a Firebase identity for {@code email} and returns a real ID token for it.
     *
     * @throws IllegalStateException when the key is wrong, the address is already taken, or the
     *                               network is unavailable — all of which are setup problems the
     *                               caller should surface rather than swallow
     */
    public static SocialIdentity mintFor(String email) {
        String payload;
        try {
            payload = MAPPER.writeValueAsString(Map.of(
                    "email", email,
                    // Only ever used to create the identity; the token is what the test needs.
                    "password", "Trimio@2580",
                    "returnSecureToken", true));
        } catch (Exception e) {
            throw new IllegalStateException("Could not build the sign-up payload", e);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SIGNUP_URL + webApiKey()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15)).build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode body = MAPPER.readTree(response.body());
            String idToken = body.path("idToken").asText("");
            if (idToken.isBlank()) {
                throw new IllegalStateException("Firebase sign-up failed (HTTP "
                        + response.statusCode() + "): "
                        + body.path("error").path("message").asText(response.body()));
            }

            LOG.info("Minted a Firebase ID token for {}", email);
            return new SocialIdentity(body.path("localId").asText(),
                    body.path("email").asText(email), idToken);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not mint a Firebase ID token: " + e.getMessage(), e);
        }
    }
}
