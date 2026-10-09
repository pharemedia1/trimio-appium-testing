package org.example.tests.api;

import org.example.utils.ApiClient;
import org.example.utils.SocialTokenMinter;
import org.example.utils.SocialTokenMinter.SocialIdentity;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Social sign-up — {@code POST /auth/registerGoogleUser}.
 *
 * <p><b>Why this is an API suite and not a UI one.</b> The three provider buttons
 * ({@code SocialSignInRow} in {@code widgets/auth/auth_widgets.dart}) are an {@code InkWell}
 * wrapping an {@code Image.asset} with no {@code Semantics(label:)} and no {@code Text}, so they
 * export <em>no content-desc at all</em> — verified against the live tree, which shows the
 * divider "or sign up with" and then nothing until "Already have an account?". There is nothing
 * for UiAutomator2 to address. Even with a label, tapping one hands control to Google Play
 * Services or a provider web view, outside the app and outside Appium's reach. So the driver is
 * the endpoint the app calls once the provider hands back a token, which is where every rule
 * that matters actually lives.
 *
 * <p><b>Real tokens, not a bypass.</b> {@code SOCIAL_AUTH_ENFORCED=true} means an unverifiable
 * {@code providerToken} is refused outright, so these tests mint a genuine Firebase ID token
 * (see {@link SocialTokenMinter}) rather than switching the control off. The one thing that must
 * NOT be faked is the check being tested.
 *
 * <p>Order of enforcement in {@code authService.registerSocialUser}, which is why the cases are
 * grouped the way they are: required fields → token identity → account-type allowlist → web
 * portal block → create/adopt.
 */
public class SocialRegistrationTest {

    private static final String GOOGLE = "google";
    private static final int CLIENT = 1;
    private static final int PROFESSIONAL = 2;
    private static final int ADMIN = 3;
    private static final int VENDOR = 9;

    private static final String BROWSER_ORIGIN = "http://localhost:8080";
    private static final String MISSING_FIELDS = "Missing email, providerUserId, or userTypeId.";
    private static final String TYPE_REFUSED = "This account type cannot be self-registered.";
    private static final String PORTAL_CODE = "WEB_PORTAL_NOT_AVAILABLE";

    private final ApiClient api = new ApiClient();

    @BeforeClass(alwaysRun = true)
    public void requireBackend() {
        if (!api.isReachable()) {
            throw new SkipException("The Trimio backend is not reachable — start it with "
                    + "`cd ~/StudioProjects/trimio/backend && npm start`.");
        }
    }

    /** A fresh Firebase identity, or a skip when no web API key is configured. */
    private SocialIdentity newIdentity() {
        if (!SocialTokenMinter.isConfigured()) {
            throw new SkipException("Set firebase.webApiKey (or -Dfirebase.webApiKey=…) to run the "
                    + "social sign-up tests — they mint a real Firebase ID token because the "
                    + "backend verifies providerToken.");
        }
        return SocialTokenMinter.mintFor(
                "trimiotest+soc" + System.currentTimeMillis() + "@gmail.com");
    }

