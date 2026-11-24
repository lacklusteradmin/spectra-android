package dev.spectra.wallets

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.models.AppLocalization
import uniffi.spectra_core.Chain
import uniffi.spectra_core.DerivationProfile
import uniffi.spectra_core.FundsFinderCandidate
import uniffi.spectra_core.SeedPhraseLength
import uniffi.spectra_core.TonWalletVersion
import uniffi.spectra_core.WalletDerivationOverrides
import uniffi.spectra_core.WalletImportCommit
import uniffi.spectra_core.WalletImportKind
import uniffi.spectra_core.WalletImportRequest
import uniffi.spectra_core.WalletPasswordRejection
import uniffi.spectra_core.WalletSetupDescriptor
import uniffi.spectra_core.WalletSetupField
import uniffi.spectra_core.WalletSetupMethod
import uniffi.spectra_core.WalletSetupOption
import uniffi.spectra_core.WalletState
import uniffi.spectra_core.createdPhraseLengths
import uniffi.spectra_core.derivationProfilePath
import uniffi.spectra_core.generateSeedPhrase
import uniffi.spectra_core.isValidPrivateKey
import uniffi.spectra_core.validateWalletPassword
import uniffi.spectra_core.walletSetupDescriptor
import java.security.SecureRandom

/**
 * What a draft is for: adding a wallet one way on one network, or renaming
 * one. The network and the method are chosen before the form opens.
 */
sealed interface WalletDraftMode {
    data class Setup(val method: WalletSetupMethod) : WalletDraftMode

    data object Edit : WalletDraftMode
}

/**
 * The add-wallet form's draft.
 *
 * Mutation contract:
 *
 *  * **Derived state is computed, not stored.** [chain] and the verdicts are
 *    read from the fields they depend on, so no field needs a hook to keep
 *    them current and any field may be bound directly.
 *  * **Fields with a custom setter reshape other fields** — a created
 *    phrase's length regenerates it. The phrase grid is [seedEntry]'s, which
 *    follows core's verdict as words land.
 */
class WalletImportDraft {
    var mode by mutableStateOf<WalletDraftMode>(WalletDraftMode.Setup(WalletSetupMethod.IMPORT_PHRASE))

    /** The network the wallet is added on, chosen before the form opens. */
    var chain by mutableStateOf<Chain?>(null)

    /**
     * The watched wallet this form gives its keys to, when it was opened from
     * that wallet's page. Core refuses a secret that does not hold its address
     * rather than adding a wallet.
     */
    var upgradeWalletId by mutableStateOf<String?>(null)

    val isEditingWallet: Boolean get() = mode == WalletDraftMode.Edit

    val method: WalletSetupMethod? get() = (mode as? WalletDraftMode.Setup)?.method

    var walletName by mutableStateOf("")

    /** The phrase grid: typed when importing, generated when creating. */
    val seedEntry = SeedPhraseEntry()

    /** The phrase as core reads it. */
    val seedPhrase: String get() = seedEntry.phrase

    /** Typed in [dev.spectra.components.WalletPasswordField]s; never saved with a page. */
    val walletPassword = TextFieldState()
    val walletPasswordConfirmation = TextFieldState()
    var privateKeyInput by mutableStateOf("")

    /**
     * The derivation profile a phrase wallet uses: one of the network's, its
     * first by default. `null` on a network that derives without a path.
     */
    var derivationProfile by mutableStateOf<DerivationProfile?>(null)

    /** The account index on the profile. */
    var derivationAccount by mutableStateOf(0u)

    /** A path typed under Advanced. When set it replaces the profile's. */
    var customDerivationPath by mutableStateOf("")

    // Power-user derivation overrides (the Advanced page). Each field is the
    // exact text typed; core reads a blank one as no override.
    var overridePassphrase by mutableStateOf("")

    /**
     * A new wallet's passphrase, typed again. Nothing else can catch a typo
     * in it: backup verification checks the words, and a mistyped passphrase
     * derives a different wallet that can never be restored.
     */
    var overridePassphraseConfirmation by mutableStateOf("")
    var overrideHmacKey by mutableStateOf("")

    private var selectedSeedPhraseWordCountState by mutableStateOf(12)

    /** The length a created phrase is generated at: one of [createdLengths]. */
    var selectedSeedPhraseWordCount: Int
        get() = selectedSeedPhraseWordCountState
        set(value) {
            val old = selectedSeedPhraseWordCountState
            selectedSeedPhraseWordCountState = value
            if (value != old) regenerateSeedPhrase()
        }

