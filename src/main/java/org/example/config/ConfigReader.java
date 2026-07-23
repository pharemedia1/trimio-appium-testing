package org.example.config;

import org.example.constants.FrameworkConstants;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads {@code config.properties} once and exposes typed getters.
 *
 * <p>Resolution precedence for every key:
 * <ol>
 *   <li>JVM system property ({@code -Dkey=value})</li>
 *   <li>environment variable (UPPER_SNAKE_CASE of the key)</li>
 *   <li>value from {@code config.properties}</li>
 * </ol>
 * This lets CI override {@code browser}, {@code headless}, {@code baseUrl}, etc. without
 * editing the file.
 */
public final class ConfigReader {

    private static final Properties PROPERTIES = new Properties();

    static {
        try (InputStream in = ConfigReader.class.getClassLoader()
                .getResourceAsStream(FrameworkConstants.CONFIG_FILE)) {
            if (in == null) {
                throw new IllegalStateException(
                        FrameworkConstants.CONFIG_FILE + " not found on the classpath");
            }
            PROPERTIES.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + FrameworkConstants.CONFIG_FILE, e);
        }
    }

    private ConfigReader() {
        // static utility
    }

    /** Returns the value for {@code key}, honouring the precedence rules above; null if absent. */
    public static String get(String key) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }
        String env = System.getenv(key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String prop = PROPERTIES.getProperty(key);
        return prop == null ? null : prop.trim();
    }

    /** Returns the value for {@code key}, or {@code defaultValue} if missing/blank. */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        return (value == null || value.isBlank()) ? defaultValue : Boolean.parseBoolean(value);
    }

    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