    private static Map<String, Object> payload(SocialIdentity id, int userTypeId, String platform) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", id.email());
        body.put("providerUserId", id.uid());
        body.put("userTypeId", userTypeId);
        body.put("platform", platform);
        body.put("providerToken", id.idToken());
        return body;
    }

    private ApiClient.Response register(Map<String, Object> body) {
        ApiClient.Response response = api.postJson("/auth/registerGoogleUser", body, Map.of());
        if (response.status() == 429) {
            // The backend's auth limiter, not an answer to the question this test asked.
            // /auth/* is capped per SOURCE (AUTH_RATE_MAX, default 60 per 15 minutes), so a
            // security or API suite run just before this one can exhaust the budget and every
            // assertion here then compares 429 against the status it expected -- reporting a
            // missing authorization control that is in fact working and simply was not consulted.
            throw new SkipException("The backend is rate-limiting /auth (429), so this request "
                    + "never reached the control being tested. Re-run this class on its own, or "
                    + "after the auth window clears. See backend/middleware/authRateLimit.js.");
        }
        return response;
    }

    // ---- happy path ---------------------------------------------------------

    @Test(description = "A new client registers through Google and an account is created")
    public void newClientRegistersViaGoogle() {
        SocialIdentity id = newIdentity();

        ApiClient.Response response = register(payload(id, CLIENT, GOOGLE));

        Assert.assertTrue(response.isSuccess(),
                "Social sign-up should succeed, got " + response.status() + ": " + response.body());
        Assert.assertTrue(response.json().path("success").asBoolean(),
                "Response should report success: " + response.body());
        Assert.assertFalse(response.json().path("userId").asText("").isBlank(),
                "A userId should be returned for the new account: " + response.body());
    }

    @Test(description = "A new professional registers through Google")
    public void newProfessionalRegistersViaGoogle() {
        SocialIdentity id = newIdentity();

        ApiClient.Response response = register(payload(id, PROFESSIONAL, GOOGLE));

        Assert.assertTrue(response.isSuccess(),
                "A professional social sign-up should succeed: " + response.body());
        Assert.assertFalse(response.json().path("userId").asText("").isBlank(),
                "A userId should be returned: " + response.body());
    }

    /**
     * The account a social sign-up creates must be able to AUTHENTICATE.
     *
     * <p>Everything above asserts that registration answers success and returns a userId. It
     * always did. What nothing checked was whether the row it created could then be used, and for
     * a long time it could not: {@code insertSocialUser} stored NULL in {@code users.firebase_id},
     * while {@code middleware/firebaseAuth.js} resolves every caller with
     * {@code WHERE u.firebase_id = $1} against the uid in their token. The account matched no
     * token, so every authenticated request answered 401 "User not recognized" — and a 401 signs
     * the app out. A Google sign-up produced an account that could not be used.
     *
     * <p>It went unseen because the two halves were never joined: these tests register and stop,
     * and the tests that authenticate use accounts seeded directly in SQL, which do have a
     * firebase_id. The symptom only appears when the SAME identity does both. It was eventually
     * found by signing in with Google by hand on an emulator, where the app printed
     * "No Trimio account for this sign-in (401)" and offered to register an account that already
     * existed.
     *
     * <p>{@code checkUserExists} is the endpoint under test because it is the one the app calls
     * next, and it sits behind {@code firebaseAuth}. 401 here is the regression; 200 means the
     * registration stored an identity the backend can resolve.
     */
    @Test(description = "An account made by social sign-up can authenticate with the same token")
    public void socialAccountCanAuthenticateAfterRegistration() {
        SocialIdentity id = newIdentity();

        ApiClient.Response created = register(payload(id, CLIENT, GOOGLE));
        Assert.assertTrue(created.isSuccess(),
                "Social sign-up should succeed before this test can check anything else, got "
                        + created.status() + ": " + created.body());

        ApiClient.Response authenticated = api.postJson(
                "/auth/checkUserExists",
                Map.of("email", id.email()),
                Map.of("Authorization", "Bearer " + id.idToken()));

        if (authenticated.status() == 429) {
            throw new SkipException("The backend is rate-limiting /auth (429), so this request "
                    + "never reached firebaseAuth. Re-run this class on its own.");
        }

        Assert.assertNotEquals(authenticated.status(), 401,
                "THE ACCOUNT CANNOT AUTHENTICATE. Social sign-up reported success and returned a "
                        + "userId, but the very next authenticated call with the SAME token was "
                        + "refused: " + authenticated.body() + ". firebaseAuth resolves callers by "
                        + "users.firebase_id, so this means the registration did not store the uid "
                        + "its token proves. Every request this account makes will be a 401, and a "
                        + "401 signs the app out — the person is left re-registering an account "
                        + "they already have.");
        Assert.assertEquals(authenticated.status(), 200,
                "The registered identity should be recognised by firebaseAuth, got "
                        + authenticated.status() + ": " + authenticated.body());
        Assert.assertEquals(
                authenticated.json().path("userId").asText(""),
                created.json().path("userId").asText("ABSENT"),
                "checkUserExists should resolve to the account registration just created, not to "
                        + "a different one: " + authenticated.body());
    }

    /**
     * The same person signing in again must be adopted, not duplicated.
     *
     * <p>Social sign-in is the one flow with no "already registered" screen — the app calls this
     * endpoint on every sign-in, so a second call with the same identity is the normal case, not
     * an edge case. Returning a different userId would mean a second account and an invisible
     * split of that person's bookings.
     */
    @Test(description = "Signing in again with the same identity adopts the existing account")
    public void repeatSignInIsIdempotent() {
        SocialIdentity id = newIdentity();

        String firstUserId = register(payload(id, CLIENT, GOOGLE)).json().path("userId").asText();
        String secondUserId = register(payload(id, CLIENT, GOOGLE)).json().path("userId").asText();

        Assert.assertFalse(firstUserId.isBlank(), "The first sign-up should return a userId");
        Assert.assertEquals(secondUserId, firstUserId,
                "A repeat social sign-in must adopt the same account, not create a second one");
    }

    /** The intended client <-> professional toggle, which only public types may do. */
    @Test(description = "An existing client may switch to professional through social sign-in")
    public void publicRoleMaySwitch() {
        SocialIdentity id = newIdentity();
        String asClient = register(payload(id, CLIENT, GOOGLE)).json().path("userId").asText();

        ApiClient.Response asPro = register(payload(id, PROFESSIONAL, GOOGLE));

        Assert.assertTrue(asPro.isSuccess(), "Switching to professional should succeed: " + asPro.body());
        Assert.assertEquals(asPro.json().path("userId").asText(), asClient,
                "The switch should keep the same account");
    }

    // ---- required fields (checked before the token) -------------------------

    @Test(description = "A sign-up with no email is rejected")
    public void missingEmailIsRejected() {
        SocialIdentity id = newIdentity();
        Map<String, Object> body = payload(id, CLIENT, GOOGLE);
        body.remove("email");

        ApiClient.Response response = register(body);

        Assert.assertEquals(response.status(), 400, "Expected 400: " + response.body());
        Assert.assertEquals(response.errorMessage(), MISSING_FIELDS);
    }

    @Test(description = "A sign-up with no providerUserId is rejected")
    public void missingProviderUserIdIsRejected() {
        SocialIdentity id = newIdentity();
        Map<String, Object> body = payload(id, CLIENT, GOOGLE);
        body.remove("providerUserId");

        ApiClient.Response response = register(body);

        Assert.assertEquals(response.status(), 400, "Expected 400: " + response.body());
        Assert.assertEquals(response.errorMessage(), MISSING_FIELDS);
    }

    @Test(description = "A sign-up with no userTypeId is rejected")
    public void missingUserTypeIsRejected() {
        SocialIdentity id = newIdentity();
        Map<String, Object> body = payload(id, CLIENT, GOOGLE);
        body.remove("userTypeId");

        ApiClient.Response response = register(body);

        Assert.assertEquals(response.status(), 400, "Expected 400: " + response.body());
        Assert.assertEquals(response.errorMessage(), MISSING_FIELDS);
    }

    // ---- token identity -----------------------------------------------------

    /**
     * The control that gives the rest of this endpoint its meaning.
     *
     * <p>Without it the body alone decided who you were: POST somebody's address and the server
     * adopted their account. A request carrying no token must be refused outright.
     */
    @Test(description = "A sign-up with no provider token is refused")
    public void missingProviderTokenIsRefused() {
        SocialIdentity id = newIdentity();
        Map<String, Object> body = payload(id, CLIENT, GOOGLE);
        body.remove("providerToken");

        ApiClient.Response response = register(body);

        Assert.assertEquals(response.status(), 401,
                "An unverified social sign-up must be refused: " + response.body());
    }

    @Test(description = "A sign-up with a garbage provider token is refused")
    public void invalidProviderTokenIsRefused() {
        SocialIdentity id = newIdentity();
        Map<String, Object> body = payload(id, CLIENT, GOOGLE);
        body.put("providerToken", "not-a-real-token");

        ApiClient.Response response = register(body);

        Assert.assertEquals(response.status(), 401,
                "A token that proves nothing must be refused: " + response.body());
    }

    /**
     * The impersonation case, and the reason the token is checked at all.
     *
     * <p>A VALID token for one identity, presented alongside somebody else's address. The email
     * binding is what carries the weight in {@code identityMatchesClaim}, so this must fail even
     * though the token itself verifies perfectly.
     */
    @Test(description = "A valid token cannot be used to claim a different email")
    public void tokenForAnotherEmailIsRefused() {
        SocialIdentity id = newIdentity();
        Map<String, Object> body = payload(id, CLIENT, GOOGLE);
        body.put("email", "trimiotest+victim" + System.currentTimeMillis() + "@gmail.com");

        ApiClient.Response response = register(body);

        Assert.assertEquals(response.status(), 401,
                "A token proving one identity must not register another: " + response.body());
    }

    // ---- account-type allowlist --------------------------------------------

    /**
     * Privilege escalation, blocked. {@code userTypeId} arrives in the request body, so without
     * an allowlist a social sign-up would mint an admin.
     */
    @Test(description = "Social sign-up cannot create an admin account")
    public void adminCannotBeSelfRegistered() {
        SocialIdentity id = newIdentity();

        ApiClient.Response response = register(payload(id, ADMIN, GOOGLE));

        Assert.assertEquals(response.status(), 403,
                "Registering an admin through social sign-up must be refused: " + response.body());
        Assert.assertEquals(response.errorMessage(), TYPE_REFUSED);
    }

    @Test(description = "Social sign-up cannot create a vendor account")
    public void vendorCannotBeSelfRegistered() {
        SocialIdentity id = newIdentity();

        ApiClient.Response response = register(payload(id, VENDOR, GOOGLE));

        Assert.assertEquals(response.status(), 403,
                "Registering a vendor through social sign-up must be refused: " + response.body());
        Assert.assertEquals(response.errorMessage(), TYPE_REFUSED);
    }

    // ---- platform allowlist -------------------------------------------------

    @Test(description = "Facebook and Apple are accepted platforms")
    public void otherProvidersAreAccepted() {
        for (String platform : new String[]{"facebook", "apple"}) {
            SocialIdentity id = newIdentity();

            ApiClient.Response response = register(payload(id, CLIENT, platform));

            Assert.assertTrue(response.isSuccess(),
                    "'" + platform + "' should be an accepted platform: " + response.body());
        }
    }

    /** An unrecognised platform falls back to google rather than failing the sign-up. */
    @Test(description = "An unknown platform still registers, falling back to google")
    public void unknownPlatformFallsBack() {
        SocialIdentity id = newIdentity();

        ApiClient.Response response = register(payload(id, CLIENT, "myspace"));

        Assert.assertTrue(response.isSuccess(),
                "An unknown platform should fall back rather than fail: " + response.body());
    }

    // ---- the staff-only portal rule ----------------------------------------

    /**
     * The portal block applies to social sign-up too, not just password login.
     *
     * <p>A client registering from a browser is exactly the hole a signup path would open in the
     * staff-only rule if it were enforced only on {@code /auth/login}.
     */
    @Test(description = "A browser-shaped social sign-up by a client is refused")
    public void browserSocialSignUpIsBlocked() {
        SocialIdentity id = newIdentity();

        ApiClient.Response response = api.postJson("/auth/registerGoogleUser",
                payload(id, CLIENT, GOOGLE),
                Map.of("Origin", BROWSER_ORIGIN, "Sec-Fetch-Site", "same-origin"));

        Assert.assertEquals(response.status(), 403,
                "A browser social sign-up by a client must be refused: " + response.body());
        Assert.assertEquals(response.errorCode(), PORTAL_CODE);
    }

    /**
     * The mandatory partner to the test above.
     *
     * <p>The browser check keys on headers, so it can only ever be a control. If it also caught
     * the phone apps — which send no {@code Origin} — that would be a far worse defect than the
     * one it prevents.
     */
    @Test(description = "The same sign-up still works from the native app")
    public void nativeSocialSignUpStillWorks() {
        SocialIdentity id = newIdentity();

        ApiClient.Response response = register(payload(id, CLIENT, GOOGLE));

        Assert.assertTrue(response.isSuccess(),
                "A native-shaped social sign-up must NOT be caught by the portal block: "
                        + response.body());
    }
}