    /**
     * A restored Monero wallet's restore height as typed; blank lets core read
     * a Polyseed's birthday or scan a 25-word seed from the start.
     */
    var restoreHeightInput by mutableStateOf("")

    /**
     * The typed restore height, or `null` when blank. Not a number is not a
     * height, and is refused by [isSecretComplete].
     */
    val restoreHeight: ULong?
        get() {
            val trimmed = restoreHeightInput.trim()
            if (trimmed.isEmpty() || !trimmed.all { it in '0'..'9' }) return null
            return trimmed.toULongOrNull()
        }

    val isRestoreHeightValid: Boolean
        get() = restoreHeightInput.trim().isEmpty() || restoreHeight != null

    /** The last network's descriptor, so a render asks core once per network. */
    private var descriptorCache: WalletSetupDescriptor? = null

    /** This method's entry in the network's setup descriptor. */
    private val setupOption: WalletSetupOption?
        get() {
            val chain = chain ?: return null
            val method = method ?: return null
            val descriptor = descriptorCache?.takeIf { it.chain == chain }
                ?: walletSetupDescriptor(chain).also { descriptorCache = it }
            return descriptor.options.firstOrNull { it.method == method }
        }

    /** Whether this method on this network asks for a restore height. */
    val asksRestoreHeight: Boolean get() = setupOption?.fields?.contains(WalletSetupField.RESTORE_HEIGHT) ?: false

    /** A named account the key controls, as typed; blank keeps the key's implicit account. */
    var namedAccountInput by mutableStateOf("")

    /** Whether this method on this network takes a named account. */
    val asksNamedAccount: Boolean get() = setupOption?.fields?.contains(WalletSetupField.NAMED_ACCOUNT) ?: false

    /** The wallet contract a TON key's account is under. */
    var tonWalletVersion by mutableStateOf(TonWalletVersion.W5)

    /** Whether this method on this network asks which wallet version to hold. */
    val asksTonWalletVersion: Boolean
        get() = setupOption?.fields?.contains(WalletSetupField.TON_WALLET_VERSION) ?: false

    /**
     * The profiles this method offers on the network, default first; empty
     * where the network derives without a path.
     */
    val derivationProfiles: List<DerivationProfile> get() = setupOption?.profiles ?: emptyList()

    /** The profile's path at the chosen account, as core renders it. */
    val profileDerivationPath: String?
        get() {
            val chain = chain ?: return null
            val profile = derivationProfile ?: return null
            return try {
                derivationProfilePath(chain, profile, derivationAccount)
            } catch (_: Exception) {
                null
            }
        }

    /**
     * A Substrate path of hard (`//`) and soft (`/`) junctions as typed;
     * blank derives the phrase's root key.
     */
    var junctionPathInput by mutableStateOf("")

    /** Whether this method on this network takes a junction path. */
    val asksJunctionPath: Boolean get() = setupOption?.fields?.contains(WalletSetupField.JUNCTION_PATH) ?: false

    /**
     * The path the import derives along: on a Substrate network the typed
     * junctions, else the custom one when typed, else the profile's. Core
     * refuses one that does not parse.
     */
    val derivationPath: String?
        get() {
            if (asksJunctionPath) return junctionPathInput.trim().ifEmpty { null }
            val custom = customDerivationPath.trim()
            return custom.ifEmpty { null } ?: profileDerivationPath
        }

    /** The watched addresses, one per line, on the import's chain. */
    var watchOnlyInput by mutableStateOf("")

    /**
     * Not an address: an account xpub stands in for the whole account and
     * imports one wallet rather than one per line.
     */
    var accountXpubInput by mutableStateOf("")

    /** A Monero wallet's private view key, watched with its primary address typed in [watchOnlyInput]. */
    var viewKeyInput by mutableStateOf("")

    /** A multisig account's output descriptor. */
    var descriptorInput by mutableStateOf("")

    var backupVerificationWordIndices by mutableStateOf<List<Int>>(emptyList())
    var backupVerificationEntries by mutableStateOf<List<String>>(emptyList())

    val isCreateMode: Boolean get() = method == WalletSetupMethod.CREATE_PHRASE
    val isPrivateKeyImportMode: Boolean get() = method == WalletSetupMethod.IMPORT_PRIVATE_KEY

