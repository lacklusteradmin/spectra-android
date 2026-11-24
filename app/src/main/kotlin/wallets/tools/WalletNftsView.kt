package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Route
import dev.spectra.components.AddressEntryRow
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.isWatchOnly
import dev.spectra.wallets.title
import uniffi.spectra_core.NftStandard
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletNft
import uniffi.spectra_core.WalletNfts
import uniffi.spectra_core.WalletOperation
import uniffi.spectra_core.WalletState

/** Sending one NFT the wallet holds. */
internal data class SendNftRoute(val walletId: String, val nft: WalletNft) : Route

/**
 * The ERC-721 and ERC-1155 tokens an EVM wallet holds, as core reads them
 * from its network's Blockscout inventory, and sending one: core reads the
 * collection's standard and the wallet's ownership, builds the collection's
 * `safeTransferFrom` as a send, and its page signs and broadcasts it through
 * the same stages. Token images are not loaded: a token's metadata can point
 * at any host, and fetching from it tells that host who looked.
 */
@Composable
internal fun WalletNftsView(wallet: WalletState) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val nfts = remember(wallet.id) { ToolRead<WalletNfts>() }
    suspend fun load() = nfts.load { app.bridge.ready().walletNfts(wallet.id) }
    LaunchedEffect(wallet.id) { load() }

    SpectraPage(title = WalletAction.NFTS.title, onRefresh = { load() }) {
        ToolReadSections(nfts) { read ->
            val stoppedShort = if (read.complete) {
                null
            } else {
                AppLocalization.string("The explorer's list stopped short; the wallet may hold more NFTs.")
            }
            if (read.nfts.isEmpty()) {
                WalletToolEmptySection(AppLocalization.string("This wallet holds no NFTs."))
                stoppedShort?.let { ToolSectionFooter(it) }
            } else {
                ToolListSection(items = read.nfts, footer = stoppedShort) { nft ->
                    Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                nft.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (nft.standard == NftStandard.ERC1155) {
                                Text(
                                    "×${nft.quantity}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                    maxLines = 1,
                                )
                            }
                        }
                        IdentifierText("#${nft.tokenId}")
                        IdentifierText(nft.contract, style = MaterialTheme.typography.labelSmall)
                        if (!wallet.signing.isWatchOnly) {
                            ToolRowButton(
                                text = AppLocalization.string("Send"),
                                onClick = { navigator.push(SendNftRoute(wallet.id, nft)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The token's own name, else its collection's, else its standard. */
internal val WalletNft.title: String
    get() {
        name?.takeIf { it.isNotEmpty() }?.let { return it }
        return collection.ifEmpty { standard.label }
    }

internal val NftStandard.label: String
    get() = when (this) {
        NftStandard.ERC721 -> "ERC-721"
        NftStandard.ERC1155 -> "ERC-1155"
    }

/**
 * Sending one NFT: the recipient, and how many of an ERC-1155 token; then
 * the transaction core builds, signed and broadcast through the stages.
 */
@Composable
internal fun SendNftView(wallet: WalletState, nft: WalletNft) {
    val app = LocalAppState.current
    var recipient by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }

    WalletOperationPage(
        wallet = wallet,
        title = AppLocalization.string("Send NFT"),
        authenticationReason = AppLocalization.format("Authenticate to send an NFT from %@", wallet.name),
        submittedText = AppLocalization.string("Submitted. The token moves once it confirms."),
        formIsComplete = recipient.isNotBlank(),
        build = {
            val count = if (nft.standard == NftStandard.ERC1155) quantity.trim() else "1"
            app.bridge.ready().buildNftTransfer(wallet.id, nft.contract, nft.tokenId, count, recipient.trim())
        },
        fields = {
            NftTokenSection(nft)
            ToolSection(
                footer = if (nft.standard == NftStandard.ERC1155) {
                    AppLocalization.format("This wallet holds %@ of this token.", nft.quantity)
                } else {
                    null
                },
            ) {
                AddressEntryRow(
                    title = AppLocalization.string("Destination Address"),
                    text = recipient,
                    onTextChange = { recipient = it },
                    chain = wallet.chainId,
                    contacts = app.addressBook.entries.filter { it.chainId == wallet.chainId },
                )
                if (nft.standard == NftStandard.ERC1155) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(AppLocalization.string("Quantity")) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        },
    ) { artifact ->
        NftTokenSection(nft)
        val transfer = artifact?.operation as? WalletOperation.TransferNft
        if (artifact != null && transfer != null) {
            ToolSection(
                footer = AppLocalization.string(
                    "A contract recipient must accept the token, or the transfer is refused and only the fee is spent.",
                ),
            ) {
                IdentifierValueRow(AppLocalization.string("To"), artifact.recipient)
                SpectraValueRow(label = AppLocalization.string("Quantity"), value = transfer.quantity)
            }
        }
    }
}

@Composable
private fun NftTokenSection(nft: WalletNft) {
    ToolSection {
        SpectraValueRow(label = AppLocalization.string("Token"), value = nft.title)
        IdentifierValueRow(AppLocalization.string("Token ID"), nft.tokenId)
        SpectraValueRow(label = AppLocalization.string("Standard"), value = nft.standard.label)
        IdentifierValueRow(AppLocalization.string("Contract"), nft.contract)
    }
}
