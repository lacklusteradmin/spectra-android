package dev.spectra.wallets

import dev.spectra.models.AppLocalization
import uniffi.spectra_core.WalletSetupMethod

/**
 * Ordered wallet-setup pages. Navigation and the step indicator derive from
 * this list.
 */
data class SetupFlow(val pages: List<WalletSetupPage>) {
    /** Linear index of [page] within this flow, or `null` for a page this flow does not visit. */
    fun index(page: WalletSetupPage): Int? = pages.indexOf(page).takeIf { it >= 0 }

    /**
     * Page to advance to from [current], or `null` if already at the end
     * (the primary action then submits instead of routing).
     */
    fun next(current: WalletSetupPage): WalletSetupPage? {
        val i = index(current) ?: return null
        return pages.getOrNull(i + 1)
    }

    companion object {
        /**
         * The pages for adding a wallet by [method]. The network was chosen
         * before the form opened, so no flow asks for it.
         */
        fun forMethod(method: WalletSetupMethod): SetupFlow = when (method) {
            // A new phrase is checked straight after it is written down, while
            // the paper is still in hand; the name and password come last.
            WalletSetupMethod.CREATE_PHRASE -> SetupFlow(
                listOf(WalletSetupPage.SEED_PHRASE, WalletSetupPage.BACKUP_VERIFICATION, WalletSetupPage.WALLET_NAME),
            )
            WalletSetupMethod.IMPORT_PHRASE, WalletSetupMethod.IMPORT_PRIVATE_KEY ->
                SetupFlow(listOf(WalletSetupPage.SEED_PHRASE, WalletSetupPage.WALLET_NAME))
            WalletSetupMethod.WATCH_ADDRESSES, WalletSetupMethod.WATCH_ACCOUNT_XPUB,
            WalletSetupMethod.WATCH_VIEW_KEY, WalletSetupMethod.WATCH_MULTISIG,
            -> SetupFlow(listOf(WalletSetupPage.WATCH_ADDRESSES, WalletSetupPage.WALLET_NAME))
        }

        /** Edit existing wallet: a single page, the name field. */
        val editWallet = SetupFlow(listOf(WalletSetupPage.WALLET_NAME))
    }
}

/** A page of the wallet-setup flow; [SetupFlow] lists them in order for each import method. */
enum class WalletSetupPage {
    WATCH_ADDRESSES,
    SEED_PHRASE,
    BACKUP_VERIFICATION,

    /** The name, and the optional password a wallet with a secret may take. */
    WALLET_NAME,
    ;

    /**
     * Page wording. Exhaustive so every new page must supply a title.
     *
     * The page alone does not decide what it is called — "seed phrase" reads
     * as *record* one when creating a wallet and *enter* one when importing —
     * so the copy takes the mode alongside the page.
     */
    fun copy(mode: WalletDraftMode): WalletSetupPageCopy = when (this) {
        WALLET_NAME -> when {
            mode == WalletDraftMode.Edit -> WalletSetupPageCopy(
                AppLocalization.string("import_flow.editWalletTitle"),
                AppLocalization.string("import_flow.editWalletSubtitle"),
            )
            mode.takesWalletPassword -> WalletSetupPageCopy(
                AppLocalization.string("import_flow.name_your_wallet"),
                AppLocalization.string("import_flow.name_and_password_hint"),
            )
            else -> WalletSetupPageCopy(
                AppLocalization.string("import_flow.name_your_wallet"),
                AppLocalization.string("import_flow.wallet_name_hint"),
            )
        }
        BACKUP_VERIFICATION -> WalletSetupPageCopy(
            AppLocalization.string("import_flow.backupVerificationTitle"),
            AppLocalization.string("import_flow.backupVerificationSubtitle"),
        )
        WATCH_ADDRESSES -> when (mode) {
            WalletDraftMode.Setup(WalletSetupMethod.WATCH_ACCOUNT_XPUB) -> WalletSetupPageCopy(
                AppLocalization.string("Watch Account"),
                AppLocalization.string("Enter the account's extended public key."),
            )
            WalletDraftMode.Setup(WalletSetupMethod.WATCH_MULTISIG) -> WalletSetupPageCopy(
                AppLocalization.string("Watch Multisig"),
                AppLocalization.string("Enter the account's descriptor: wsh(sortedmulti(…)) with each key's origin."),
            )
            WalletDraftMode.Setup(WalletSetupMethod.WATCH_VIEW_KEY) -> WalletSetupPageCopy(
                AppLocalization.string("Watch with View Key"),
                AppLocalization.string("Enter the wallet's primary address and its private view key."),
            )
            else -> WalletSetupPageCopy(
                AppLocalization.string("import_flow.watchAddressesTitle"),
                AppLocalization.string("import_flow.watchAddressesSubtitle"),
            )
        }
        SEED_PHRASE -> if (mode == WalletDraftMode.Setup(WalletSetupMethod.IMPORT_PRIVATE_KEY)) {
            WalletSetupPageCopy(
                AppLocalization.string("import_flow.enterPrivateKeyTitle"),
                AppLocalization.string("import_flow.privateKeySubtitle"),
            )
        } else {
            val isCreating = mode == WalletDraftMode.Setup(WalletSetupMethod.CREATE_PHRASE)
            WalletSetupPageCopy(
                AppLocalization.string(
                    if (isCreating) "import_flow.recordSeedPhraseTitle" else "import_flow.enterSeedPhraseTitle",
                ),
                AppLocalization.string(
                    if (isCreating) "import_flow.saveRecoveryPhraseSubtitle" else "import_flow.enterRecoveryPhraseSubtitle",
                ),
            )
        }
    }
}

/** What a page calls itself: the heading and the line under it. */
data class WalletSetupPageCopy(val title: String, val subtitle: String)

/**
 * A wallet added with a phrase or a key may seal it under a password; a
 * watched one has nothing to seal, and a rename changes no secret.
 */
val WalletDraftMode.takesWalletPassword: Boolean
    get() = when (this) {
        is WalletDraftMode.Setup -> when (method) {
            WalletSetupMethod.CREATE_PHRASE, WalletSetupMethod.IMPORT_PHRASE, WalletSetupMethod.IMPORT_PRIVATE_KEY -> true
            else -> false
        }
        WalletDraftMode.Edit -> false
    }
