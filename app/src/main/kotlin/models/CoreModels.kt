package dev.spectra.models

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.spectra.components.SpectraColors
import uniffi.spectra_core.AddressBookEntry
import uniffi.spectra_core.AssetHolding
import uniffi.spectra_core.Chain
import uniffi.spectra_core.DerivationProfile
import uniffi.spectra_core.DiagnosticLogLevel
import uniffi.spectra_core.FundsFinderCandidate
import uniffi.spectra_core.HistoryQueryFilter
import uniffi.spectra_core.HistorySource
import uniffi.spectra_core.PriceAlertCondition
import uniffi.spectra_core.PriceAlertRule
import uniffi.spectra_core.ResetScope
import uniffi.spectra_core.TonWalletVersion
import uniffi.spectra_core.TransactionDirection
import uniffi.spectra_core.TransactionFailure
import uniffi.spectra_core.TransactionKind
import uniffi.spectra_core.TransactionRecord
import uniffi.spectra_core.TransactionStatus
import uniffi.spectra_core.WalletSigning
import uniffi.spectra_core.WalletState
import uniffi.spectra_core.resetScopes
import uniffi.spectra_core.tonWalletVersions
import uniffi.spectra_core.transactionExplorerLink

// Display helpers on core's records. Core owns the records and every rule
// about them; nothing here decides anything a person could act on.

// ── Holdings ─────────────────────────────────────────────────────────────

/** A holding's colour: its catalog token's, or its chain's for a native asset. */
val AssetHolding.color: Color get() = AssetPresentationCatalog.color(id)

/** For text a person reads; identity is `chainId`. */
val AssetHolding.chainName: String get() = chainId.displayName

/** The chain's own asset — `ETH` on Arbitrum, not `ARB` — by deployment identity. */
val AssetHolding.isNativeCoin: Boolean get() = chainId.entry?.nativeDeploymentId == id

/** Whether anything is held. Core stores amounts canonically, so zero is `"0"`. */
val AssetHolding.hasBalance: Boolean get() = amount != "0"

val AssetHolding.artworkName: String get() = AssetPresentationCatalog.artwork(id)

/** The holding's protocol, or that it is the network's own coin. */
val AssetHolding.standardLabel: String get() = tokenStandard?.displayName ?: AppLocalization.string("Native")

// ── Wallets ──────────────────────────────────────────────────────────────

val WalletState.chainName: String get() = chainId.displayName

/** The mainnet whose family this wallet belongs to. */
val WalletState.family: Chain get() = chainId.mainnetCounterpart

val WalletState.familyName: String get() = family.displayName

/** Whether the user hid [holding] from this wallet's total. */
fun WalletState.hides(holding: AssetHolding): Boolean = holding.id in hiddenHoldings

/** The holdings with a balance the wallet shows and counts. */
val WalletState.shownHoldings: List<AssetHolding> get() = holdings.filter { it.hasBalance && !hides(it) }

/** Spectra holds no key for the wallet's address. */
val WalletSigning.isWatchOnly: Boolean get() = this == WalletSigning.WatchOnly

/** Spectra holds one account's private key, and derives nothing from it. */
val WalletSigning.isPrivateKey: Boolean get() = this is WalletSigning.PrivateKey

/** Spectra holds the wallet's recovery phrase. */
val WalletSigning.hasSeedPhrase: Boolean get() = this is WalletSigning.SeedPhrase

/** Signing, revealing or scanning needs the wallet's password. */
val WalletSigning.requiresPassword: Boolean
    get() = when (this) {
        WalletSigning.WatchOnly -> false
        is WalletSigning.SeedPhrase -> passwordProtected
        is WalletSigning.PrivateKey -> passwordProtected
    }

// ── Derivation ───────────────────────────────────────────────────────────

