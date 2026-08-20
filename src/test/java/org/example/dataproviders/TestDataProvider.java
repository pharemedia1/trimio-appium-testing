package org.example.dataproviders;

import org.example.utils.JsonDataReader;
import org.testng.annotations.DataProvider;

import java.util.List;
import java.util.Map;

/**
 * Central home for TestNG {@code @DataProvider}s. Each provider reads a JSON file from
 * {@code src/test/resources/testdata} and yields one {@code Map<String,Object>} row per test
 * invocation, so scenarios live in data files rather than in the test code.
 */
public class TestDataProvider {

    private static Object[][] asRows(String resource) {
        List<Map<String, Object>> rows = JsonDataReader.readList(resource);
        Object[][] data = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            data[i][0] = rows.get(i);
        }
        return data;
    }

    // ---- mobile (Appium) scenarios -----------------------------------------

    @DataProvider(name = "registrationNegative")
    public static Object[][] registrationNegative() {
        return asRows("testdata/mobile/registration-data.json");
    }

    @DataProvider(name = "loginNegative")
    public static Object[][] loginNegative() {
        return asRows("testdata/mobile/login-data.json");
    }

    @DataProvider(name = "forgotPasswordNegative")
    public static Object[][] forgotPasswordNegative() {
        return asRows("testdata/mobile/forgot-password-data.json");
    }

    @DataProvider(name = "otpNegative")
    public static Object[][] otpNegative() {
        return asRows("testdata/mobile/otp-data.json");
    }

    @DataProvider(name = "resetPasswordNegative")
    public static Object[][] resetPasswordNegative() {
        return asRows("testdata/mobile/reset-password-data.json");
    }
}
