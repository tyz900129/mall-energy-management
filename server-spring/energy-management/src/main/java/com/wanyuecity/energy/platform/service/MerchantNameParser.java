package com.wanyuecity.energy.platform.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts a merchant/brand name without treating legacy account labels as authoritative. */
public final class MerchantNameParser {
    private static final Pattern STALL_PREFIX = Pattern.compile(
            "^(?:[A-Z]{1,3}\\d{2,5}[A-Z]?(?:[-+/]\\d+)?|[A-Z]\\d+-\\d+)\\s*-\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    private MerchantNameParser() {}

    public static String extract(String value, String merchantCode) {
        String text = normalize(value);
        if (text == null) return null;
        String code = merchantCode == null ? "" : merchantCode.trim();

        if (code.matches("\\d{6}")) {
            String[] parts = text.replaceFirst("(?i)^3066-?", "").split("-");
            for (int i = 0; i < parts.length; i++) {
                if (!code.equals(parts[i].trim())) continue;
                String after = join(parts, i + 1, parts.length);
                if (!after.isBlank()) return stripStallPrefix(after);
                String before = join(parts, 0, i);
                if (!before.isBlank()) return stripStallPrefix(before);
            }
        }

        text = text.replaceFirst("(?i)^3066-?", "").replaceAll("^-+|-+$", "").trim();
        String result = stripStallPrefix(text);
        return result.equals(code) ? null : result;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.replace('—', '-').replace('－', '-').replace('_', '-')
                .replaceAll("\\s*-\\s*", "-").trim();
    }

    private static String stripStallPrefix(String value) {
        String text = value.trim();
        Matcher matcher = STALL_PREFIX.matcher(text);
        return matcher.matches() ? matcher.group(1).trim() : text;
    }

    private static String join(String[] parts, int from, int to) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < to; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append('-');
            out.append(part);
        }
        return out.toString();
    }
}