    /** The pages this draft's form walks through. */
    val setupFlow: SetupFlow
        get() = when (val mode = mode) {
            WalletDraftMode.Edit -> SetupFlow.editWallet
            is WalletDraftMode.Setup -> SetupFlow.forMethod(mode.method)
        }

    /** The phrase as words, as core reads them. */
    val seedPhraseWords: List<String> get() = seedEntry.verdict.words

    /**
     * The password as typed, or `null` for an empty field: the one way to say
     * "no password". Core owns what counts as a password — it ignores
     * surrounding whitespace and refuses a blank one rather than storing the
     * wallet unsealed — so this side does not reshape it.
     */
    val walletPasswordInput: String? get() = walletPassword.text.toString().ifEmpty { null }

    val walletPasswordValidationError: String?
        get() = validateWalletPassword(walletPassword.text.toString(), walletPasswordConfirmation.text.toString())?.localizedMessage

    /**
     * The overrides exactly as typed. Core reads a blank one as none and
     * refuses one the chain does not take.
     */
    val resolvedDerivationOverrides: WalletDerivationOverrides
        get() = WalletDerivationOverrides(passphrase = overridePassphrase, hmacKey = overrideHmacKey)

    /** The watched addresses, one per non-blank line. */
    val watchOnlyEntries: List<String>
        get() = watchOnlyInput.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * How core reads this import. Creating and restoring a phrase are one
     * import: core does not need to know the phrase was just generated.
     */
    val importKind: WalletImportKind
        get() = when (method) {
            WalletSetupMethod.IMPORT_PRIVATE_KEY -> WalletImportKind.PrivateKey
            WalletSetupMethod.WATCH_ADDRESSES -> WalletImportKind.WatchAddresses(watchOnlyEntries)
            WalletSetupMethod.WATCH_ACCOUNT_XPUB -> WalletImportKind.WatchAccountXpub(accountXpubInput.trim())
            WalletSetupMethod.WATCH_VIEW_KEY -> WalletImportKind.WatchViewKey(watchOnlyInput.trim(), viewKeyInput.trim())
            WalletSetupMethod.WATCH_MULTISIG -> WalletImportKind.WatchMultisig(descriptorInput.trim())
            WalletSetupMethod.CREATE_PHRASE, WalletSetupMethod.IMPORT_PHRASE, null -> WalletImportKind.Phrase
        }

    /**
     * The commit core imports from, built from the form as it stands. The one
     * place the form becomes a request, so whatever reads it before the import
     * sees what the import will.
     */
    fun importCommit(name: String): WalletImportCommit? {
        val chain = chain ?: return null
        val kind = importKind
        // A created wallet's scan starts now, which core sets; a restored one
        // from the height typed, if any.
        val restoreHeight = if (isCreateMode) null else restoreHeight
        return WalletImportCommit(
            password = walletPasswordInput,
            request = WalletImportRequest(walletName = name, chain = chain, kind = kind),
            derivationPath = if (kind == WalletImportKind.Phrase) derivationPath else null,
            derivationOverrides = resolvedDerivationOverrides,
            seedPhrase = if (kind == WalletImportKind.Phrase) seedPhrase else null,
            privateKey = if (kind == WalletImportKind.PrivateKey) privateKeyInput else null,
            restoreHeight = restoreHeight,
            namedAccount = if (asksNamedAccount && namedAccountInput.trim().isNotEmpty()) namedAccountInput else null,
            tonWalletVersion = if (asksTonWalletVersion) tonWalletVersion else null,
            upgradeWalletId = upgradeWalletId,
            created = isCreateMode,
        )
    }

    /**
     * The commit an address preview reads: the form's, without the name and
     * password, which change no address. `null` until there is enough to
     * derive from — a complete secret, or a line or key to watch.
     */
    val previewCommit: WalletImportCommit?
        get() {
            val ready = when (val kind = importKind) {
                is WalletImportKind.WatchAddresses -> kind.addresses.isNotEmpty()
                is WalletImportKind.WatchAccountXpub -> kind.xpub.isNotEmpty()
                is WalletImportKind.WatchViewKey ->
                    kind.address.isNotEmpty() && kind.viewKey.isNotEmpty() && isRestoreHeightValid
                is WalletImportKind.WatchMultisig -> kind.policy.isNotEmpty()
                WalletImportKind.Phrase, WalletImportKind.PrivateKey -> isSecretComplete
            }
            if (!ready) return null
            return importCommit(name = "")?.copy(password = null)
        }

