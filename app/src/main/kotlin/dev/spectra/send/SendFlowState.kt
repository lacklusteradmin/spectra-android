package dev.spectra.send

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.app.MainAppTab
import dev.spectra.app.WalletServiceBridge
import dev.spectra.app.rethrowIfCancellation
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.DisplayedError
import dev.spectra.models.chainName
import dev.spectra.models.chainRiskProbeMessages
import dev.spectra.models.localizedText
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uniffi.spectra_core.AssetHolding
import uniffi.spectra_core.EvmCustomFeeConfiguration
import uniffi.spectra_core.EvmCustomFeeException
import uniffi.spectra_core.EvmFeeTerms
import uniffi.spectra_core.EvmNonceException
import uniffi.spectra_core.EvmSendOverridesInput
import uniffi.spectra_core.HighRiskSendWarning
import uniffi.spectra_core.OwnedSendPreview
import uniffi.spectra_core.PaymentMemo
import uniffi.spectra_core.PaymentMemoKind
import uniffi.spectra_core.RecipientCheck
import uniffi.spectra_core.ScannedPayment
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.SendDestinationResolution
import uniffi.spectra_core.SendFeeTerms
import uniffi.spectra_core.SendReviewInput
import uniffi.spectra_core.SpectraBridgeException
import uniffi.spectra_core.parseEvmCustomFees
import uniffi.spectra_core.parseEvmNonce
import uniffi.spectra_core.readScannedPayment
import java.util.UUID

/** Raw form identity, including invalid edits that may parse to the same `null` value. */
data class SendPreviewInputSnapshot(
    val walletId: String,
    val holdingKey: String,
    val amount: String,
    val destination: String,
    val nonceEnabled: Boolean,
    val nonce: String,
    val feesEnabled: Boolean,
    val maxFee: String,
    val priorityFee: String,
)

/**
 * Why core turned the recipient down, in its words, and whether asking again
 * could change the answer: only a failure to reach or read the network can,
 * so only that offers Retry.
 */
data class SendRecipientProblem(val message: String, val canRetry: Boolean) {
    constructor(error: Throwable) : this(
        message = userErrorMessage(error),
        canRetry = error is SpectraBridgeException.Network || error is SpectraBridgeException.Decode,
    )
}

/**
 * The send composer: its form, its preview and the send it stages.
 *
 * Transient native flow state; persisted domain data remains in core, which
 * resolves the recipient, quotes, builds, signs and broadcasts. Every request
 * the composer makes starts here, when what it depends on changes while the
 * composer is on screen, and only its latest answer is adopted. What only the
 * wallet projection knows — the selected holding, whether a wallet signs with
 * a password, what a completed send sets off — `AppState` supplies.
 *
 * Contract with `AppState`: the constructor, [selectedHolding],
 * [refreshProgress], [updateVerificationNotice] and [reset] keep their
 * signatures.
 */
class SendFlowState(private val bridge: WalletServiceBridge, private val scope: CoroutineScope) {
    /**
     * The holding the composer has selected, as the wallet projection has it
     * now. `AppState` supplies it; requests read it when they start and when
     * they land.
     */
    var selectedHolding: () -> AssetHolding? = { null }

    private var walletIdState by mutableStateOf("")
    var walletId: String
        get() = walletIdState
        set(value) {
            if (value == walletIdState) return
            walletIdState = value
            recipientChanged()
        }

    private var holdingKeyState by mutableStateOf("")

    /** A memo belongs to the network it was typed for, so another holding clears it. */
    var holdingKey: String
        get() = holdingKeyState
        set(value) {
            if (value == holdingKeyState) return
            holdingKeyState = value
            clearMemo()
            recipientChanged()
        }

    private var amountState by mutableStateOf("")
    var amount: String
        get() = amountState
        set(value) {
            if (value == amountState) return
            amountState = value
            schedulePreview()
        }

    private var addressState by mutableStateOf("")
    var address: String
        get() = addressState
        set(value) {
            if (value == addressState) return
            addressState = value
            recipientChanged()
        }

    /** The destination tag or memo for the recipient, where the network takes one. */
    var memoKind by mutableStateOf<PaymentMemoKind?>(null)
    var memoText by mutableStateOf("")

