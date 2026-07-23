package org.example.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Reads JSON test-data files from the classpath ({@code src/test/resources}) into
 * generic maps, ready to feed a TestNG {@code @DataProvider}.
 */
public final class JsonDataReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonDataReader() {
        // static utility
    }

    /**
     * Reads a JSON file containing an array of objects.
     *
     * @param classpathResource e.g. {@code "testdata/login-data.json"}
     * @return one map per array element (key -> value)
     */
    public static List<Map<String, Object>> readList(String classpathResource) {
        try (InputStream in = JsonDataReader.class.getClassLoader()
                .getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("Test data not found on classpath: " + classpathResource);
            }
            return MAPPER.readValue(in, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read test data: " + classpathResource, e);
        }
    }
}
