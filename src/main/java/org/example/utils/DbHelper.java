package org.example.utils;

import org.example.config.ConfigReader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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

    /** @return the current OTP stored for {@code email}, or {@code null} if none/no user. */
    public static String getOtp(String email) {
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