    /** What the last scanned code filled in, said for as long as the recipient is still the one it named. */
    var scannedPayment by mutableStateOf<ScannedPayment?>(null)

    /**
     * What core's recipient check says deserves a second look: an address
     * this wallet has never sent to, one with no history on chain.
     */
    var destinationWarnings by mutableStateOf<List<String>>(emptyList())
    var destinationInfoMessage by mutableStateOf<String?>(null)

    /** Core's resolution of the recipient as typed, once it has answered for the address in the field now. */
    var resolvedRecipient by mutableStateOf<SendDestinationResolution?>(null)
        private set

    /** Why core turned the recipient as typed down. */
    var recipientProblem by mutableStateOf<SendRecipientProblem?>(null)
        private set
    var isResolvingRecipient by mutableStateOf(false)
        private set

    /** The recipient is checked inside the preview request. */
    val isCheckingDestination: Boolean get() = isPreparingPreview && address.isNotBlank()

    var isShowingHighRiskConfirmation by mutableStateOf(false)
    var verificationNotice by mutableStateOf<String?>(null)
    var verificationNoticeIsWarning by mutableStateOf(false)
    var isPreparingReplacement by mutableStateOf(false)
    var isPreparingPreview by mutableStateOf(false)

    /**
     * Why the last quote failed. Separate from `session.error`, which is the
     * build's, the signature's or the broadcast's: a quote that did not land
     * leaves the fee unknown, and no build goes ahead without one.
     */
    var previewError by mutableStateOf<String?>(null)

    /** The form the last quote answered. A quote is current only while the form still reads the same. */
    var quotedInput by mutableStateOf<SendPreviewInputSnapshot?>(null)

    val session = SendSession(bridge)
    var savedArtifacts by mutableStateOf<List<SendArtifact>>(emptyList())
    val previewStore = SendPreviewStore()

    private var useCustomEvmFeesState by mutableStateOf(false)

    /**
     * An override turned on starts from the quote's own terms: an edit of
     * what the network asks, not a blank field that reads as an error the
     * moment it appears.
     */
    var useCustomEvmFees: Boolean
        get() = useCustomEvmFeesState
        set(value) {
            if (value == useCustomEvmFeesState) return
            useCustomEvmFeesState = value
            schedulePreview()
            val terms = quotedEvmTerms
            if (!value || terms == null || customEvmMaxFeeGwei.isNotEmpty() || customEvmPriorityFeeGwei.isNotEmpty()) return
            customEvmMaxFeeGwei = AmountPresentation.decimalFieldText(terms.maxFeePerGasGwei)
            customEvmPriorityFeeGwei = AmountPresentation.decimalFieldText(terms.maxPriorityFeePerGasGwei)
        }

    private var customEvmMaxFeeGweiState by mutableStateOf("")
    var customEvmMaxFeeGwei: String
        get() = customEvmMaxFeeGweiState
        set(value) {
            if (value == customEvmMaxFeeGweiState) return
            customEvmMaxFeeGweiState = value
            schedulePreview()
        }

    private var customEvmPriorityFeeGweiState by mutableStateOf("")
    var customEvmPriorityFeeGwei: String
        get() = customEvmPriorityFeeGweiState
        set(value) {
            if (value == customEvmPriorityFeeGweiState) return
            customEvmPriorityFeeGweiState = value
            schedulePreview()
        }

    private var evmManualNonceEnabledState by mutableStateOf(false)
    var evmManualNonceEnabled: Boolean
        get() = evmManualNonceEnabledState
        set(value) {
            if (value == evmManualNonceEnabledState) return
            evmManualNonceEnabledState = value
            schedulePreview()
            val terms = quotedEvmTerms
            if (!value || terms == null || evmManualNonce.isNotEmpty()) return
            evmManualNonce = terms.nonce.toString()
        }

    private var evmManualNonceState by mutableStateOf("")
    var evmManualNonce: String
        get() = evmManualNonceState
        set(value) {
            if (value == evmManualNonceState) return
            evmManualNonceState = value
            schedulePreview()
        }

