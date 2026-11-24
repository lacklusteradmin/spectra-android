package dev.spectra.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraNoticeCard
import dev.spectra.components.SpectraRowSection
import dev.spectra.components.SpectraRowSectionScope
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.color

// The parts the settings and diagnostics pages share: the rows of a system
// form as Material draws them, and the notices and headers around them.
// Internal to the module; the diagnostics package uses them too.

// ── Form rows ────────────────────────────────────────────────────────────

/** Where a divider starts in a form card: under the text, as its rows lead with no badge. */
internal val FormDividerInset: Dp = SpectraLayout.rowHorizontal

/**
 * A system form's section: a [SpectraRowSection] of rows with no leading
 * badge, a title over the card and an explanation under it.
 */
@Composable
internal fun FormSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: SpectraRowSectionScope.() -> Unit,
) {
    SpectraRowSection(modifier = modifier, title = title, footer = footer, dividerInset = FormDividerInset, content = content)
}

/** A line of text as a form row: a note, a status, an explanation. */
@Composable
internal fun FormTextRow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = secondaryText,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
) {
    Text(text, modifier = modifier.spectraRowPadding(), style = style, color = color)
}

/** A label and its value on one row, the value secondary: a fact a form only shows. */
@Composable
internal fun FormValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = secondaryText) {
    Row(
        modifier = modifier.spectraRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, textAlign = TextAlign.End)
    }
}

/**
 * A form's button: an action that runs in place, in the theme colour, as a
 * system form draws its buttons. [color] is the error colour for a
 * destructive one.
 */
@Composable
internal fun FormActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val tint = if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .spectraRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/**
 * A choice from a short list, as a system form's menu picker: the label, the
 * value chosen, and the list in a menu anchored to the row. The chosen
 * option carries a check.
 */
@Composable
internal fun <T> FormPickerRow(
    label: String,
    options: List<T>,
    selected: T,
    optionTitle: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    valueTitle: String = optionTitle(selected),
) {
    var expanded by remember { mutableStateOf(false) }
    val contentColor = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
                .spectraRowPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(SpectraLayout.Space.xs))
            }
            Text(label, style = MaterialTheme.typography.bodyLarge, color = contentColor, modifier = Modifier.weight(1f))
            Text(
                valueTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) secondaryText else contentColor,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Outlined.UnfoldMore, contentDescription = null, tint = secondaryText, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionTitle(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = if (option == selected) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * A [dev.spectra.components.SpectraStepper]'s inset as a form row: its
 * buttons' own touch padding stands in for most of the end inset.
 */
internal fun Modifier.formStepperPadding(): Modifier = padding(
    start = SpectraLayout.rowHorizontal,
    end = SpectraLayout.Space.s,
    top = SpectraLayout.Space.xs,
    bottom = SpectraLayout.Space.xs,
)

/** A text field as a form row, at the row's inset. */
@Composable
internal fun FormTextFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    autoCorrect: Boolean = false,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SpectraLayout.rowHorizontal, vertical = SpectraLayout.rowVertical),
        enabled = enabled,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            autoCorrectEnabled = autoCorrect,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
    )
}

/** An error in a row of its own: why a form could not be saved. */
@Composable
internal fun FormErrorRow(message: String, modifier: Modifier = Modifier) {
    FormTextRow(message, modifier = modifier, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

// ── Notices and headers ──────────────────────────────────────────────────

/**
 * Core's reason for refusing a change, in the error colour, until it is
 * closed: a refused save must not read as a save that did nothing.
 */
@Composable
internal fun DismissibleErrorNotice(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    SpectraNoticeCard(
        text = message,
        modifier = modifier,
        color = MaterialTheme.colorScheme.error,
        icon = Icons.Outlined.Warning,
        action = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = AppLocalization.string("Close"), tint = secondaryText)
            }
        },
    )
}

/** The icon, title and subtitle a page about one task opens with. */
@Composable
internal fun SettingsPageHeader(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = secondaryText)
        }
    }
}

/** A whole page with nothing to show: an icon over a title, centred. */
@Composable
internal fun UnavailableContent(title: String, icon: ImageVector, modifier: Modifier = Modifier, message: String? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = SpectraLayout.Space.xl * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        Icon(icon, contentDescription = null, tint = secondaryText, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center)
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = secondaryText, textAlign = TextAlign.Center)
        }
    }
}
