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

    private static String readSecret(String email) {
        String url = ConfigReader.get("db.url", "jdbc:postgresql://localhost:5432/trimio");
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
}
