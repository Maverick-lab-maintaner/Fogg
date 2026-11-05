package com.example.dialsender.ble;

import java.text.Bidi;
import java.util.HashMap;
import java.util.Map;

/**
 * The H19 draws text strictly left to right with no Arabic shaping. To read
 * correctly it must be sent in visual form: letters swapped for their joined
 * presentation forms, and right-to-left runs reversed. This changes the
 * encoding for display only, not the words.
 */
final class ArabicVisual {
    private ArabicVisual() {
    }

    /** letter -> {isolated, final, initial, medial}; 0 = the letter has no such form. */
    private static final Map<Character, int[]> FORMS = new HashMap<>();

    private static void add(char c, int iso, int fin, int ini, int med) {
        FORMS.put(c, new int[] { iso, fin, ini, med });
    }

    /** Joins on both sides: four consecutive code points. */
    private static void dual(char c, int base) {
        add(c, base, base + 1, base + 2, base + 3);
    }

    /** Joins to the previous letter only. */
    private static void right(char c, int base) {
        add(c, base, base + 1, 0, 0);
    }

    static {
        add('ء', 0xFE80, 0, 0, 0);
        right('آ', 0xFE81);
        right('أ', 0xFE83);
        right('ؤ', 0xFE85);
        right('إ', 0xFE87);
        dual('ئ', 0xFE89);
        right('ا', 0xFE8D);
        dual('ب', 0xFE8F);
        right('ة', 0xFE93);
        dual('ت', 0xFE95);
        dual('ث', 0xFE99);
        dual('ج', 0xFE9D);
        dual('ح', 0xFEA1);
        dual('خ', 0xFEA5);
        right('د', 0xFEA9);
        right('ذ', 0xFEAB);
        right('ر', 0xFEAD);
        right('ز', 0xFEAF);
        dual('س', 0xFEB1);
        dual('ش', 0xFEB5);
        dual('ص', 0xFEB9);
        dual('ض', 0xFEBD);
        dual('ط', 0xFEC1);
        dual('ظ', 0xFEC5);
        dual('ع', 0xFEC9);
        dual('غ', 0xFECD);
        dual('ف', 0xFED1);
        dual('ق', 0xFED5);
        dual('ك', 0xFED9);
        dual('ل', 0xFEDD);
        dual('م', 0xFEE1);
        dual('ن', 0xFEE5);
        dual('ه', 0xFEE9);
        right('و', 0xFEED);
        right('ى', 0xFEEF);
        dual('ي', 0xFEF1);
    }

    private static boolean joinsForward(char c) {
        int[] f = FORMS.get(c);
        return f != null && f[2] != 0;
    }

    private static boolean isMark(char c) {
        return (c >= 'ً' && c <= 'ْ') || c == 'ٰ';
    }

    /** Lam-alef ligature {isolated, final}, or null. */
    private static int[] lamAlef(char alef) {
        switch (alef) {
            case 'آ': return new int[] { 0xFEF5, 0xFEF6 };
            case 'أ': return new int[] { 0xFEF7, 0xFEF8 };
            case 'إ': return new int[] { 0xFEF9, 0xFEFA };
            case 'ا': return new int[] { 0xFEFB, 0xFEFC };
            default: return null;
        }
    }

    /** Swap letters for their joined forms; marks are dropped (the watch cannot stack them). */
    static String shape(String in) {
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < in.length(); i++)
            if (!isMark(in.charAt(i)))
                clean.append(in.charAt(i));
        String s = clean.toString();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int[] f = FORMS.get(c);
            if (f == null) {
                out.append(c);
                continue;
            }
            boolean prev = i > 0 && joinsForward(s.charAt(i - 1));
            if (c == 'ل' && i + 1 < s.length()) {
                int[] la = lamAlef(s.charAt(i + 1));
                if (la != null) {
                    out.append((char) (prev ? la[1] : la[0]));
                    i++;
                    continue;
                }
            }
            boolean next = i + 1 < s.length() && FORMS.containsKey(s.charAt(i + 1)) && f[2] != 0;
            int form = prev && next ? f[3] : prev ? f[1] : next ? f[2] : f[0];
            out.append((char) (form != 0 ? form : f[0]));
        }
        return out.toString();
    }

    /** Lay text out in display order, right-to-left runs reversed, Latin and digits left as they are. */
    static String reorder(String s) {
        Bidi bidi = new Bidi(s, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        int n = bidi.getRunCount();
        byte[] levels = new byte[n];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            levels[i] = (byte) bidi.getRunLevel(i);
            order[i] = i;
        }
        Bidi.reorderVisually(levels, 0, order, 0, n);
        StringBuilder out = new StringBuilder();
        for (Integer run : order) {
            String part = s.substring(bidi.getRunStart(run), bidi.getRunLimit(run));
            out.append((bidi.getRunLevel(run) & 1) == 1 ? new StringBuilder(part).reverse() : part);
        }
        return out.toString();
    }

    /**
     * The H19 ignores every line-break character and only wraps at a plain space, so a
     * row is built as one unbreakable piece: words glued with the ideographic space,
     * the one blank tested on the watch that it will not break at.
     */
    private static final char GLUE = (char) 0x3000;
    /** Row width in letters; a glue space counts as two. A screen row holds about 28. */
    private static final int ROW_MAX = 20;
    /** Two rows must never fit one screen row, or the second would land on the left. */
    private static final int ROW_MIN = 16;

    private static int units(String logical) {
        int n = 0;
        for (char c : shape(logical).toCharArray())
            n += c == ' ' ? 2 : 1;
        return n;
    }

    private static String row(String logical) {
        StringBuilder out = new StringBuilder(reorder(shape(logical)).replace(' ', GLUE));
        int pad = Math.max(0, (ROW_MIN - units(logical) + 1) / 2);
        for (int i = 0; i < pad; i++) {
            if (i % 2 == 0)
                out.append(GLUE);
            else
                out.insert(0, GLUE);
        }
        return out.toString();
    }

    /** Joined letters in display order, for a short text such as a title. */
    static String flat(String s) {
        return reorder(shape(s));
    }

    /**
     * What the watch needs: joined letters in display order, flipped one row at a time
     * so the text starts on the top row. Rows are separated by plain spaces, where the
     * watch breaks. Layout confirmed by photo on the H19.
     */
    static String visual(String s) {
        StringBuilder out = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (String w : s.trim().split("\\s+")) {
            if (line.length() > 0 && units(line + " " + w) > ROW_MAX) {
                out.append(row(line.toString())).append(' ');
                line.setLength(0);
            }
            if (line.length() > 0)
                line.append(' ');
            line.append(w);
        }
        return out.append(row(line.toString())).toString();
    }

    private static int bytes(String s) {
        return visual(s).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /** The longest run of whole words from the start of {@code s} that fits {@code maxBytes} once joined. */
    static String fit(String s, int maxBytes) {
        if (bytes(s) <= maxBytes)
            return s;
        String[] words = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            String next = out.length() == 0 ? w : out + " " + w;
            if (bytes(next) > maxBytes)
                break;
            out.setLength(0);
            out.append(next);
        }
        return out.toString();
    }

    /** Split into whole-word parts that each fit, so nothing is lost. */
    static java.util.List<String> split(String s, int maxBytes) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        String rest = s.trim();
        while (!rest.isEmpty()) {
            String part = fit(rest, maxBytes);
            if (part.isEmpty())
                break;
            parts.add(part);
            rest = rest.substring(part.length()).trim();
        }
        return parts;
    }

    static boolean hasArabic(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '؀' && c <= 'ۿ')
                return true;
        }
        return false;
    }
}
