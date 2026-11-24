package dev.spectra.send

import androidx.navigation3.runtime.EntryProviderScope
import dev.spectra.app.Route
import dev.spectra.app.Routes

/**
 * The send flow's pages. The composer is one route: its steps follow each
 * other inside it, and Back inside it is a step back.
 */
fun EntryProviderScope<Route>.sendEntries() {
    entry<Routes.Send> { SendScreen() }
}
