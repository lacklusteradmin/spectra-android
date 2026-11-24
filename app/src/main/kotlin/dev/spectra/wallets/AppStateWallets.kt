package dev.spectra.wallets

import dev.spectra.app.AppState
import dev.spectra.app.DeviceAuthenticationAction
import dev.spectra.app.authenticate
import dev.spectra.models.AppLocalization
import dev.spectra.models.DateStyle
import dev.spectra.models.DisplayedError
import dev.spectra.models.TimeStyle
import dev.spectra.models.formatUnixDate
import dev.spectra.models.isPrivateKey
import dev.spectra.models.isWatchOnly
import dev.spectra.models.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.spectra_core.SeedPhraseReveal
import uniffi.spectra_core.WalletState

/**
 * Reveal a wallet's seed phrase after device authentication. The password
 * goes to core as typed; core applies its password rule and says why a
 * phrase was not revealed.
 */
suspend fun AppState.revealSeedPhrase(wallet: WalletState, password: String? = null): String {
    authenticate(
        DeviceAuthenticationAction.SECRET_MATERIAL,
        AppLocalization.format("Authenticate to view seed phrase for %@", wallet.name),
    )?.let { throw DisplayedError(it) }
    val supplied = password?.ifEmpty { null }
    val service = bridge.service()
    val walletId = wallet.id
    // A blocking call through the Keystore and the password check: off the
    // main thread, so the UI does not stall on it.
    val reveal = withContext(Dispatchers.IO) { service.revealSeedPhrase(walletId, supplied) }
    return when (reveal) {
        is SeedPhraseReveal.Phrase -> reveal.phrase
        SeedPhraseReveal.NotStored -> throw DisplayedError(AppLocalization.string("No seed phrase is stored for this wallet."))
        SeedPhraseReveal.PasswordRequired ->
            throw DisplayedError(AppLocalization.string("Enter the wallet password to view this seed phrase."))
        SeedPhraseReveal.IncorrectPassword -> throw DisplayedError(AppLocalization.string("The wallet password is incorrect."))
        SeedPhraseReveal.PasswordNotRequired -> throw DisplayedError(AppLocalization.string("This wallet has no password."))
    }
}

/**
 * The wallet's derivation path, named by its profile and account when core
 * reads it as one. A watched or private-key wallet derives nothing.
 */
internal val WalletState.derivationPathsText: String?
    get() {
        if (signing.isWatchOnly || signing.isPrivateKey) return null
        val path = derivationPath?.takeIf { it.isNotEmpty() } ?: return null
        val choice = derivationProfile ?: return path
        return AppLocalization.format(
            "wallet.detail.chainPath",
            AppLocalization.format("derivation.profile_account_format", choice.profile.title, choice.account.toLong()),
            path,
        )
    }

/** When the wallet's earliest stored transaction happened, or that it has none yet. */
internal fun AppState.firstActivityDateText(walletId: String): String =
    firstActivityByWalletId[walletId]?.let { formatUnixDate(it, DateStyle.ABBREVIATED, TimeStyle.SHORTENED) }
        ?: AppLocalization.string("No activity yet")
