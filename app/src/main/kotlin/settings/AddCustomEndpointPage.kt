package dev.spectra.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Route
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraSwitchRow
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainCatalog
import dev.spectra.models.displayName
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.spectra_core.AppSettingUpdate
import uniffi.spectra_core.Chain
import uniffi.spectra_core.EndpointCapability
import uniffi.spectra_core.EndpointDirectoryEntry
import uniffi.spectra_core.StateCommand
import uniffi.spectra_core.StateEvent
import uniffi.spectra_core.endpointApiOptions
import uniffi.spectra_core.endpointCapabilityId

/**
 * Every network that takes an endpoint, in catalog order — including one
 * with no built-in provider, which is the network that most needs one.
 */
internal val endpointNetworks: List<Chain> by lazy { ChainCatalog.all.filter { endpointApiOptions(it).isNotEmpty() } }

/**
 * Adding an endpoint of the user's own, opened on [initialChain], or editing
 * [editing]: its network stays, and saving changes it where it stands.
 * [route] is the page's own, which it closes once core has stored the
 * endpoint.
 */
@Composable
internal fun AddCustomEndpointPage(route: Route, editing: EndpointDirectoryEntry?, initialChain: Chain?) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val networks = endpointNetworks
    var chain by rememberSaveable { mutableStateOf(editing?.record?.chainId ?: initialChain ?: networks.firstOrNull()) }
    val options = remember(chain) { chain?.let { endpointApiOptions(it) } ?: emptyList() }
    val types = options.map { it.id }
    var api by rememberSaveable {
        mutableStateOf(
            if (editing != null) {
                endpointApiOptions(editing.record.chainId).firstOrNull { it.api == editing.record.api }?.id ?: ""
            } else {
                types.firstOrNull() ?: ""
            },
        )
    }
    var url by rememberSaveable { mutableStateOf(editing?.record?.endpoint ?: "") }
    var capabilities by remember { mutableStateOf(editing?.record?.capabilities?.toSet() ?: emptySet()) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val capabilityOptions = options.firstOrNull { it.id == api }?.capabilities ?: emptyList()
    val canSave = url.isNotBlank() && api.isNotEmpty() && capabilities.isNotEmpty() && !isSaving

    fun save() {
        val network = chain ?: return
        val selected = options.firstOrNull { it.id == api }?.api ?: return
        isSaving = true
        scope.launch {
            try {
                val update = if (editing != null) {
                    AppSettingUpdate.ReplaceCustomEndpoint(network, editing.record.endpoint, selected, url, capabilities.toList())
                } else {
                    AppSettingUpdate.AddCustomEndpoint(capabilities.toList(), network, selected, url)
                }
                val transition = app.stateCommands.apply(StateCommand.SetAppSetting(update))
                if (transition.events.any { it is StateEvent.AppSettingRejected }) {
                    errorMessage = AppLocalization.string("endpoints.invalidEndpointMessage")
                } else {
                    navigator.dismiss(route)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = userErrorMessage(error)
            }
            isSaving = false
        }
    }

    SpectraPage(
        title = if (editing == null) AppLocalization.string("endpoints.addEndpointTitle") else AppLocalization.string("Edit Endpoint"),
        actions = {
            TextButton(onClick = ::save, enabled = canSave) { Text(AppLocalization.string("Save")) }
        },
    ) {
        FormSection {
            row {
                FormPickerRow(
                    label = AppLocalization.string("Network"),
                    options = networks,
                    selected = chain,
                    optionTitle = { it?.displayName ?: "" },
                    onSelect = { picked ->
                        // A change the user makes clears what was declared for
                        // the old API.
                        if (picked != chain) {
                            chain = picked
                            capabilities = emptySet()
                            val pickedTypes = picked?.let { endpointApiOptions(it) }?.map { it.id } ?: emptyList()
                            if (api !in pickedTypes) api = pickedTypes.firstOrNull() ?: ""
                        }
                    },
                    enabled = editing == null && !isSaving,
                )
            }
            row {
                FormPickerRow(
                    label = AppLocalization.string("endpoints.typeTitle"),
                    options = types,
                    selected = api,
                    optionTitle = { it },
                    onSelect = { picked ->
                        if (picked != api) {
                            if (api.isNotEmpty()) capabilities = emptySet()
                            api = picked
                        }
                    },
                    enabled = !isSaving,
                )
            }
            row {
                FormTextFieldRow(
                    value = url,
                    onValueChange = { url = it },
                    label = AppLocalization.string("endpoints.urlPlaceholder"),
                    enabled = !isSaving,
                    keyboardType = KeyboardType.Uri,
                )
            }
        }
        FormSection(
            title = AppLocalization.string("Endpoint capabilities"),
            footer = AppLocalization.string(
                if (capabilityOptions.isEmpty()) {
                    "This API has no supported custom endpoint operations."
                } else {
                    "Select the operations enabled on this endpoint. These are your declarations, not verified probe results."
                },
            ),
        ) {
            capabilityOptions.forEach { capability ->
                row { CapabilityToggle(capability, capability in capabilities, enabled = !isSaving) { on ->
                    capabilities = if (on) capabilities + capability else capabilities - capability
                } }
            }
        }
        errorMessage?.let { message -> FormSection { row { FormErrorRow(message) } } }
    }
}

@Composable
private fun CapabilityToggle(capability: EndpointCapability, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val id = endpointCapabilityId(capability)
    SpectraSwitchRow(
        title = AppLocalization.string("endpointCapability.$id"),
        subtitle = AppLocalization.string("endpointCapabilityDescription.$id"),
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
    )
}
