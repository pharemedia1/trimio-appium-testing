package org.example.utils;

import org.example.config.ConfigReader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Minimal JDBC helper for backend verification from tests — e.g. reading the OTP the backend
 * just generated (Trimio stores it in {@code users.two_factor_secret}).
 *
 * <p>Connection settings come from {@link ConfigReader} so no credentials live in code:
 * {@code db.url} (default local Trimio DB), {@code db.user} (default postgres) and
 * {@code db.password} (no default — pass {@code -Ddb.password=…} or {@code DB_PASSWORD}).
 * Tests that need the DB should skip themselves when {@link #isConfigured()} is false.
 */
public final class DbHelper {

    /**
     * Where the Trimio database listens by default.
     *
     * <p>Port <b>5433</b>, not 5432. The backend's own {@code .env} moved to 5433 and the tests
     * followed it; a helper still pointing at 5432 fails to connect and every DB-backed assertion
     * quietly degrades to "couldn't check". Override with {@code -Ddb.url=…} when it moves again.
     */
    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5433/trimio";

    private DbHelper() {
        // static utility
    }

    public static boolean isConfigured() {
        String pass = ConfigReader.get("db.password");
        return pass != null && !pass.isBlank();
    }

    /**
     * The 6-digit OTP currently issued to {@code email}, or {@code null} if there is none.
     *
     * <p>The column no longer holds the code. Since the two-step hardening the backend stores
     * <em>SHA-256 of</em> the code and clears it on use, so a test cannot simply read it out —
     * which is the point of the change. The digits are recovered by hashing the whole six-digit
     * space until one matches: a million fast hashes, well under a second, and exactly the
     * offline-recovery limitation {@code shared/auth/otp.js} documents about using a fast hash
     * over so small a space. That is acceptable for a test harness reading its own fixture; it
     * is not a claim that the storage is weak in a way that matters, because the real
     * protections are the five-minute expiry, the attempt limit, and single use.
     *
     * <p>A legacy plaintext value is returned as-is so this still works against a backend that
     * predates the change.
     */
    public static String getOtp(String email) {
        String stored = readSecret(email);
        if (stored == null || stored.isBlank()) return null;
        if (isSixDigits(stored)) return stored;      // pre-hardening plaintext
        return recoverCodeFromHash(stored);
    }

    private static boolean isSixDigits(String v) {
        return v.length() == 6 && v.chars().allMatch(Character::isDigit);
    }

    /** Brute-forces the six-digit space against a SHA-256 digest. */
    private static String recoverCodeFromHash(String hexDigest) {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        String target = hexDigest.trim().toLowerCase();
        for (int i = 0; i < 1_000_000; i++) {
            String candidate = String.format("%06d", i);
            sha.reset();
            if (target.equals(toHex(sha.digest(candidate.getBytes(StandardCharsets.UTF_8))))) {
                return candidate;
            }
        }
        return null;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                               .append(Character.forDigit(b & 0xF, 16));
        return sb.toString();
    }

    // ---- disposable professional fixture -----------------------------------

    /** The identity document the fixture submits, spelled as readiness expects it. */
    public static final String IDENTITY_DOCUMENT_ALT = "Driving license Front image";

    /**
     * Creates a professional sitting in the admin's <b>Pending</b> queue, and returns its
     * {@code professional_id}.
     *
     * <p>Exists so the approval test can bring its OWN subject. Approving is the one admin action
     * the suite had deliberately left alone — it makes somebody bookable to real clients — and the
     * way to test it without inheriting that objection is to approve a professional the test
     * created and then removes, never one already in the queue.
     *
     * <p>A registered professional is NOT enough: signing up writes only a {@code users} row, and
     * a professional reaches the queue only once a profile is submitted. So the three rows the
     * queue reads from are written directly.
     *
     * <p>The NAME matters more than the address here: the admin's Pending queue renders
     * "{@code <first> <last>}" and no email at all, so the name is the only handle a test has on
     * its own row. Callers should make it unique per run.
     */
    public static long createPendingProfessional(String email, String firstName, String lastName) {
        String url = ConfigReader.get("db.url", DEFAULT_URL);
        String user = ConfigReader.get("db.user", "postgres");
        String pass = ConfigReader.get("db.password", "");
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            c.setAutoCommit(false);
            long userId;
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into users (user_type_id, email, account_status, email_verified_at) "
                            + "values (2, ?, 'active', now()) returning user_id")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    userId = rs.getLong(1);
                }
            }
            long professionalId;
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into professional (user_id) values (?) returning professional_id")) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    professionalId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into professional_profile (professional_id, first_name, last_name, "
                            + "approval_status) values (?, ?, ?, 'pending')")) {
                ps.setLong(1, professionalId);
                ps.setString(2, firstName);
                ps.setString(3, lastName);
                ps.executeUpdate();
            }
            // AND A DOCUMENT TO ACTUALLY APPROVE.
            //
            // Without one the detail screen reads "No ID document submitted" and offers no
            // Approve control at all — correctly, since there is nothing to decide. A fixture
            // that only appears in the queue is enough to test the queue and nothing else.
            //
            // alt_text matches what services/proReadiness.js compares on (case-insensitively);
            // a near-miss reads as "not submitted" and the professional stays blocked with no
            // explanation. status defaults to 'pending', which is the state under test.
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into images (user_id, professional_id, url, alt_text, purpose, status) "
                            + "values (?, ?, ?, ?, 'identity_document', 'pending')")) {
                ps.setLong(1, userId);
                ps.setLong(2, professionalId);
                ps.setString(3, "https://placehold.co/800x500/png?text=approval-fixture");
                ps.setString(4, IDENTITY_DOCUMENT_ALT);
                ps.executeUpdate();
            }
            c.commit();
            return professionalId;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the pending professional " + email
                    + ": " + e.getMessage(), e);
        }
    }

    /** The status the admin console has left on this professional's identity document. */
    public static String documentStatusOf(long professionalId) {
        return queryOne("select status from images where professional_id = ? "
                + "and purpose = 'identity_document' order by image_id desc limit 1",
                professionalId);
    }

    /** The approval status the admin console has left on this professional. */
    public static String approvalStatusOf(long professionalId) {
        return queryOne("select approval_status from professional_profile where professional_id = ?",
                professionalId);
    }

    /** Removes the fixture and everything hanging off it. */
    public static void deleteProfessional(long professionalId) {
        String url = ConfigReader.get("db.url", DEFAULT_URL);
        String user = ConfigReader.get("db.user", "postgres");
        String pass = ConfigReader.get("db.password", "");
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            c.setAutoCommit(false);
            Long userId = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "select user_id from professional where professional_id = ?")) {
                ps.setLong(1, professionalId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) userId = rs.getLong(1);
                }
            }
            // Inserting an identity_document image causes a verifications row to be created,
            // so the fixture owns that too — otherwise every run leaves an orphan behind.
            exec(c, "delete from verifications where professional_id = ?", professionalId);
            exec(c, "delete from images where professional_id = ?", professionalId);
            exec(c, "delete from professional_profile where professional_id = ?", professionalId);
            exec(c, "delete from professional where professional_id = ?", professionalId);
            if (userId != null) exec(c, "delete from users where user_id = ?", userId);
            c.commit();
        } catch (SQLException e) {
            // Cleanup failure must not turn a passing test red; it leaves one disposable row.
            throw new IllegalStateException("Could not remove professional " + professionalId
                    + ": " + e.getMessage(), e);
        }
    }

    private static void exec(Connection c, String sql, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private static String queryOne(String sql, long id) {
        String url = ConfigReader.get("db.url", DEFAULT_URL);
        String user = ConfigReader.get("db.user", "postgres");
        String pass = ConfigReader.get("db.password", "");
        try (Connection c = DriverManager.getConnection(url, user, pass);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB read failed: " + e.getMessage(), e);
        }
    }

    private static String readSecret(String email) {
        String url = ConfigReader.get("db.url", DEFAULT_URL);
        String user = ConfigReader.get("db.user", "postgres");
        String pass = ConfigReader.get("db.password", "");
        String sql = "select two_factor_secret from users where email = ?";
        try (Connection c = DriverManager.getConnection(url, user, pass);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB read failed for " + email + ": " + e.getMessage(), e);
        }
    }

    // ---- booking verification ----------------------------------------------

    /**
     * How many appointments a professional holds, found by the display name the app shows.
     *
     * <p>Used to prove a paid booking actually became a row rather than only a confirmation
     * screen. Returns -1 when the DB is not configured, so the caller can degrade to asserting on
     * the UI alone instead of failing for want of a password.
     *
     * @param professionalName the name as rendered in the booking shortlist, e.g. "Pat Pro"
     */
    public static long countAppointmentsFor(String professionalName) {
        String sql = "select count(*) from appointments a "
                + " join professional_profile pp on pp.professional_id = a.professional_id "
                + " where trim(concat(pp.first_name, ' ', pp.last_name)) = ?";
        String v = queryOneByName(sql, professionalName);
        return v == null ? -1 : Long.parseLong(v);
    }

    /**
     * The Stripe connected-account id a professional would be charged through, or null.
     *
     * <p>Exists to explain a refused charge. {@code resolveChargeAccount} rejects any id Stripe
     * could not have issued — seeded environments carry {@code acct_SEEDED_NOT_REAL_<id>} — and
     * that refusal reaches the client as a generic failure, so the id is worth naming in the test
     * output rather than leaving someone to find it.
     */
    public static String stripeAccountIdFor(String professionalName) {
        String sql = "select psa.stripe_account_id from professional_stripe_accounts psa "
                + " join professional_profile pp on pp.professional_id = psa.professional_id "
                + " where trim(concat(pp.first_name, ' ', pp.last_name)) = ?";
        return queryOneByName(sql, professionalName);
    }

    /** Runs a single-column, single-row query keyed on a name; null when unconfigured or absent. */
    private static String queryOneByName(String sql, String name) {
        if (!isConfigured()) {
            return null;
        }
        String url = ConfigReader.get("db.url", DEFAULT_URL);
        String user = ConfigReader.get("db.user", "postgres");
        String pass = ConfigReader.get("db.password", "");
        try (Connection c = DriverManager.getConnection(url, user, pass);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            // Verification is a bonus here, never the point of the test: a booking that the UI
            // confirmed is still a booking if this lookup cannot run.
            return null;
        }
    }
}