    /** The last quote's EVM terms, when it was an EVM quote. */
    val quotedEvmTerms: EvmFeeTerms?
        get() = (previewStore.quote?.preview?.terms as? SendFeeTerms.Evm)?.terms

    /** Rejects every completion of a superseded preview. */
    internal var previewRequestId: UUID = UUID.randomUUID()
    private var previewJob: Job? = null
    private var recipientJob: Job? = null
    private var progressJob: Job? = null

    private var isPresentedState by mutableStateOf(false)

    /**
     * Whether the composer is on screen. Opening it asks about the recipient
     * and quotes the form; closing it resets the composer.
     */
    var isPresented: Boolean
        get() = isPresentedState
        set(value) {
            if (value == isPresentedState) return
            isPresentedState = value
            if (value) {
                presentationCount += 1
                recipientChanged()
            } else {
                resetComposer()
            }
        }

    /**
     * Counts the times the composer was opened. The composer's own view state
     * — the step it is on — is keyed on it, so a composer opened again starts
     * on its first step even where the navigation entry kept its saved state.
     */
    var presentationCount by mutableIntStateOf(0)
        private set

    /** The tab whose stack the composer is pushed on: the one it was opened from, so Back returns to the page that opened it. */
    var presentingTab by mutableStateOf(MainAppTab.HOME)

    fun clearVerificationNotice() {
        verificationNotice = null
        verificationNoticeIsWarning = false
    }

    /**
     * Let the staged send go. The form stays, and is quoted again: the quote
     * the send was built from may be stale by now.
     */
    fun invalidateSession() {
        session.reset()
        progressJob?.cancel()
        previewRequestId = UUID.randomUUID()
        quotedInput = null
        isPreparingPreview = false
        isPreparingReplacement = false
        isShowingHighRiskConfirmation = false
        clearVerificationNotice()
        schedulePreview()
    }

    /**
     * Drop the last quote. A quote already scheduled still goes ahead: the
     * form it reads is the one now on screen.
     */
    fun clearPreview() {
        previewRequestId = UUID.randomUUID()
        previewStore.reset()
        quotedInput = null
        previewError = null
        isPreparingPreview = false
        isShowingHighRiskConfirmation = false
    }

    fun clearDestinationCheck() {
        destinationWarnings = emptyList()
        destinationInfoMessage = null
    }

    fun clearEvmOverrides() {
        useCustomEvmFees = false
        customEvmMaxFeeGwei = ""
        customEvmPriorityFeeGwei = ""
        evmManualNonceEnabled = false
        evmManualNonce = ""
    }

    fun clearMemo() {
        memoKind = null
        memoText = ""
    }

    fun resetComposer() {
        recipientJob?.cancel()
        progressJob?.cancel()
        invalidateSession()
        clearPreview()
        amount = ""
        address = ""
        clearMemo()
        clearDestinationCheck()
        clearEvmOverrides()
        clearRecipientResolution()
        scannedPayment = null
    }

    /** The memo as core reads it: none until something is typed. */
    val paymentMemo: PaymentMemo?
        get() {
            val kind = memoKind ?: return null
            if (memoText.isEmpty()) return null
            return PaymentMemo(kind, memoText)
        }

    /** Dismissing resets the composer; one that is not on screen is reset here. */
    fun close() {
        if (isPresented) isPresented = false else resetComposer()
    }

    /** Clear the composer and drop any staged send from view. */
    fun reset() {
        close()
        walletId = ""
        holdingKey = ""
        savedArtifacts = emptyList()
    }

    // ── The recipient ───────────────────────────────────────────────────

    /** The wallet, holding and address core's resolution answers for. */
    private data class RecipientKey(val walletId: String, val holdingKey: String, val address: String)

    private val recipientKey: RecipientKey get() = RecipientKey(walletId, holdingKey, address)

    private var resolvedRecipientKey: RecipientKey? = null

    /** Core's resolution of the recipient, if it answered for the field as it reads now. */
    val currentRecipient: SendDestinationResolution?
        get() = if (resolvedRecipientKey == recipientKey) resolvedRecipient else null

    private fun clearRecipientResolution() {
        resolvedRecipient = null
        resolvedRecipientKey = null
        recipientProblem = null
        isResolvingRecipient = false
    }

