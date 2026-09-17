package com.reacthive.honeystyle;

import org.jetbrains.annotations.NotNull;
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
        return parse(raw, Map.of());
    }

    /**
     * @param customNames extra color names configured for the project, which win over the standard
     *                    ones so a project can redefine a name if it needs to
     */
    public static @Nullable Color parse(@Nullable String raw, @NotNull Map<String, String> customNames) {
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
        String named = customNames.get(value);
        if (named == null) {
            named = NAMED.get(value);
        }
        if (named != null) {
            value = named.trim().toLowerCase(Locale.ROOT);
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
        if (hp < 1) {
            r1 = c;
            g1 = x;
        } else if (hp < 2) {
            r1 = x;
            g1 = c;
        } else if (hp < 3) {
            g1 = c;
            b1 = x;
        } else if (hp < 4) {
            g1 = x;
            b1 = c;
        } else if (hp < 5) {
            r1 = x;
            b1 = c;
        } else {
            r1 = c;
            b1 = x;
        }
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

    /**
     * The 148 CSS named colors. Complete on purpose - a partial list silently fails to recognise
     * whatever it omits, and a user should not have to configure a W3C standard.
     */
    private static final String STANDARD_NAMED_COLORS = """
            aliceblue #F0F8FF
            antiquewhite #FAEBD7
            aqua #00FFFF
            aquamarine #7FFFD4
            azure #F0FFFF
            beige #F5F5DC
            bisque #FFE4C4
            black #000000
            blanchedalmond #FFEBCD
            blue #0000FF
            blueviolet #8A2BE2
            brown #A52A2A
            burlywood #DEB887
            cadetblue #5F9EA0
            chartreuse #7FFF00
            chocolate #D2691E
            coral #FF7F50
            cornflowerblue #6495ED
            cornsilk #FFF8DC
            crimson #DC143C
            cyan #00FFFF
            darkblue #00008B
            darkcyan #008B8B
            darkgoldenrod #B8860B
            darkgray #A9A9A9
            darkgreen #006400
            darkgrey #A9A9A9
            darkkhaki #BDB76B
            darkmagenta #8B008B
            darkolivegreen #556B2F
            darkorange #FF8C00
            darkorchid #9932CC
            darkred #8B0000
            darksalmon #E9967A
            darkseagreen #8FBC8F
            darkslateblue #483D8B
            darkslategray #2F4F4F
            darkslategrey #2F4F4F
            darkturquoise #00CED1
            darkviolet #9400D3
            deeppink #FF1493
            deepskyblue #00BFFF
            dimgray #696969
            dimgrey #696969
            dodgerblue #1E90FF
            firebrick #B22222
            floralwhite #FFFAF0
            forestgreen #228B22
            fuchsia #FF00FF
            gainsboro #DCDCDC
            ghostwhite #F8F8FF
            gold #FFD700
            goldenrod #DAA520
            gray #808080
            green #008000
            greenyellow #ADFF2F
            grey #808080
            honeydew #F0FFF0
            hotpink #FF69B4
            indianred #CD5C5C
            indigo #4B0082
            ivory #FFFFF0
            khaki #F0E68C
            lavender #E6E6FA
            lavenderblush #FFF0F5
            lawngreen #7CFC00
            lemonchiffon #FFFACD
            lightblue #ADD8E6
            lightcoral #F08080
            lightcyan #E0FFFF
            lightgoldenrodyellow #FAFAD2
            lightgray #D3D3D3
            lightgreen #90EE90
            lightgrey #D3D3D3
            lightpink #FFB6C1
            lightsalmon #FFA07A
            lightseagreen #20B2AA
            lightskyblue #87CEFA
            lightslategray #778899
            lightslategrey #778899
            lightsteelblue #B0C4DE
            lightyellow #FFFFE0
            lime #00FF00
            limegreen #32CD32
            linen #FAF0E6
            magenta #FF00FF
            maroon #800000
            mediumaquamarine #66CDAA
            mediumblue #0000CD
            mediumorchid #BA55D3
            mediumpurple #9370DB
            mediumseagreen #3CB371
            mediumslateblue #7B68EE
            mediumspringgreen #00FA9A
            mediumturquoise #48D1CC
            mediumvioletred #C71585
            midnightblue #191970
            mintcream #F5FFFA
            mistyrose #FFE4E1
            moccasin #FFE4B5
            navajowhite #FFDEAD
            navy #000080
            oldlace #FDF5E6
            olive #808000
            olivedrab #6B8E23
            orange #FFA500
            orangered #FF4500
            orchid #DA70D6
            palegoldenrod #EEE8AA
            palegreen #98FB98
            paleturquoise #AFEEEE
            palevioletred #DB7093
            papayawhip #FFEFD5
            peachpuff #FFDAB9
            peru #CD853F
            pink #FFC0CB
            plum #DDA0DD
            powderblue #B0E0E6
            purple #800080
            rebeccapurple #663399
            red #FF0000
            rosybrown #BC8F8F
            royalblue #4169E1
            saddlebrown #8B4513
            salmon #FA8072
            sandybrown #F4A460
            seagreen #2E8B57
            seashell #FFF5EE
            sienna #A0522D
            silver #C0C0C0
            skyblue #87CEEB
            slateblue #6A5ACD
            slategray #708090
            slategrey #708090
            snow #FFFAFA
            springgreen #00FF7F
            steelblue #4682B4
            tan #D2B48C
            teal #008080
            thistle #D8BFD8
            tomato #FF6347
            turquoise #40E0D0
            violet #EE82EE
            wheat #F5DEB3
            white #FFFFFF
            whitesmoke #F5F5F5
            yellow #FFFF00
            yellowgreen #9ACD32
            """;

    private static Map<String, String> namedColors() {
        Map<String, String> map = new HashMap<>();
        for (String line : STANDARD_NAMED_COLORS.lines().toList()) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2) {
                map.put(parts[0], parts[1]);
            }
        }
        return Map.copyOf(map);
    }
}
