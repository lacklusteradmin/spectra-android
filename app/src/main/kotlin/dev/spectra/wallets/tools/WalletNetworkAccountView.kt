package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Route
import dev.spectra.components.AddressEntryRow
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraNoticeCard
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraSwitchRow
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.secondaryText
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.send.PaymentMemoRow
import dev.spectra.send.SendPaymentMemoField
import dev.spectra.wallets.title
import uniffi.spectra_core.NetworkAccount
import uniffi.spectra_core.PaymentMemo
import uniffi.spectra_core.PaymentMemoKind
import uniffi.spectra_core.SendStage
import uniffi.spectra_core.TonAccountState
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletNetworkAccount
import uniffi.spectra_core.WalletOperation
import uniffi.spectra_core.WalletState
import uniffi.spectra_core.paymentMemoKinds

/** Closing the wallet's XRP or Stellar account into another one. */
internal data class CloseAccountRoute(val walletId: String) : Route

/**
 * What the wallet's account on its network holds and needs beyond a
 * balance, as core reads it from a node: Tron's resources, the XRP and
 * Stellar reserve, a TON contract's state, the parts of a Substrate balance,
 * the storage a NEAR account pays for, and a Cardano address's stake address
 * with its rewards and delegation. Read-only, but for closing an account
 * that holds a reserve.
 */
