package dev.toolkitmc.guiapi.gui;

import java.util.function.Function;

/**
 * Pure-Java helpers for placeholder substitution (no Minecraft dependencies).
 */
final class PlaceholderUtil {

    // Private-use code points used to hide braces of untrusted text (see escapeBraces).
    private static final char ESC_OPEN  = '\uE000';
    private static final char ESC_CLOSE = '\uE001';

    private PlaceholderUtil() {}

    /**
     * Replaces every token that starts with {@code prefix} and ends at the next
     * closing brace (e.g. {@code {var:coins}}) with the result of {@code lookup}.
     *
     * Scanning resumes <em>after</em> each inserted value. Re-scanning inserted text
     * would loop forever whenever a value contains its own token — for example a
     * variable set from anvil input to the literal text {@code {var:input}}.
     */
    static String replaceTokens(String text, String prefix, Function<String, String> lookup) {
        int from = 0;
        int idx;
        while ((idx = text.indexOf(prefix, from)) >= 0) {
            int end = text.indexOf('}', idx);
            if (end < 0) break;
            String key = text.substring(idx + prefix.length(), end);
            String value = lookup.apply(key);
            if (value == null) value = "";
            text = text.substring(0, idx) + value + text.substring(end + 1);
            from = idx + value.length();
        }
        return text;
    }

    /** Masks braces so untrusted text (anvil input) is never re-read as a placeholder. */
    static String escapeBraces(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replace('{', ESC_OPEN).replace('}', ESC_CLOSE);
    }

    /** Restores braces masked by {@link #escapeBraces(String)}. */
    static String unescapeBraces(String s) {
        return s.replace(ESC_OPEN, '{').replace(ESC_CLOSE, '}');
    }
}