/** The profile's name. A Bitcoin-family profile is its script type. */
val DerivationProfile.title: String
    get() = AppLocalization.string(
        when (this) {
            DerivationProfile.STANDARD -> "Standard"
            DerivationProfile.LEGACY -> "Legacy"
            DerivationProfile.NESTED_SEG_WIT -> "Nested SegWit"
            DerivationProfile.NATIVE_SEG_WIT -> "Native SegWit"
            DerivationProfile.TAPROOT -> "Taproot"
            DerivationProfile.ECDSA -> "ECDSA"
            DerivationProfile.EVM -> "EVM"
        },
    )

/** Every TON contract version, in core's order, the default first. */
val tonWalletVersionsInOrder: List<TonWalletVersion> by lazy { tonWalletVersions() }

/** The version's name as TON wallets print it. */
val TonWalletVersion.title: String
    get() = AppLocalization.string(
        when (this) {
            TonWalletVersion.W5 -> "W5"
            TonWalletVersion.V4R2 -> "v4R2"
        },
    )

val FundsFinderCandidate.chainName: String get() = chainId.displayName

/**
 * The profile and account the address is on, the wallet version on TON, or
 * nothing for a network that derives one account.
 */
val FundsFinderCandidate.profileTitle: String?
    get() {
        tonWalletVersion?.let { return it.title }
        return profile?.let { AppLocalization.format("derivation.profile_account_format", it.title, account.toLong()) }
    }

// ── Transactions ─────────────────────────────────────────────────────────

val TransactionStatus.localizedTitle: String
    get() = AppLocalization.string(
        when (this) {
            TransactionStatus.PENDING -> "Pending"
            TransactionStatus.CONFIRMED -> "Confirmed"
            TransactionStatus.FAILED -> "Failed"
        },
    )

/** A status's colour: pending is a warning, confirmed success, failed a failure. */
val TransactionStatus.color: Color
    @Composable get() = when (this) {
        TransactionStatus.PENDING -> SpectraColors.semantic.warning
        TransactionStatus.CONFIRMED -> SpectraColors.semantic.success
        TransactionStatus.FAILED -> SpectraColors.semantic.failure
    }

/** Picker order for core's history filter. */
val historyFilterOrder = listOf(
    HistoryQueryFilter.ALL,
    HistoryQueryFilter.SEND,
    HistoryQueryFilter.RECEIVE,
    HistoryQueryFilter.PENDING,
)

val HistoryQueryFilter.localizedTitle: String
    get() = AppLocalization.string(
        when (this) {
            HistoryQueryFilter.ALL -> "All"
            HistoryQueryFilter.SEND -> "Sends"
            HistoryQueryFilter.RECEIVE -> "Receives"
            HistoryQueryFilter.PENDING -> "Pending"
        },
    )

enum class HistorySortOrder(private val key: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    ;

    val localizedTitle: String get() = AppLocalization.string(key)
}

/** History with no deployment identity draws its letter. */
val TransactionRecord.artworkName: String get() = AssetPresentationCatalog.artwork(deploymentId)

val TransactionRecord.chainName: String get() = chainId.displayName

/** When it was recorded, in milliseconds. Core stores Unix seconds. */
val TransactionRecord.createdAtMillis: Long get() = (createdAtUnix * 1000).toLong()

val TransactionRecord.titleText: String
    get() = when (kind) {
        TransactionKind.SEND -> AppLocalization.format("Sent %@", symbol)
        TransactionKind.RECEIVE -> AppLocalization.format("Received %@", symbol)
        TransactionKind.STAKE -> AppLocalization.format("Stake %@", symbol)
        TransactionKind.UNSTAKE -> AppLocalization.format("Unstake %@", symbol)
        TransactionKind.WITHDRAW -> AppLocalization.format("Withdraw %@", symbol)
        TransactionKind.CLAIM_REWARDS -> AppLocalization.format("Claim %@ Rewards", symbol)
        TransactionKind.REVOKE_APPROVAL -> AppLocalization.format("Revoke %@ Approval", symbol)
        TransactionKind.DELETE_ACCESS_KEY -> AppLocalization.string("Delete Access Key")
        TransactionKind.MERGE_COINS -> AppLocalization.format("Merge %@ Coins", symbol)
        TransactionKind.CLOSE_TOKEN_ACCOUNTS -> AppLocalization.string("Close Token Accounts")
        TransactionKind.REFUND_TOKEN_STORAGE -> AppLocalization.string("Refund Token Storage")
        TransactionKind.TRUST_ASSET -> AppLocalization.string("Trust Asset")
        TransactionKind.REMOVE_TRUST_LINE -> AppLocalization.string("Remove Trust Line")
        TransactionKind.SHIELD -> AppLocalization.format("Shield %@", symbol)
    }

