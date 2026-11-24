package dev.spectra.dashboard

import dev.spectra.app.Navigator
import dev.spectra.app.Routes

/** Settings' pages the dashboard opens: Tor's, from its indicator, and the endpoint catalog, from a notice. */
internal fun Navigator.openTorSettings() = push(Routes.TorSettings)

internal fun Navigator.openEndpointSettings() = push(Routes.EndpointSettings)
