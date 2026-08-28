package com.wanyuecity.energy.platform.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls a 6-digit merchant code out of a free-text name. The source-platform
 * account name often embeds the code as part of a longer string
 * (e.g. "3066-000001-沪上阿姨"). The regex requires a 6-digit run that is
 * bounded by non-digit characters on both sides so substrings of longer
 * numbers are not picked up.
 */
public final class MerchantCodeExtractor {

    /** (?<!\d)\d{6}(?!\d) — exactly six consecutive digits, not part of a longer run. */
    private static final Pattern PATTERN = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    private MerchantCodeExtractor() {}

    public static Match match(String text) {
        if (text == null || text.isBlank()) {
            return Match.empty();
        }
        Matcher m = PATTERN.matcher(text);
        int count = 0;
        String first = null;
        while (m.find()) {
            if (first == null) {
                first = m.group();
            }
            count++;
        }
        if (first == null) {
            return Match.empty();
        }
        if (count == 1) {
            return new Match(first, "AUTO_MATCHED");
        }
        return new Match(first, "AMBIGUOUS");
    }

    public record Match(String code, String status) {
        public static Match empty() { return new Match(null, "NOT_FOUND"); }
        public boolean found() { return code != null; }
    }
}