/** The network and wallet, prefixing the asset name for token transfers. */
val TransactionRecord.subtitleText: String
    get() {
        val asset = if (assetDisplayName.equals(chainName, ignoreCase = true)) {
            assetDisplayName
        } else {
            AppLocalization.format("common.assetOnChainFormat", assetDisplayName, chainName)
        }
        return AppLocalization.format("common.transactionSubtitleFormat", asset, walletName)
    }

val TransactionRecord.amountSign: String
    get() = when (direction) {
        TransactionDirection.INCOMING -> "+"
        TransactionDirection.OUTGOING -> "-"
        TransactionDirection.NEUTRAL -> ""
    }

val TransactionRecord.amountColor: Color
    @Composable get() = when (direction) {
        TransactionDirection.INCOMING -> SpectraColors.semantic.success
        TransactionDirection.OUTGOING -> SpectraColors.semantic.failure
        TransactionDirection.NEUTRAL -> SpectraColors.semantic.neutral
    }

val TransactionRecord.receiptBlockNumberText: String? get() = receiptBlockNumber?.toString()

val TransactionRecord.storedConfirmationCountText: String?
    get() = confirmationCount?.let { AppLocalization.formatCount("%lld confirmations", it, it) }

val TransactionRecord.storedUsedChangeOutputText: String?
    get() = usedChangeOutput?.let { AppLocalization.string(if (it) "Yes" else "No") }

/** Core stores a time it does not know as a date before any chain existed. */
val TransactionRecord.hasKnownDate: Boolean get() = createdAtUnix > 0

/** When it happened, in the app's language: the date and the second. */
val TransactionRecord.fullTimestampText: String
    get() = if (!hasKnownDate) {
        AppLocalization.string("Unknown date")
    } else {
        formatAppDate(createdAtMillis, DateStyle.ABBREVIATED, TimeStyle.STANDARD)
    }

/** When it happened, as a list row shows it: the time alone under a day header. */
fun TransactionRecord.timestampText(showsDate: Boolean): String =
    if (!hasKnownDate) {
        AppLocalization.string("Unknown date")
    } else {
        formatAppDate(createdAtMillis, if (showsDate) DateStyle.ABBREVIATED else DateStyle.OMITTED, TimeStyle.SHORTENED)
    }

/** The button that opens this transaction on its network's explorer. */
val TransactionRecord.explorerLink: Pair<String, String>?
    get() {
        val hash = transactionHash ?: return null
        val link = transactionExplorerLink(chainId, hash) ?: return null
        return AppLocalization.format("Open In %@", link.name) to link.url
    }

/** The sentence a finished send reports, in a notification; `null` while pending. */
fun TransactionRecord.sendOutcomeDetail(status: TransactionStatus): String? = when (status) {
    TransactionStatus.PENDING -> null
    TransactionStatus.CONFIRMED ->
        AppLocalization.format("Your %@ send from %@ is now confirmed on %@.", symbol, walletName, chainName)
    TransactionStatus.FAILED -> localizedFailureReason
        ?: AppLocalization.format("Your %@ send from %@ failed on %@.", symbol, walletName, chainName)
}

/** The failure reason, localized. Core stores the reason; the words are made here. */
val TransactionRecord.localizedFailureReason: String?
    get() = when (val reason = failureReason) {
        null -> null
        TransactionFailure.ExecutionFailed ->
            AppLocalization.string("The transaction failed during on-chain execution.")
        TransactionFailure.SubmissionOutcomeUnknown ->
            AppLocalization.string("Submission outcome unknown; check network status before sending again.")
        TransactionFailure.RebroadcastOutcomeUnknown ->
            AppLocalization.string("Rebroadcast outcome unknown; check network status before retrying.")
        is TransactionFailure.Reported -> reason.message
    }