    /** The wallet, holding or address changed: ask core about the recipient once typing pauses, and quote the form again. */
    private fun recipientChanged() {
        scheduleRecipientResolution()
        schedulePreview()
    }

    /** Ask core again, as when a failure to reach the network may since have passed. */
    fun retryRecipientResolution() {
        scheduleRecipientResolution()
    }

    /**
     * Read a scanned code — a bare address or a payment URI — against the
     * network and the asset being sent, and fill in the amount and memo it
     * asks for. Returns the address in core's stored form. Without an asset
     * there is nothing to judge the code against.
     */
    fun applyScannedPayment(payload: String): String {
        val coin = selectedHolding() ?: throw DisplayedError(
            AppLocalization.string("The scanned QR code does not contain a valid address for the selected asset."),
        )
        try {
            val payment = readScannedPayment(coin.chainId, coin.contractAddress, payload)
            address = payment.address
            payment.amount?.let { amount = AmountPresentation.decimalFieldText(it) }
            payment.memo?.let { memo ->
                memoKind = memo.kind
                memoText = memo.value
            }
            scannedPayment = payment
            return payment.address
        } catch (error: Exception) {
            scannedPayment = null
            throw error
        }
    }

    private fun scheduleRecipientResolution() {
        recipientJob?.cancel()
        clearRecipientResolution()
        val key = recipientKey
        val chain = selectedHolding()?.chainId
        if (!isPresented || chain == null || address.isBlank()) return
        recipientJob = scope.launch {
            // Typing is not checking: the answer comes once typing pauses,
            // not on every keystroke.
            delay(RECIPIENT_DEBOUNCE_MILLIS)
            if (recipientKey != key) return@launch
            isResolvingRecipient = true
            try {
                val resolution = bridge.ready().resolveSendDestination(chain, key.address)
                if (!isActive || recipientKey != key) return@launch
                resolvedRecipient = resolution
                resolvedRecipientKey = key
            } catch (error: Exception) {
                error.rethrowIfCancellation()
                if (!isActive || recipientKey != key) return@launch
                recipientProblem = SendRecipientProblem(error)
            } finally {
                if (recipientKey == key) isResolvingRecipient = false
            }
        }
    }

    // ── The quote ───────────────────────────────────────────────────────

    /**
     * Quote the form once editing pauses. A newer edit cancels the wait, and
     * the request it starts supersedes any still in flight.
     */
    fun schedulePreview() {
        previewJob?.cancel()
        quotedInput = null
        if (!isPresented || session.artifact != null) return
        previewJob = scope.launch {
            delay(PREVIEW_DEBOUNCE_MILLIS)
            if (session.artifact != null) return@launch
            refreshPreview()
        }
    }

    /** Whether the last quote answers the form as it reads now. */
    val quoteIsCurrent: Boolean get() = quotedInput != null && quotedInput == previewInput

    /** The form as a quote reads it. */
    val previewInput: SendPreviewInputSnapshot
        get() = SendPreviewInputSnapshot(
            walletId = walletId,
            holdingKey = holdingKey,
            amount = amountInput,
            destination = address,
            nonceEnabled = evmManualNonceEnabled,
            nonce = evmManualNonce,
            feesEnabled = useCustomEvmFees,
            maxFee = customEvmMaxFeeGwei,
            priorityFee = customEvmPriorityFeeGwei,
        )

    /**
     * Every completion, including errors and loading cleanup, belongs to one
     * request. Core quotes and checks the recipient in the same call, and
     * quotes an empty amount provisionally, for a share of the holding.
     *
     * The selected holding is read when the request starts and again when its
     * answer lands: an answer for a form the composer has since moved off is
     * not adopted.
     */
    suspend fun refreshPreview() {
        val requestId = UUID.randomUUID()
        previewRequestId = requestId
        val input = previewInput
        previewError = null
        // An override still being typed is pointed out beside its field. Until
        // it parses there is nothing to quote, and a refused quote would only
        // say so again, worse worded. The last quote and its recipient check
        // stay as what the network said — the fields' placeholders, the
        // estimate rows, the destination warnings — and the quote is not
        // current, so nothing builds from it.
        if (evmNonceValidationError != null || customEvmFeeValidationError != null) {
            isPreparingPreview = false
            return
        }
        clearDestinationCheck()
        if (selectedHolding() == null) {
            isPreparingPreview = false
            previewStore.reset()
            return
        }
        isPreparingPreview = true
        try {
            // Every input is captured before the first suspension.
            val nonce = explicitEvmNonce()
            val fees = customEvmFeeConfiguration()
            val preview = bridge.ready().previewOwnedSend(
                input.walletId, input.holdingKey, input.amount, input.destination, nonce, fees,
            )
            adoptPreviewResult(Result.success(preview), requestId, input)
        } catch (error: Exception) {
            // A cancelled request is superseded: nothing of it is adopted.
            error.rethrowIfCancellation()
            adoptPreviewResult(Result.failure(error), requestId, input)
        } finally {
            if (previewRequestId == requestId) isPreparingPreview = false
        }
    }