    /**
     * Form completeness is view state. Domain validation remains mandatory in
     * core's import and rename operations even when a client skips this check.
     */
    val canImportWallet: Boolean
        get() {
            if (isEditingWallet) return walletName.trim().isNotEmpty()
            if (chain == null) return false
            when (val kind = importKind) {
                is WalletImportKind.WatchAddresses -> return kind.addresses.isNotEmpty()
                is WalletImportKind.WatchAccountXpub -> return kind.xpub.isNotEmpty()
                is WalletImportKind.WatchViewKey ->
                    return kind.address.isNotEmpty() && kind.viewKey.isNotEmpty() && isRestoreHeightValid
                is WalletImportKind.WatchMultisig -> return kind.policy.isNotEmpty()
                WalletImportKind.Phrase, WalletImportKind.PrivateKey -> Unit
            }
            return isSecretComplete && (!requiresBackupVerification || isBackupVerificationComplete)
        }

    /**
     * Whether the secret step — a seed phrase or a private key — is complete
     * enough to move on. The one definition both the step and the submit use.
     */
    val isSecretComplete: Boolean
        get() {
            val chain = chain ?: return false
            if (isPrivateKeyImportMode) return isValidPrivateKey(chain, privateKeyInput)
            return seedEntry.verdict.isValid && isRestoreHeightValid && passphraseConfirmationError == null
        }

    /** Creating a wallet, a passphrase must be typed the same twice. */
    val passphraseConfirmationError: String?
        get() {
            if (!isCreateMode || overridePassphrase.isEmpty() || overridePassphrase == overridePassphraseConfirmation) return null
            return AppLocalization.string("The passphrase and its confirmation do not match.")
        }

    val requiresBackupVerification: Boolean get() = isCreateMode

    val isBackupVerificationComplete: Boolean
        get() {
            if (!requiresBackupVerification) return true
            val indices = backupVerificationWordIndices
            val entries = backupVerificationEntries
            if (indices.size != entries.size || indices.isEmpty()) return false
            val words = seedPhraseWords
            if (words.size != selectedSeedPhraseWordCount) return false
            indices.forEachIndexed { offset, index ->
                if (index !in words.indices) return false
                val expected = words[index].trim().lowercase()
                val entered = entries[offset].trim().lowercase()
                if (expected != entered) return false
            }
            return true
        }

    val backupVerificationPromptLabel: String
        get() {
            if (!requiresBackupVerification) return ""
            if (backupVerificationWordIndices.isEmpty()) {
                return AppLocalization.string("Generate a backup verification challenge to continue.")
            }
            return ""
        }

    /** An empty form, on no network. */
    fun clear() {
        // Reset outside create mode: resetting the word count regenerates a
        // phrase in create mode.
        mode = WalletDraftMode.Setup(WalletSetupMethod.IMPORT_PHRASE)
        reset()
    }

    /**
     * A fresh form for adding a wallet on [chain] by [method]. Creating
     * generates exactly one phrase.
     */
    fun configure(chain: Chain, method: WalletSetupMethod, upgrading: String? = null) {
        clear()
        this.chain = chain
        upgradeWalletId = upgrading
        // The grid judges the phrase in the network's own formats.
        seedEntry.chain = chain
        createdLengths.firstOrNull()?.let { selectedSeedPhraseWordCount = it.wordCount.toInt() }
        mode = WalletDraftMode.Setup(method)
        derivationProfile = derivationProfiles.firstOrNull()
        if (method == WalletSetupMethod.CREATE_PHRASE) regenerateSeedPhrase()
    }

    /**
     * The single import of an account a funds scan found: on its network, with
     * the phrase and the passphrase it was scanned with, exactly as typed,
     * deriving as it was found.
     */
    fun configure(foundAccount: FundsFinderCandidate, words: List<String>, passphrase: String) {
        configure(foundAccount.chainId, WalletSetupMethod.IMPORT_PHRASE)
        seedEntry.load(words, words.size)
        overridePassphrase = passphrase
        derive(foundAccount)
    }