/** An indexer's proper name, when one was the record's history source. */
val TransactionRecord.historySourceName: String?
    get() = when (val source = historySource) {
        is HistorySource.Indexer -> source.name
        HistorySource.Internal, null -> null
    }

// ── Alerts and contacts ──────────────────────────────────────────────────

val priceAlertConditions = listOf(PriceAlertCondition.ABOVE, PriceAlertCondition.BELOW)

val PriceAlertCondition.displayName: String
    get() = AppLocalization.string(
        when (this) {
            PriceAlertCondition.ABOVE -> "Above"
            PriceAlertCondition.BELOW -> "Below"
        },
    )

val PriceAlertRule.chainName: String get() = chainId.displayName

val PriceAlertRule.titleText: String
    get() = AppLocalization.format("common.assetOnChainFormat", assetDisplayName, chainName)

val PriceAlertRule.statusText: String
    get() = when {
        !isEnabled -> AppLocalization.string("Paused")
        hasTriggered -> AppLocalization.string("Triggered")
        else -> AppLocalization.string("Watching")
    }

val PriceAlertRule.statusColor: Color
    @Composable get() = when {
        !isEnabled -> SpectraColors.semantic.neutral
        hasTriggered -> SpectraColors.semantic.success
        else -> SpectraColors.semantic.warning
    }

val AddressBookEntry.chainName: String get() = chainId.displayName

val AddressBookEntry.subtitleText: String
    get() = if (note.isEmpty()) chainName else AppLocalization.format("common.addressBookSubtitleFormat", chainName, note)

// ── Diagnostics and reset ────────────────────────────────────────────────

val diagnosticLogLevels = listOf(
    DiagnosticLogLevel.DEBUG,
    DiagnosticLogLevel.INFO,
    DiagnosticLogLevel.WARNING,
    DiagnosticLogLevel.ERROR,
)

val DiagnosticLogLevel.displayName: String
    get() = AppLocalization.string(
        when (this) {
            DiagnosticLogLevel.DEBUG -> "Debug"
            DiagnosticLogLevel.INFO -> "Info"
            DiagnosticLogLevel.WARNING -> "Warning"
            DiagnosticLogLevel.ERROR -> "Error"
        },
    )

/** The export's tag. Not localized: the export is read by whoever debugs it. */
val DiagnosticLogLevel.exportTag: String get() = name

/** Every reset scope, in core's order. */
val resetScopesInOrder: List<ResetScope> by lazy { resetScopes() }

val ResetScope.title: String
    get() = AppLocalization.string(
        when (this) {
            ResetScope.WALLETS_AND_SECRETS -> "Wallets & Secrets"
            ResetScope.HISTORY_AND_CACHE -> "History & Cache"
            ResetScope.ALERTS_AND_CONTACTS -> "Alerts & Contacts"
            ResetScope.SETTINGS_AND_ENDPOINTS -> "Settings & Endpoints"
            ResetScope.DASHBOARD_CUSTOMIZATION -> "Dashboard Customization"
        },
    )

val ResetScope.detail: String
    get() = AppLocalization.string(
        when (this) {
            ResetScope.WALLETS_AND_SECRETS ->
                "Imported wallets, seed phrases, watched addresses, and local wallet access data."
            ResetScope.HISTORY_AND_CACHE ->
                "Transactions, history database, diagnostics snapshots, and cached chain state."
            ResetScope.ALERTS_AND_CONTACTS ->
                "Price alerts, notification rules, and saved address book recipients."
            ResetScope.SETTINGS_AND_ENDPOINTS ->
                "Known tokens, pricing and RPC settings, preferences, and icon customizations."
            ResetScope.DASHBOARD_CUSTOMIZATION ->
                "Pinned assets and other home page customization choices stored on this device."
        },
    )
