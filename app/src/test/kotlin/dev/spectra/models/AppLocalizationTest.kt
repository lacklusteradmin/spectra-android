package dev.spectra.models

import org.junit.Assert.assertEquals
import org.junit.Test

/** The tables are spectra-apple's; their Apple specifiers must read as Java's. */
class AppLocalizationTest {
    @Test
    fun appleObjectSpecifiersReadAsStrings() {
        assertEquals("Sent %s", AppLocalization.javaTemplate("Sent %@"))
        assertEquals("%1\$s on %2\$s", AppLocalization.javaTemplate("%1\$@ on %2\$@"))
    }

    @Test
    fun lengthModifiersAreDropped() {
        assertEquals("%d confirmations", AppLocalization.javaTemplate("%lld confirmations"))
        assertEquals("%2\$d of %1\$d", AppLocalization.javaTemplate("%2\$ld of %1\$lu"))
    }

    @Test
    fun floatsAndPercentSignsAreKept() {
        assertEquals("%.0f%% commission", AppLocalization.javaTemplate("%.0f%% commission"))
        assertEquals(String.format("%.0f%% commission", 5.0), "5% commission")
    }
}
