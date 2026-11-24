package dev.spectra.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.spectra.app.AppearanceMode
import dev.spectra.app.LocalAppState
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowSection
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization

/** The colour scheme: the system's, or always light or dark. The choice carries a check. */
@Composable
internal fun AppearanceSettingsPage() {
    val preferences = LocalAppState.current.preferences
    SpectraPage(title = AppLocalization.string("Appearance")) {
        SpectraRowSection(
            footer = AppLocalization.string("Controls the color scheme used throughout the app. Defaults to System."),
            dividerInset = SpectraLayout.rowHorizontal + 24.dp + SpectraLayout.Space.m,
        ) {
            AppearanceMode.entries.forEach { mode ->
                row {
                    val isSelected = preferences.appearanceMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = isSelected, role = Role.RadioButton) { preferences.appearanceMode = mode }
                            .spectraRowPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
                    ) {
                        Icon(mode.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

private val AppearanceMode.icon: ImageVector
    get() = when (this) {
        AppearanceMode.DARK -> Icons.Outlined.DarkMode
        AppearanceMode.LIGHT -> Icons.Outlined.LightMode
        AppearanceMode.SYSTEM -> Icons.Outlined.Contrast
    }