    private suspend fun adoptPreviewResult(
        result: Result<OwnedSendPreview?>,
        requestId: UUID,
        input: SendPreviewInputSnapshot,
    ) {
        if (!currentCoroutineContext().isActive || previewRequestId != requestId || previewInput != input) return
        result.onSuccess { preview ->
            previewStore.apply(preview)
            previewError = null
            quotedInput = input
            clearVerificationNotice()
            adoptRecipientCheck(preview?.recipient, selectedHolding())
        }.onFailure { error ->
            previewStore.reset()
            previewError = userErrorMessage(error)
        }
    }

    /**
     * Core checks the destination beside the quote; this only words it. An
     * own address says so and nothing else: whether it is funded or fresh is
     * no reason to double-check a transfer between the user's own wallets. A
     * new destination is said here, before the build, in the words the
     * build's review uses for it.
     */
    private fun adoptRecipientCheck(check: RecipientCheck?, coin: AssetHolding?) {
        if (check == null || coin == null) return
        if (check.isOwnAddress) {
            destinationWarnings = emptyList()
            destinationInfoMessage = AppLocalization.string("This address belongs to one of your wallets.")
            return
        }
        val warnings = if (check.isNewToWallet) mutableListOf(HighRiskSendWarning.NewAddress.localizedText) else mutableListOf()
        val activity = check.activity
        if (activity != null) {
            val (warning, info) = chainRiskProbeMessages(coin.chainName, coin.symbol, activity)
            warning?.let { warnings += it }
            destinationInfoMessage = info
        } else {
            destinationInfoMessage = AppLocalization.string("Unable to verify this address's activity. Try again later.")
        }
        destinationWarnings = warnings
    }

    // ── The staged send ─────────────────────────────────────────────────

    /**
     * Read core's answer for the staged send again once it was submitted: its
     * history record moves while the artifact does not.
     */
    fun refreshProgress() {
        if (session.artifact?.attempts?.isEmpty() != false) return
        progressJob?.cancel()
        progressJob = scope.launch { session.refresh() }
    }

    private fun reviewInput(): SendReviewInput {
        (customEvmFeeValidationError ?: evmNonceValidationError)?.let { throw DisplayedError(it) }
        val nonce = explicitEvmNonce()
        val fees = customEvmFeeConfiguration()
        val overrides = if (nonce == null && fees == null) {
            null
        } else {
            EvmSendOverridesInput(nonce = nonce, customFees = fees, gasLimit = null, calldataHex = null, accessListJson = null)
        }
        return SendReviewInput(
            walletId = walletId,
            holdingKey = holdingKey,
            amount = amountInput,
            destination = address,
            overrides = overrides,
            memo = paymentMemo,
        )
    }

    // Building, signing and broadcasting the staged send. Core does each step
    // and keeps the artifact; `session` adopts its answers for the composer
    // that asked.

    suspend fun build() {
        if (session.artifact != null || session.isBusy) return
        try {
            val input = reviewInput()
            val changed = { DisplayedError(AppLocalization.string("Send inputs changed. Build the transaction again.")) }
            session.load(
                operation = SendSession.Operation.BUILD,
                prepare = {
                    val artifact = bridge.ready().buildOwnedSend(input)
                    if (reviewInput() != input) throw changed()
                    artifact
                },
                loadEndpoints = { chain ->
                    val choices = bridge.ready().sendEndpoints(chain)
                    if (reviewInput() != input) throw changed()
                    choices
                },
            )
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            session.error = userErrorMessage(error)
        }
    }

