package com.reacthive.honeystyle;

import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the CSS color literals that appear on the right-hand side of a honey theme entry.
 */
public final class CssColorParser {

    private static final Pattern FUNCTIONAL =
            Pattern.compile("^(rgba?|hsla?)\\s*\\(([^)]*)\\)$", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> NAMED = namedColors();

    private CssColorParser() {
    }

    public static @Nullable Color parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (value.contains("gradient") || value.contains("var(") || value.contains("url(")) {
            return null;
        }
        if ("transparent".equals(value)) {
            return new Color(0, 0, 0, 0);
        }
        String named = NAMED.get(value);
        if (named != null) {
            value = named;
        }
        if (value.charAt(0) == '#') {
            return parseHex(value.substring(1));
        }
        Matcher matcher = FUNCTIONAL.matcher(value);
        if (matcher.matches()) {
            String fn = matcher.group(1).toLowerCase(Locale.ROOT);
            String[] parts = matcher.group(2).replace('/', ',').split(",");
            return fn.startsWith("rgb") ? parseRgb(parts) : parseHsl(parts);
        }
        return null;
    }

    /**
     * Applies an alpha multiplier the way {@code hexWithAlpha} / {@code resolveColor(path, alpha)} do.
     */
    public static Color withAlpha(Color color, double alpha) {
        int a = Math.clamp(Math.round(Math.clamp(alpha, 0d, 1d) * 255d), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    public static String toHex(Color color) {
        if (color.getAlpha() == 255) {
            return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
        }
        return String.format("#%02X%02X%02X%02X",
                color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private static @Nullable Color parseHex(String hex) {
        if (!hex.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            return null;
        }
        try {
            switch (hex.length()) {
                case 3, 4 -> {
                    int r = Integer.parseInt(hex.substring(0, 1).repeat(2), 16);
                    int g = Integer.parseInt(hex.substring(1, 2).repeat(2), 16);
                    int b = Integer.parseInt(hex.substring(2, 3).repeat(2), 16);
                    int a = hex.length() == 4 ? Integer.parseInt(hex.substring(3, 4).repeat(2), 16) : 255;
                    return new Color(r, g, b, a);
                }
                case 6, 8 -> {
                    int r = Integer.parseInt(hex.substring(0, 2), 16);
                    int g = Integer.parseInt(hex.substring(2, 4), 16);
                    int b = Integer.parseInt(hex.substring(4, 6), 16);
                    int a = hex.length() == 8 ? Integer.parseInt(hex.substring(6, 8), 16) : 255;
                    return new Color(r, g, b, a);
                }
                default -> {
                    return null;
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static @Nullable Color parseRgb(String[] parts) {
        if (parts.length < 3) {
            return null;
        }
        Double r = channel(parts[0], 255d);
        Double g = channel(parts[1], 255d);
        Double b = channel(parts[2], 255d);
        if (r == null || g == null || b == null) {
            return null;
        }
        double a = parts.length > 3 ? alpha(parts[3]) : 1d;
        return new Color(Math.clamp(Math.round(r), 0, 255),
                Math.clamp(Math.round(g), 0, 255),
                Math.clamp(Math.round(b), 0, 255),
                Math.clamp(Math.round(a * 255d), 0, 255));
    }

    private static @Nullable Color parseHsl(String[] parts) {
        if (parts.length < 3) {
            return null;
        }
        Double h = number(parts[0].replace("deg", ""));
        Double s = channel(parts[1], 1d);
        Double l = channel(parts[2], 1d);
        if (h == null || s == null || l == null) {
            return null;
        }
        double alpha = parts.length > 3 ? alpha(parts[3]) : 1d;
        double sat = Math.clamp(s, 0d, 1d);
        double light = Math.clamp(l, 0d, 1d);
        double c = (1 - Math.abs(2 * light - 1)) * sat;
        double hp = ((h % 360d) + 360d) % 360d / 60d;
        double x = c * (1 - Math.abs(hp % 2 - 1));
        double r1 = 0, g1 = 0, b1 = 0;
        if (hp < 1) { r1 = c; g1 = x; }
        else if (hp < 2) { r1 = x; g1 = c; }
        else if (hp < 3) { g1 = c; b1 = x; }
        else if (hp < 4) { g1 = x; b1 = c; }
        else if (hp < 5) { r1 = x; b1 = c; }
        else { r1 = c; b1 = x; }
        double m = light - c / 2;
        return new Color(Math.clamp(Math.round((r1 + m) * 255), 0, 255),
                Math.clamp(Math.round((g1 + m) * 255), 0, 255),
                Math.clamp(Math.round((b1 + m) * 255), 0, 255),
                Math.clamp(Math.round(alpha * 255), 0, 255));
    }

    private static @Nullable Double channel(String token, double percentBase) {
        String t = token.trim();
        if (t.endsWith("%")) {
            Double pct = number(t.substring(0, t.length() - 1));
            return pct == null ? null : pct / 100d * percentBase;
        }
        return number(t);
    }

    private static double alpha(String token) {
        String t = token.trim();
        if (t.endsWith("%")) {
            Double pct = number(t.substring(0, t.length() - 1));
            return pct == null ? 1d : pct / 100d;
        }
        Double n = number(t);
        return n == null ? 1d : n;
    }

    private static @Nullable Double number(String token) {
        try {
            return Double.parseDouble(token.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, String> namedColors() {
        Map<String, String> map = new HashMap<>();
        map.put("black", "#000000");
        map.put("white", "#FFFFFF");
        map.put("red", "#FF0000");
        map.put("green", "#008000");
        map.put("blue", "#0000FF");
        map.put("yellow", "#FFFF00");
        map.put("orange", "#FFA500");
        map.put("purple", "#800080");
        map.put("gray", "#808080");
        map.put("grey", "#808080");
        map.put("silver", "#C0C0C0");
        map.put("maroon", "#800000");
        map.put("olive", "#808000");
        map.put("lime", "#00FF00");
        map.put("aqua", "#00FFFF");
        map.put("cyan", "#00FFFF");
        map.put("teal", "#008080");
        map.put("navy", "#000080");
        map.put("fuchsia", "#FF00FF");
        map.put("magenta", "#FF00FF");
        map.put("pink", "#FFC0CB");
        map.put("brown", "#A52A2A");
        map.put("gold", "#FFD700");
        map.put("beige", "#F5F5DC");
        map.put("ivory", "#FFFFF0");
        map.put("coral", "#FF7F50");
        map.put("crimson", "#DC143C");
        map.put("indigo", "#4B0082");
        map.put("khaki", "#F0E68C");
        map.put("lavender", "#E6E6FA");
        map.put("salmon", "#FA8072");
        map.put("tan", "#D2B48C");
        map.put("tomato", "#FF6347");
        map.put("turquoise", "#40E0D0");
        map.put("violet", "#EE82EE");
        map.put("wheat", "#F5DEB3");
        map.put("whitesmoke", "#F5F5F5");
        map.put("aliceblue", "#F0F8FF");
        map.put("darkgrey", "#A9A9A9");
        map.put("darkgray", "#A9A9A9");
        map.put("lightgrey", "#D3D3D3");
        map.put("lightgray", "#D3D3D3");
        return Map.copyOf(map);
    }
}
