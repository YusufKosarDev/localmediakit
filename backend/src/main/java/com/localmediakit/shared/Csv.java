package com.localmediakit.shared;

import java.util.List;

/**
 * CSV formatting for exports of user-supplied text.
 *
 * <p><b>Why this is not one String.join.</b> The rows exported here contain
 * text a stranger typed: the brand contact form is public, so the brand name
 * and the message are whatever a visitor sent. Two things follow.
 *
 * <p>The first is ordinary escaping -- a comma, a quote or a newline inside a
 * field would otherwise shift every column after it.
 *
 * <p>The second is the one that is easy to miss. A spreadsheet treats a cell
 * beginning {@code =}, {@code +}, {@code -} or {@code @} as a formula, and
 * some will happily evaluate it -- historically including things that reach
 * outside the document. The person opening this file is the creator, on their
 * own machine, having been sent the content by an anonymous visitor. Quoting
 * does not help: the quotes are consumed by the CSV parser before the cell is
 * interpreted. Prefixing the value so it can no longer start with one of those
 * characters does, and that is what happens here.
 */
public final class Csv {

    /** The characters a spreadsheet reads as "this cell is a formula". */
    private static final String FORMULA_STARTERS = "=+-@";

    /**
     * Also prefixed: a leading tab or carriage return is stripped by some
     * spreadsheets before the formula check, which would let "\t=1+1" through.
     */
    private static final String STRIPPED_LEADERS = "\t\r";

    private Csv() {
    }

    public static String row(List<String> values) {
        return String.join(",", values.stream().map(Csv::cell).toList());
    }

    /** Escaped, quoted when it has to be, and never a formula. */
    static String cell(String value) {
        String safe = neutraliseFormula(value == null ? "" : value);
        boolean needsQuotes = safe.contains(",") || safe.contains("\"")
                || safe.contains("\n") || safe.contains("\r");
        if (!needsQuotes) {
            return safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    /**
     * A single quote is the conventional neutraliser: spreadsheets read it as
     * "the rest is literal text" and do not display it, so the cell still looks
     * like what was typed while no longer being executable.
     */
    private static String neutraliseFormula(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (FORMULA_STARTERS.indexOf(first) >= 0 || STRIPPED_LEADERS.indexOf(first) >= 0) {
            return "'" + value;
        }
        return value;
    }
}