    /**
     * Derive as [candidate] was found: at its wallet version on TON, and at its
     * profile and account in place of a typed path. `false`, changing nothing,
     * for a network that derives one account.
     */
    fun derive(candidate: FundsFinderCandidate): Boolean {
        if (candidate.tonWalletVersion == null && candidate.profile == null) return false
        candidate.tonWalletVersion?.let { tonWalletVersion = it }
        candidate.profile?.let { profile ->
            derivationProfile = profile
            derivationAccount = candidate.account
            customDerivationPath = ""
        }
        return true
    }

    /**
     * The lengths a phrase created on the network can have — BIP-39's five,
     * Monero's 25, TON's 24 — shortest first.
     */
    val createdLengths: List<SeedPhraseLength>
        get() = chain?.let { createdPhraseLengths(it) } ?: emptyList()

    fun configureForEditing(wallet: WalletState) {
        clear()
        mode = WalletDraftMode.Edit
        walletName = wallet.name
    }

    fun reset() {
        upgradeWalletId = null
        walletName = ""
        seedEntry.reset()
        walletPassword.clearText()
        walletPasswordConfirmation.clearText()
        privateKeyInput = ""
        derivationProfile = null
        derivationAccount = 0u
        customDerivationPath = ""
        overridePassphrase = ""
        overridePassphraseConfirmation = ""
        overrideHmacKey = ""
        selectedSeedPhraseWordCount = 12
        watchOnlyInput = ""
        accountXpubInput = ""
        viewKeyInput = ""
        descriptorInput = ""
        restoreHeightInput = ""
        namedAccountInput = ""
        tonWalletVersion = TonWalletVersion.W5
        chain = null
        seedEntry.chain = null
        backupVerificationWordIndices = emptyList()
        backupVerificationEntries = emptyList()
    }

    fun regenerateSeedPhrase() {
        if (!isCreateMode) return
        val chain = chain ?: return
        backupVerificationWordIndices = emptyList()
        backupVerificationEntries = emptyList()
        // The length is one of core's for the network's created format, so
        // generating it cannot be refused; if it were, the grid shows no
        // phrase rather than a guessed one.
        val generatedPhrase = try {
            generateSeedPhrase(chain, selectedSeedPhraseWordCount.toUInt())
        } catch (_: Exception) {
            ""
        }
        val generatedWords = generatedPhrase.lowercase().whitespaceSeparatedWords()
        seedEntry.load(generatedWords, selectedSeedPhraseWordCount)
    }

    fun prepareBackupVerificationChallenge() {
        if (!requiresBackupVerification) {
            backupVerificationWordIndices = emptyList()
            backupVerificationEntries = emptyList()
            return
        }
        val words = seedPhraseWords
        if (words.size != selectedSeedPhraseWordCount) {
            backupVerificationWordIndices = emptyList()
            backupVerificationEntries = emptyList()
            return
        }
        val random = SecureRandom()
        val indices = mutableSetOf<Int>()
        while (indices.size < minOf(3, selectedSeedPhraseWordCount)) {
            indices += random.nextInt(selectedSeedPhraseWordCount)
        }
        val sortedIndices = indices.sorted()
        backupVerificationWordIndices = sortedIndices
        backupVerificationEntries = List(sortedIndices.size) { "" }
    }

    fun updateBackupVerificationEntry(index: Int, value: String) {
        if (index !in backupVerificationEntries.indices) return
        backupVerificationEntries = backupVerificationEntries.toMutableList().also {
            it[index] = value.trim().lowercase()
        }
    }

    companion object {
        /**
         * What Funds Finder's Import All commits for a found account: the
         * single import's draft, sealed with [password]. No name: core names it.
         */
        fun importCommit(
            foundAccount: FundsFinderCandidate,
            words: List<String>,
            passphrase: String,
            password: String,
        ): WalletImportCommit? {
            val draft = WalletImportDraft()
            draft.configure(foundAccount, words, passphrase)
            draft.walletPassword.setTextAndPlaceCursorAtEnd(password)
            draft.walletPasswordConfirmation.setTextAndPlaceCursorAtEnd(password)
            return draft.importCommit(name = "")
        }
    }
}

/** Why core refused a wallet password, in the reader's language. */
val WalletPasswordRejection.localizedMessage: String
    get() = when (this) {
        is WalletPasswordRejection.TooShort -> AppLocalization.format(
            "Wallet password must be at least %lld characters, or leave it blank.",
            minChars.toLong(),
        )
        WalletPasswordRejection.ConfirmationMismatch -> AppLocalization.string("Wallet password confirmation does not match.")
    }