@Composable
internal fun WalletNetworkAccountView(wallet: WalletState) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val account = remember(wallet.id) { ToolRead<WalletNetworkAccount>() }
    suspend fun load() = account.load { app.bridge.ready().walletNetworkAccount(wallet.id) }
    LaunchedEffect(wallet.id) { load() }

    SpectraPage(title = WalletAction.NETWORK_ACCOUNT.title, onRefresh = { load() }) {
        ToolReadSections(account) { read ->
            NetworkAccountContent(read.account, read.symbol)
            if (read.closable) {
                ToolSection(footer = AppLocalization.string("Recover the reserve by closing this account into another one.")) {
                    OutlinedButton(
                        onClick = { navigator.push(CloseAccountRoute(wallet.id)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(AppLocalization.string("Close Account")) }
                }
            }
        }
    }
}

@Composable
private fun NetworkAccountContent(account: NetworkAccount, symbol: String) {
    val hidesBalances = LocalAppState.current.preferences.hideBalances
    fun exact(amount: String) = AmountPresentation.exactAmount(amount, symbol)
    when (account) {
        is NetworkAccount.Tron -> {
            if (!account.activated) {
                AccountNotice(
                    AppLocalization.string(
                        "Not on the network yet: the first TRX sent to this address creates the account. Tokens alone do not.",
                    ),
                )
            }
            ToolSection(
                title = AppLocalization.string("Bandwidth"),
                footer = AppLocalization.format(
                    "Every transaction spends bandwidth for its size. Without enough, it burns TRX instead: %@ %@ for a TRX transfer.",
                    account.transferBurn,
                    symbol,
                ),
            ) {
                Resource(available = account.bandwidthAvailable, limit = account.bandwidthLimit)
                SpectraValueRow(label = AppLocalization.string("Price per Point"), value = exact(account.bandwidthPrice))
            }
            ToolSection(
                title = AppLocalization.string("Energy"),
                footer = AppLocalization.format(
                    "Token contracts spend energy. Without it, a token transfer burns about %@ %@, and at most %@ %@.",
                    account.tokenTransferBurn,
                    symbol,
                    account.tokenTransferCeiling,
                    symbol,
                ),
            ) {
                Resource(available = account.energyAvailable, limit = account.energyLimit)
                SpectraValueRow(label = AppLocalization.string("Price per Unit"), value = exact(account.energyPrice))
            }
        }
        is NetworkAccount.Reserve -> {
            if (!account.exists) {
                AccountNotice(
                    AppLocalization.format(
                        "Not on the network yet: a payment of at least %@ %@ creates the account.",
                        account.baseReserve,
                        symbol,
                    ),
                )
            }
            ToolSection(
                footer = AppLocalization.string(
                    "The network locks the reserve in the account; a send moves only what is above it. Each trust line, offer or other object the account owns locks more, and removing it frees that again.",
                ),
            ) {
                SpectraValueRow(label = AppLocalization.string("Balance"), value = exact(account.balance), isHidden = hidesBalances)
                SpectraValueRow(label = AppLocalization.string("Base Reserve"), value = exact(account.baseReserve))
                SpectraValueRow(
                    label = AppLocalization.format("Owned Objects (%lld)", account.ownedObjects.toLong()),
                    value = exact(account.objectReserve),
                )
                SpectraValueRow(label = AppLocalization.string("Total Reserve"), value = exact(account.totalReserve))
                SpectraValueRow(label = AppLocalization.string("Spendable"), value = exact(account.spendable), isHidden = hidesBalances)
            }
        }
        is NetworkAccount.Ton -> ToolSection(footer = account.state.explanation) {
            SpectraValueRow(label = AppLocalization.string("State"), value = account.state.title)
            account.contract?.let { IdentifierValueRow(AppLocalization.string("Wallet Contract"), it) }
        }
        is NetworkAccount.Substrate -> ToolSection(
            footer = AppLocalization.string(
                "Reserved is held by the network for deposits and is not in the free balance. Frozen is free balance that staking or another lock keeps from moving. An account that falls below the existential deposit is removed, and what is left in it is lost.",
            ),
        ) {
            SpectraValueRow(label = AppLocalization.string("Free"), value = exact(account.free), isHidden = hidesBalances)
            SpectraValueRow(label = AppLocalization.string("Reserved"), value = exact(account.reserved), isHidden = hidesBalances)
            SpectraValueRow(label = AppLocalization.string("Frozen"), value = exact(account.frozen), isHidden = hidesBalances)
            SpectraValueRow(label = AppLocalization.string("Existential Deposit"), value = exact(account.existentialDeposit))
            SpectraValueRow(label = AppLocalization.string("Transferable"), value = exact(account.transferable), isHidden = hidesBalances)
        }
        is NetworkAccount.Near -> ToolSection(
            footer = AppLocalization.string(
                "Every byte an account stores must be covered by NEAR it holds, except for a small account within the network's free allowance. Locked stake counts toward it; the rest is kept back from the balance, and a send cannot move it.",
            ),
        ) {
            SpectraValueRow(
                label = AppLocalization.string("Storage Used"),
                value = AppLocalization.formatCount("%lld bytes", account.storageBytes.toLong(), account.storageBytes.toLong()),
            )
            SpectraValueRow(label = AppLocalization.string("Price per Byte"), value = exact(account.storagePrice))
            SpectraValueRow(label = AppLocalization.string("Locked Stake"), value = exact(account.locked), isHidden = hidesBalances)
            SpectraValueRow(label = AppLocalization.string("Kept for Storage"), value = exact(account.storageReserve))
        }
        is NetworkAccount.Cardano -> {
            if (!account.registered) {
                AccountNotice(
                    AppLocalization.string(
                        "The stake key is not registered: this account delegates to no pool and earns no rewards.",
                    ),
                )
            }
            ToolSection(
                footer = AppLocalization.string(
                    "Every address of this account shares one stake key. Delegating it to a pool earns rewards, which build up at the stake address and are not part of the balance until withdrawn.",
                ),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
                    Text(AppLocalization.string("Stake Address"), style = MaterialTheme.typography.bodyMedium)
                    SelectionContainer {
                        Text(
                            account.stakeAddress,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = secondaryText,
                        )
                    }
                }
                SpectraValueRow(label = AppLocalization.string("Rewards"), value = exact(account.rewards), isHidden = hidesBalances)
                IdentifierValueRow(AppLocalization.string("Pool"), account.delegatedPool ?: AppLocalization.string("None"))
                IdentifierValueRow(AppLocalization.string("Voting Delegation"), account.delegatedDrep ?: AppLocalization.string("None"))
            }
        }
    }
}

