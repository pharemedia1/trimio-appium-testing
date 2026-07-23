package org.example.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

/**
 * Loads {@code testdata/mobile/test-accounts.json} once and exposes typed accessors, so no
 * credentials or email patterns live in the test code. Loaded from the suite-level setup.
 */
public final class TestAccounts {

    private static final String RESOURCE = "testdata/mobile/test-accounts.json";
    private static JsonNode root;

    private TestAccounts() {
        // static holder
    }

    /** Reads the JSON once (idempotent) — call from {@code @BeforeSuite}. */
    public static synchronized void load() {
        if (root != null) {
            return;
        }
        try (InputStream in = TestAccounts.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Test-accounts file not found: " + RESOURCE);
            }
            root = new ObjectMapper().readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + RESOURCE, e);
        }
    }

    private static JsonNode root() {
        if (root == null) {
            load();
        }
        return root;
    }

    /** The single generic password used for all created test accounts. */
    public static String genericPassword() {
        return root().get("genericPassword").asText();
    }

    /** Default phone number for forms that require one. */
    public static String defaultPhone() {
        return root().get("defaultPhone").asText();
    }

    /** A valid new password (meets the reset screen's complexity rules) for reset-password tests. */
    public static String resetPassword() {
        return root().get("resetPassword").asText();
    }

    /** A fresh, unique client Gmail alias (delivers to trimiotest@gmail.com), e.g. for registration. */
    public static String newClientEmail() {
        return root().get("newClientEmailTemplate").asText().replace("{unique}", uniqueSuffix());
    }

    /** A fresh, unique professional Gmail alias. */
    public static String newProfessionalEmail() {
        return root().get("newProfessionalEmailTemplate").asText().replace("{unique}", uniqueSuffix());
    }

    /** Email of the pre-existing verified account (blank if not configured). */
    public static String verifiedEmail() {
        return root().path("verifiedAccount").path("email").asText("");
    }

    /** Password of the pre-existing verified account (blank if not configured). */
    public static String verifiedPassword() {
        return root().path("verifiedAccount").path("password").asText("");
    }

    /** True when a usable verified account is configured (positive tests run; otherwise skip). */
    public static boolean hasVerifiedAccount() {
        return !verifiedEmail().isBlank() && !verifiedPassword().isBlank();
    }

    // ---- per-role accounts --------------------------------------------------

    /**
     * Email of the account for {@code role} ("client", "professional", "admin", "vendor",
     * "support"), or "" when none is configured.
     *
     * <p>Role-scoped accounts are what make the signed-in suites possible at all: the app routes to a
     * completely different shell per {@code user_type_id}, so there is no such thing as a generic
     * logged-in session. Falls back to {@code verifiedAccount} for the client role, since that
     * account already exists for the auth suite.
     */
    public static String emailFor(String role) {
        String email = root().path("roleAccounts").path(role).path("email").asText("");
        if (email.isBlank() && "client".equalsIgnoreCase(role)) {
            return verifiedEmail();
        }
        return email;
    }

    /** Password of the account for {@code role}, or "" when none is configured. */
    public static String passwordFor(String role) {
        String password = root().path("roleAccounts").path(role).path("password").asText("");
        if (password.isBlank() && "client".equalsIgnoreCase(role)) {
            return verifiedPassword();
        }
        return password;
    }

    /** True when both an email and a password are configured for {@code role}. */
    public static boolean hasAccountFor(String role) {
        return !emailFor(role).isBlank() && !passwordFor(role).isBlank();
    }

    // ---- store / vendor fixtures --------------------------------------------

    /** A unique vendor/product slug, so repeated runs never collide on the unique-slug constraint. */
    public static String uniqueSlug(String prefix) {
        return prefix + "-" + uniqueSuffix();
    }

    /** Name of a product expected to be visible in the client shop, or "" when not configured. */
    public static String storeProductName() {
        return root().path("storeFixtures").path("clientVisibleProduct").asText("");
    }

    /** Name of a pro-only product, used to assert clients cannot see it. */
    public static String proOnlyProductName() {
        return root().path("storeFixtures").path("proOnlyProduct").asText("");
    }

    /** A shipping address for store checkout tests. */
    public static String shippingStreet() {
        return root().path("storeFixtures").path("shippingStreet").asText("1 Test Street");
    }

    /** A shipping city for store checkout tests. */
    public static String shippingCity() {
        return root().path("storeFixtures").path("shippingCity").asText("Miami");
    }

    private static String uniqueSuffix() {
        return String.valueOf(System.currentTimeMillis());
    }
}
