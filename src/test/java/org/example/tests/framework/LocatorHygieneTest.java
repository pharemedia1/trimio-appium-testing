package org.example.tests.framework;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A static check on the page objects themselves: no locator may anchor on a non-ASCII character.
 *
 * <p><b>Why this needs a test rather than a convention.</b> {@code UiSelector} cannot match a
 * non-ASCII character, and it does not say so — it simply finds nothing. A locator like
 * {@code descContains("Search services…")} or {@code descContains("★")} is therefore permanently
 * broken in a way that is indistinguishable, from the outside, from the control being absent. The
 * test times out after thirty seconds and reports "the screen did not render", and whoever reads
 * that goes to look at the app, which is working perfectly.
 *
 * <p>It is an easy mistake to make and an expensive one to find, because the app's copy is full of
 * em-dashes, ellipses, curly apostrophes, section signs and middots — all of which are the natural
 * thing to paste out of the Dart source. A sweep on 2026-09-22 found <b>nineteen</b> such
 * constants across thirteen page objects, every one of them a locator that could never match.
 *
 * <p>So the rule is mechanical and this enforces it: take an ASCII-safe substring of the copy.
 * {@code "Checking availability"} instead of {@code "Checking availability…"};
 * {@code "no-show fee"} instead of {@code "Charges the §5.5 no-show fee"}. The assertion is on the
 * <em>anchor</em>, not on the comment above it — prose may say whatever it likes.
 *
 * <p>Costs nothing and needs no device, so it runs in every suite.
 */
public class LocatorHygieneTest {

    private static final Path PAGES = Paths.get("src/main/java/org/example/pages");

    /** Calls whose string argument is handed to a UiSelector / accessibility-id matcher. */
    private static final Pattern LOCATOR_CALL = Pattern.compile(
            "\\b(descContains|accId|scrollAndTap|scrollAndTapExact|scrollToDesc"
                    + "|isPresentAfterScroll|buttonDescContains)\\(\\s*\"([^\"]*)\"");

    /** {@code public static final String X = "…"} — the constants those calls are given. */
    private static final Pattern ANCHOR_CONSTANT = Pattern.compile(
            "public static final String (\\w+)\\s*=\\s*\"([^\"]*)\"");

    @Test(description = "FW-001: no page-object locator anchors on a character UiSelector cannot match")
    public void locatorsAreAscii() throws IOException {
        Assert.assertTrue(Files.isDirectory(PAGES),
                "Page-object directory not found at " + PAGES.toAbsolutePath()
                        + " — run this from the project root.");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(PAGES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    // Javadoc and comments may quote the app's copy verbatim — that is often the
                    // clearest way to record what the real text is. Only code is checked.
                    if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                        continue;
                    }
                    collect(offenders, file, i + 1, LOCATOR_CALL.matcher(line), 2);
                    collect(offenders, file, i + 1, ANCHOR_CONSTANT.matcher(line), 2);
                }
            }
        }

        Assert.assertTrue(offenders.isEmpty(),
                "These locator anchors contain characters UiSelector cannot match, so they find "
                        + "nothing and fail as 'the screen did not render'. Use an ASCII-safe "
                        + "substring of the same copy:\n  " + String.join("\n  ", offenders));
    }

    private static void collect(List<String> offenders, Path file, int line,
                                Matcher matcher, int group) {
        while (matcher.find()) {
            String value = matcher.group(group);
            String nonAscii = value.codePoints()
                    .filter(c -> c > 127)
                    .mapToObj(c -> String.format("U+%04X '%c'", c, c))
                    .distinct()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            if (!nonAscii.isEmpty()) {
                offenders.add(file.getFileName() + ":" + line + "  \"" + value + "\"  ["
                        + nonAscii + "]");
            }
        }
    }
}