    /**
     * Signs only. Broadcasting is its own action, to the nodes the user
     * selects on the signed transaction.
     */
    suspend fun sign(password: String?, authenticate: suspend () -> String?) {
        isShowingHighRiskConfirmation = false
        session.sign(password, authenticate) { id, digest, secret -> bridge.ready().signSend(id, digest, secret) }
    }

    /**
     * The broadcast artifact, which belongs to the application even after
     * this composer closes; `null` if nothing was broadcast.
     */
    suspend fun broadcast(): SendArtifact? =
        session.broadcast { id, endpoints -> bridge.ready().broadcastSend(id, endpoints) }

    suspend fun loadSavedArtifacts() {
        val request = session.id
        try {
            val artifacts = bridge.ready().listSends()
            if (!session.isCurrent(request)) return
            savedArtifacts = artifacts
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            if (session.isCurrent(request)) session.error = userErrorMessage(error)
        }
    }

    suspend fun resume(id: String): Boolean {
        invalidateSession()
        return session.load(
            operation = SendSession.Operation.RESUME,
            prepare = { bridge.ready().inspectSend(id) },
            loadEndpoints = { bridge.ready().sendEndpoints(it) },
        )
    }

    /** What core says about the last send, judged from its stored record. */
    suspend fun updateVerificationNotice() {
        val request = session.id
        val transactionId = session.artifact?.id
        if (transactionId == null) {
            clearVerificationNotice()
            return
        }
        val notice = try {
            bridge.ready().sendVerificationNotice(transactionId)
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            return
        }
        if (!session.isCurrent(request) || session.artifact?.id != transactionId) return
        verificationNotice = notice.notice
        verificationNoticeIsWarning = notice.isWarning
    }

    // ── The form as core reads it ───────────────────────────────────────

    /** The amount field as core reads it. */
    val amountInput: String get() = AmountPresentation.canonicalDecimalInput(amount)

    /**
     * The toggle is cleared outside the EVM family; only EVM preview and
     * submit paths read these fees.
     */
    private val parsedCustomEvmFees: Result<EvmCustomFeeConfiguration>?
        get() {
            if (!useCustomEvmFees) return null
            return runCatching {
                parseEvmCustomFees(
                    AmountPresentation.canonicalDecimalInput(customEvmMaxFeeGwei),
                    AmountPresentation.canonicalDecimalInput(customEvmPriorityFeeGwei),
                )
            }
        }

    val customEvmFeeValidationError: String?
        get() = when (val error = parsedCustomEvmFees?.exceptionOrNull()) {
            null -> null
            is EvmCustomFeeException.InvalidMaxFee -> AppLocalization.string("Enter a valid Max Fee in gwei.")
            is EvmCustomFeeException.InvalidPriorityFee -> AppLocalization.string("Enter a valid Priority Fee in gwei.")
            is EvmCustomFeeException.MaxBelowPriority ->
                AppLocalization.string("Max Fee must be greater than or equal to Priority Fee.")
            else -> userErrorMessage(error)
        }

    fun customEvmFeeConfiguration(): EvmCustomFeeConfiguration? = parsedCustomEvmFees?.getOrNull()

    val evmNonceValidationError: String?
        get() = try {
            explicitEvmNonce()
            null
        } catch (_: EvmNonceException.Empty) {
            AppLocalization.string("Enter a nonce value for manual nonce mode.")
        } catch (_: EvmNonceException.InvalidInteger) {
            AppLocalization.string("Nonce must be a non-negative integer.")
        } catch (_: EvmNonceException.TooLarge) {
            AppLocalization.string("Nonce value is too large.")
        } catch (error: Exception) {
            userErrorMessage(error)
        }

    fun explicitEvmNonce(): Long? {
        if (!evmManualNonceEnabled) return null
        return parseEvmNonce(evmManualNonce)
    }

    private companion object {
        const val RECIPIENT_DEBOUNCE_MILLIS = 350L
        const val PREVIEW_DEBOUNCE_MILLIS = 350L
    }
}
