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
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mints and caches real Firebase ID tokens for the seeded Trimio test accounts.
 *
 * <p><b>Why this is the only way to test the API.</b> The backend runs with
 * {@code AUTH_ENFORCED=true} and {@code ADMIN_AUTH_ENFORCED=true}, so
 * {@code middleware/firebaseAuth.js} and {@code middleware/adminAuth.js} reject every request
 * that does not carry a verifiable {@code Authorization: Bearer <Firebase ID token>}. There is
 * no API key, no session cookie and no test bypass — the identity is resolved by looking up
 * {@code users.firebase_id} from the token's uid. A test that wants to call an authenticated
 * route therefore has to hold a genuine token for a genuine account.
 *
 * <p>It can, because {@code backend/scripts/seed_login_pair.js} creates each seeded account in
 * Firebase <em>as well as</em> Postgres, with the same password. So signing in through Firebase's
 * public REST endpoint — {@code accounts:signInWithPassword}, exactly what the app does — yields a
 * token the backend accepts. Nothing is stubbed and no enforcement is switched off: the tests
 * exercise the shipping auth path.
 *
 * <p>Distinct from {@link SocialTokenMinter}, which <em>creates</em> a throwaway identity for the
 * social sign-up tests. This one signs in as accounts that already exist.
 *
 * <p><b>Configuration.</b> Needs only {@code firebase.webApiKey} — the same public key the app
 * ships in {@code firebase_options.dart}. When it is absent {@link #isConfigured()} is false and
 * the API/security/performance suites skip with an explanation instead of failing, matching the
 * convention the rest of the framework uses for an unprovisioned environment.
 *
 * <p>Tokens are cached per email for slightly under their one-hour lifetime: a suite makes
 * hundreds of calls per role and Firebase rate-limits sign-ins.
 */
public final class FirebaseTokens {

    private static final Logger LOG = LogManager.getLogger(FirebaseTokens.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SIGN_IN_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=";

    /** Firebase ID tokens live an hour; re-mint at 50 minutes so none expires mid-suite. */
    private static final Duration TOKEN_TTL = Duration.ofMinutes(50);

    private static final Map<String, CachedToken> CACHE = new ConcurrentHashMap<>();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private record CachedToken(String idToken, String uid, Instant mintedAt) {
        boolean isFresh() {
            return Instant.now().isBefore(mintedAt.plus(TOKEN_TTL));
        }
    }

    private FirebaseTokens() {
        // static utility
    }

    /** True when {@code firebase.webApiKey} is available, so tokens can be minted. */
    public static boolean isConfigured() {
        return !apiKey().isBlank();
    }

    /** The reason the suite should skip, for a {@code SkipException} message. */
    public static String notConfiguredReason() {
        return "No firebase.webApiKey configured — the backend runs with AUTH_ENFORCED=true, so "
                + "authenticated API calls need a real Firebase ID token. Pass "
                + "-Dfirebase.webApiKey=… (the public web key from frontend/lib/firebase_options.dart) "
                + "to run this suite.";
    }

    /**
     * Returns a fresh ID token for {@code email}, signing in through Firebase if the cache is cold
     * or stale.
     *
     * @throws IllegalStateException when Firebase refuses the credentials — which is a real
     *                               finding (the seeded account is gone or its password changed),
     *                               not an environment gap, so it fails rather than skips
     */
    public static String idTokenFor(String email, String password) {
        CachedToken cached = CACHE.get(email);
        if (cached != null && cached.isFresh()) {
            return cached.idToken();
        }
        CachedToken minted = signIn(email, password);
        CACHE.put(email, minted);
        return minted.idToken();
    }

    /** The Firebase uid behind {@code email} — the value the backend matches on users.firebase_id. */
    public static String uidFor(String email, String password) {
        idTokenFor(email, password);
        return CACHE.get(email).uid();
    }

    /** Forgets every cached token; the next call signs in again. */
    public static void clearCache() {
        CACHE.clear();
    }

    private static CachedToken signIn(String email, String password) {
        if (!isConfigured()) {
            throw new IllegalStateException(notConfiguredReason());
        }
        try {
            String body = MAPPER.writeValueAsString(Map.of(
                    "email", email,
                    "password", password,
                    "returnSecureToken", true));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SIGN_IN_URL + apiKey()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = MAPPER.readTree(response.body());

            if (response.statusCode() != 200) {
                // Firebase reports the reason in error.message (EMAIL_NOT_FOUND,
                // INVALID_LOGIN_CREDENTIALS, TOO_MANY_ATTEMPTS_TRY_LATER…). Surface it: each one
                // points at a different fix, and "sign-in failed" points at none of them.
                throw new IllegalStateException("Firebase refused sign-in for " + email + ": "
                        + json.path("error").path("message").asText(response.body()));
            }
            LOG.info("Minted a Firebase ID token for {}", email);
            return new CachedToken(
                    json.path("idToken").asText(),
                    json.path("localId").asText(),
                    Instant.now());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Firebase sign-in for " + email + " failed: "
                    + e.getMessage(), e);
        }
    }

    private static String apiKey() {
        return ConfigReader.get("firebase.webApiKey", "");
    }
}