/** A network resource's points: what is left of the limit. */
@Composable
private fun Resource(available: ULong, limit: ULong) {
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(AppLocalization.string("Available"), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "$available / $limit",
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = secondaryText,
            )
        }
        if (limit > 0uL) {
            LinearProgressIndicator(
                progress = { (minOf(available, limit).toDouble() / limit.toDouble()).toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AccountNotice(text: String) {
    SpectraNoticeCard(text = text, color = SpectraColors.semantic.warning, icon = Icons.Outlined.ErrorOutline)
}

internal val TonAccountState.title: String
    get() = AppLocalization.string(
        when (this) {
            TonAccountState.ACTIVE -> "Active"
            TonAccountState.UNINITIALIZED -> "Not Deployed"
            TonAccountState.FROZEN -> "Frozen"
        },
    )

internal val TonAccountState.explanation: String
    get() = AppLocalization.string(
        when (this) {
            TonAccountState.ACTIVE -> "The wallet contract is deployed and runs every send."
            TonAccountState.UNINITIALIZED ->
                "The wallet contract is not deployed yet. The first send deploys it with the transfer, for a slightly higher fee. Receiving does not need it."
            TonAccountState.FROZEN ->
                "Frozen for unpaid storage fees: it cannot send until the debt is paid and the contract is restored."
        },
    )

/**
 * Closing the wallet's XRP or Stellar account into another existing
 * account: core checks every prerequisite and builds the transaction; this
 * page shows what is given up and asks for the user's confirmation before
 * signing.
 */
@Composable
internal fun CloseAccountView(wallet: WalletState) {
    val app = LocalAppState.current
    var destination by remember { mutableStateOf("") }
    var memoKind by remember { mutableStateOf<PaymentMemoKind?>(null) }
    var memoText by remember { mutableStateOf("") }
    var understood by remember { mutableStateOf(false) }
    val memoKinds = paymentMemoKinds(wallet.chainId)

    WalletOperationPage(
        wallet = wallet,
        title = AppLocalization.string("Close Account"),
        authenticationReason = AppLocalization.format("Authenticate to close the account of %@", wallet.name),
        submittedText = AppLocalization.string("Submitted. The account is closed once it confirms."),
        formIsComplete = destination.isNotBlank(),
        canSign = understood,
        build = {
            val memo = memoKind?.let { if (memoText.isEmpty()) null else PaymentMemo(it, memoText) }
            app.bridge.ready().buildAccountClosing(wallet.id, destination.trim(), memo)
        },
        fields = {
            ToolSection(
                footer = AppLocalization.string(
                    "Closing deletes this account on the network and sends everything it holds, its reserve included, to another account that already exists. It cannot be undone. The wallet stays in Spectra, and funding its address again later opens a new account.",
                ),
            ) {
                AddressEntryRow(
                    title = AppLocalization.string("Destination Address"),
                    text = destination,
                    onTextChange = { destination = it },
                    chain = wallet.chainId,
                    contacts = app.addressBook.entries.filter { it.chainId == wallet.chainId },
                )
                if (memoKinds.isNotEmpty()) {
                    SendPaymentMemoField(
                        kinds = memoKinds,
                        kind = memoKind,
                        onKindChange = { memoKind = it },
                        text = memoText,
                        onTextChange = { memoText = it },
                    )
                }
            }
        },
    ) { artifact ->
        val closing = artifact?.operation as? WalletOperation.CloseAccount
        if (artifact != null && closing != null) {
            ToolSection(
                footer = AppLocalization.string(
                    "Everything the account holds when the transaction executes goes to this account, less the fee. The amount shown is its balance now.",
                ),
            ) {
                IdentifierValueRow(AppLocalization.string("To"), closing.destination)
                artifact.memo?.let { PaymentMemoRow(it) }
                SpectraValueRow(
                    label = AppLocalization.string("Amount"),
                    value = app.amounts.formattedNativeAmount(artifact.amount, wallet.chainId),
                )
                SpectraValueRow(
                    label = AppLocalization.string("Reserve Recovered"),
                    value = app.amounts.formattedNativeAmount(closing.reserve, wallet.chainId),
                )
                if (closing.removedObjects > 0uL) {
                    SpectraValueRow(label = AppLocalization.string("Objects Deleted"), value = closing.removedObjects.toString())
                }
            }
            if (artifact.stage == SendStage.PREPARED) {
                SpectraCard {
                    SpectraSwitchRow(
                        title = AppLocalization.string("I understand the account is deleted and this cannot be undone."),
                        checked = understood,
                        onCheckedChange = { understood = it },
                    )
                }
            }
        }
    }
}
