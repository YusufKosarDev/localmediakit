package com.localmediakit.shared;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exported file carries text a stranger typed -- the brand contact form is
 * public -- and it is opened by the creator in a spreadsheet on their own
 * machine. Both halves of that sentence are why this class exists.
 */
class CsvTest {

    @Test
    void ordinaryValuesArePassedThrough() {
        assertThat(Csv.row(List.of("Nike", "brand@example.com", "NEW")))
                .isEqualTo("Nike,brand@example.com,NEW");
    }

    @Test
    void separatorsInsideAFieldDoNotShiftTheColumns() {
        assertThat(Csv.cell("Ajans, 3. tur")).isEqualTo("\"Ajans, 3. tur\"");
        assertThat(Csv.cell("iki\nsatir")).isEqualTo("\"iki\nsatir\"");
    }

    @Test
    void quotesAreDoubledInsideAQuotedField() {
        assertThat(Csv.cell("bir \"alinti\"")).isEqualTo("\"bir \"\"alinti\"\"\"");
    }

    /* --- the part that is easy to miss --- */

    @Test
    void aCellCannotStartAFormula() {
        // Anyone can submit the contact form, so this is attacker-controlled
        // text landing in a file the creator opens in Excel.
        assertThat(Csv.cell("=1+1")).startsWith("'");
        assertThat(Csv.cell("+1234")).startsWith("'");
        assertThat(Csv.cell("-1+1")).startsWith("'");
        assertThat(Csv.cell("@SUM(A1)")).startsWith("'");
    }

    @Test
    void aLeadingTabDoesNotSmuggleAFormulaPast() {
        // Some spreadsheets strip leading whitespace before deciding whether a
        // cell is a formula, so checking only the first visible character is
        // not the same as checking the first character.
        assertThat(Csv.cell("\t=1+1")).isEqualTo("'\t=1+1");
        // A carriage return also forces quoting, so the neutraliser ends up
        // inside the quotes -- which is where it has to be to still work.
        assertThat(Csv.cell("\r=1+1")).isEqualTo("\"'\r=1+1\"");
    }

    @Test
    void neutralisingAlsoSurvivesQuoting() {
        // Quoting alone is no defence: the parser removes the quotes before the
        // cell is interpreted. A value that needs both must get both.
        String cell = Csv.cell("=cmd|'/c calc'!A1, ve virgul");

        assertThat(cell).startsWith("\"'");
        assertThat(cell).endsWith("\"");
    }

    @Test
    void aMinusSignInAPriceIsStillReadable() {
        // The neutraliser is a display-time no-op in a spreadsheet, so a value
        // that legitimately starts with one of these characters still reads
        // correctly -- it just is not executed.
        assertThat(Csv.cell("-500 TL")).isEqualTo("'-500 TL");
    }

    @Test
    void anAbsentValueIsAnEmptyCellRatherThanTheWordNull() {
        assertThat(Csv.row(java.util.Arrays.asList("Nike", null))).isEqualTo("Nike,");
    }
}
