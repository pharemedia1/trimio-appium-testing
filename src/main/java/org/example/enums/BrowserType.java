package org.example.enums;

/**
 * Supported browser engines. Maps a config value (e.g. "chromium") to a
 * type-safe enum used by {@link org.example.factory.PlaywrightFactory}.
 */
public enum BrowserType {
    CHROMIUM,
    FIREFOX,
    WEBKIT;

    /**
     * Resolves a config string to a {@link BrowserType}, case-insensitively.
     * Falls back to {@link #CHROMIUM} for null/blank/unknown values.
     */
    public static BrowserType from(String value) {
        if (value == null || value.isBlank()) {
            return CHROMIUM;
        }
        try {
            return BrowserType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CHROMIUM;
        }
    }
}
