package dev.spectra.staking

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.app.AppState
import dev.spectra.app.DeviceAuthenticationAction
import dev.spectra.app.WalletServiceBridge
import dev.spectra.app.authenticate
import dev.spectra.app.refreshTransactionProjection
import dev.spectra.app.runToCompletion
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.DisplayedError
import dev.spectra.models.requiresPassword
import dev.spectra.models.userErrorMessage
import dev.spectra.send.SendSession
import dev.spectra.send.handleBroadcastCompletion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uniffi.spectra_core.Chain
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.StakingAction
import uniffi.spectra_core.StakingInputRules
import uniffi.spectra_core.StakingPosition
import uniffi.spectra_core.StakingPositionStatus
import uniffi.spectra_core.StakingRequest
import uniffi.spectra_core.StakingValidator
import uniffi.spectra_core.formatNativeAmount
import uniffi.spectra_core.stakingInputRules
import java.util.UUID

/** A transient form over core's persisted positions, reviews and broadcast journal. */
internal class StakingState(
    val chain: Chain,
    bridge: WalletServiceBridge,
    operations: StakingOperations? = null,
    private val authentication: (suspend (String) -> String?)? = null,
    private val broadcastCompletion: (suspend (SendArtifact) -> Unit)? = null,
) {
    sealed interface Operation {
        data object Positions : Operation
        data object Build : Operation
        data object Sign : Operation
        data object Broadcast : Operation
        data class Resume(val savedId: String) : Operation
        data object Recheck : Operation
        data object Repair : Operation
    }

    class Request(val operation: Operation) {
        val id: UUID = UUID.randomUUID()
    }

    /**
     * Where a failure is shown: beside what raised it, not at the foot of
     * the page — the positions card, or the step on screen (the form, or the
     * review once there is one).
     */
    enum class ErrorPlace { POSITIONS, STEP }

    private val operations: StakingOperations = operations ?: StakingOperations.live(bridge)
    val session = SendSession(this.operations.progress)
    var validators by mutableStateOf<List<StakingValidator>>(emptyList())
    var positions by mutableStateOf<List<StakingPosition>>(emptyList())
    var savedArtifacts by mutableStateOf<List<SendArtifact>>(emptyList())
    var walletId by mutableStateOf("")
    var action by mutableStateOf(StakingAction.STAKE)
    var validatorId by mutableStateOf("")
    var positionId by mutableStateOf<String?>(null)
    var amount by mutableStateOf("")
    var lockupSeconds by mutableStateOf("")
    var extraTarget by mutableStateOf("")

    /**
     * Asked for once and used by every step on the page — reading positions,
     * building, signing, checking — and kept through a failure so it can be
     * corrected rather than retyped. Forgotten when the page closes or
     * another wallet is chosen.
     */
    val password = TextFieldState()
    var request by mutableStateOf<Request?>(null)
    var isLoading by mutableStateOf(false)
    var hasLoadedPositions by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var errorPlace by mutableStateOf(ErrorPlace.STEP)
    private var generation = UUID.randomUUID()
    private var positionsRead = UUID.randomUUID()
    private var savedRead = UUID.randomUUID()

    private suspend fun authorize(reason: String, app: AppState): String? {
        val authentication = authentication ?: return app.authenticate(DeviceAuthenticationAction.SEND, reason)
        return authentication(reason)
    }

    val rules: StakingInputRules get() = stakingInputRules(chain, action)
    val isBusy: Boolean get() = request != null || session.isBusy

    fun selectWallet(id: String) {
        if (id == walletId) return
        cancel()
        walletId = id
        positions = emptyList()
        savedArtifacts = emptyList()
        hasLoadedPositions = false
        startStake()
    }

    fun startStake() {
        session.reset()
        action = StakingAction.STAKE
        validatorId = ""
        positionId = null
        amount = ""
        lockupSeconds = ""
        error = null
    }

    fun selectPosition(position: StakingPosition, action: StakingAction) {
        session.reset()
        this.action = action
        validatorId = position.validatorIdentifier
        positionId = position.id
        val units = if (action == StakingAction.WITHDRAW) {
            position.withdrawableAmountSmallestUnit
        } else {
            position.stakedAmountSmallestUnit
        }
        amount = if (stakingInputRules(chain, action).amountAllowed) {
            formatNativeAmount(chain, units)?.let { AmountPresentation.decimalFieldText(it) } ?: ""
        } else {
            ""
        }
        lockupSeconds = ""
        error = null
    }

    fun begin(operation: Operation) {
        if (isBusy) return
        error = null
        session.error = null
        request = Request(operation)
    }

    fun cancel() {
        generation = UUID.randomUUID()
        request = null
        // A fresh state is cancelled as it is made, inside composition: only
        // a typed password needs an edit.
        if (password.text.isNotEmpty()) password.clearText()
        session.reset()
    }

    private suspend fun current(token: UUID): Boolean = generation == token && currentCoroutineContext().isActive

    private fun fail(failure: Throwable, place: ErrorPlace) {
        error = userErrorMessage(failure)
        errorPlace = place
    }

    /** The failure of the positions read, shown in its card. */
    val positionsError: String? get() = if (errorPlace == ErrorPlace.POSITIONS) error else null

    /** The failure of the step on screen: the form's, or the review's. */
    val stepError: String? get() = (if (errorPlace == ErrorPlace.STEP) error else null) ?: session.error

    /** The validator [identifier] names, by its name, once the list has it. */
    fun validatorName(identifier: String): String? = validators.firstOrNull { it.identifier == identifier }?.displayName

    suspend fun loadValidators() {
        if (isLoading) return
        isLoading = true
        try {
            validators = operations.validators(chain)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail(failure, ErrorPlace.STEP)
        } finally {
            isLoading = false
        }
    }

    suspend fun loadWalletData() {
        val token = generation
        val wallet = walletId
        if (wallet.isEmpty()) return
        loadSavedArtifacts()
        if (!current(token) || wallet != walletId) return
        try {
            if (!rules.positionsRequireAuthorization) readPositions(wallet, password = null, token = token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (current(token) && wallet == walletId) fail(failure, ErrorPlace.POSITIONS)
        }
    }

    suspend fun loadSavedArtifacts() {
        val token = generation
        val wallet = walletId
        if (wallet.isEmpty()) return
        val query = UUID.randomUUID()
        savedRead = query
        try {
            val saved = operations.saved()
            if (!current(token) || wallet != walletId || savedRead != query) return
            savedArtifacts = saved.filter { it.staking != null && it.chainId == chain && it.walletId == wallet }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (current(token) && wallet == walletId && savedRead == query) fail(failure, ErrorPlace.STEP)
        }
    }

    private suspend fun readPositions(wallet: String, password: String?, token: UUID) {
        val targets = if (extraTarget.isEmpty()) emptyList() else listOf(extraTarget)
        val query = UUID.randomUUID()
        positionsRead = query
        try {
            val result = operations.positions(wallet, chain, targets, password)
            if (!current(token) || walletId != wallet || positionsRead != query) return
            positions = result
            hasLoadedPositions = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (!current(token) || walletId != wallet || positionsRead != query) return
            throw failure
        }
    }

    private fun intent(): StakingRequest {
        val rules = rules
        val delay = if (rules.lockupRequired) {
            lockupSeconds.toULongOrNull() ?: throw DisplayedError(AppLocalization.string("staking.delay_integer"))
        } else {
            null
        }
        return StakingRequest(
            walletId = walletId,
            chainId = chain,
            action = action,
            validatorId = if (rules.validatorRequired && validatorId.isNotEmpty()) validatorId else null,
            positionId = positionId,
            amount = if (rules.amountAllowed && amount.isNotEmpty()) AmountPresentation.canonicalDecimalInput(amount) else null,
            lockupSeconds = delay,
        )
    }

    suspend fun perform(id: UUID, app: AppState) {
        val request = request ?: return
        if (request.id != id) return
        val token = generation
        val wallet = walletId
        val requiresPassword = app.wallet(session.artifact?.walletId ?: wallet)?.signing?.requiresPassword ?: true
        val suppliedPassword = if (requiresPassword) password.text.toString() else null
        try {
            when (val operation = request.operation) {
                Operation.Positions -> {
                    if (rules.positionsRequireAuthorization) {
                        authorize(AppLocalization.string("staking.authorize_positions"), app)?.let { throw DisplayedError(it) }
                        if (!current(token)) return
                    }
                    readPositions(wallet, suppliedPassword, token)
                }
                Operation.Build -> {
                    val input = intent()
                    authorize(AppLocalization.string("staking.authorize_build"), app)?.let { throw DisplayedError(it) }
                    if (!current(token) || intent() != input) return
                    session.load(
                        operation = SendSession.Operation.BUILD,
                        prepare = {
                            val result = operations.build(input, suppliedPassword)
                            if (generation != token || intent() != input) {
                                throw DisplayedError(AppLocalization.string("staking.inputs_changed"))
                            }
                            result
                        },
                        loadEndpoints = { operations.endpoints(it) },
                    )
                }
                Operation.Sign -> session.sign(
                    password = suppliedPassword,
                    authenticate = { authorize(AppLocalization.string("Authorize transaction signing"), app) },
                    sign = { artifactId, digest, secret -> operations.sign(artifactId, digest, secret) },
                )
                Operation.Broadcast -> {
                    // A committed broadcast belongs to the application even
                    // after this page closes: its completion runs on the
                    // application's scope, and only the page's adoption ends
                    // with the page.
                    app.scope.launch {
                        val submitted = session.broadcast { artifactId, chosen -> operations.broadcast(artifactId, chosen) }
                            ?: return@launch
                        val completion = broadcastCompletion
                        if (completion != null) completion(submitted) else app.handleBroadcastCompletion(submitted)
                        if (generation == token) session.refresh()
                    }.join()
                }
                is Operation.Resume -> {
                    session.reset()
                    session.load(
                        operation = SendSession.Operation.RESUME,
                        prepare = { operations.inspect(operation.savedId) },
                        loadEndpoints = { operations.endpoints(it) },
                    )
                }
                Operation.Recheck -> {
                    val progress = session.progress
                    if (progress != null && progress.canRecheck) {
                        val artifact = progress.artifact
                        if (rules.positionsRequireAuthorization) {
                            authorize(AppLocalization.string("staking.authorize_status"), app)?.let { throw DisplayedError(it) }
                            if (!current(token) || session.artifact?.id != artifact.id) return
                        }
                        runToCompletion { operations.recheck(artifact.id, suppliedPassword) }
                        app.refreshTransactionProjection()
                        if (current(token) && session.artifact?.id == artifact.id) session.refresh()
                    }
                }
                Operation.Repair -> {
                    val progress = session.progress ?: return
                    if (!progress.canRepair) return
                    val artifact = progress.artifact
                    authorize(AppLocalization.string("staking.authorize_repair"), app)?.let { throw DisplayedError(it) }
                    if (!current(token) || session.artifact?.reviewDigest != artifact.reviewDigest) return
                    session.load(
                        operation = SendSession.Operation.BUILD,
                        prepare = { operations.repair(artifact.id, suppliedPassword) },
                        loadEndpoints = { operations.endpoints(it) },
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (!current(token)) return
            fail(failure, if (request.operation == Operation.Positions) ErrorPlace.POSITIONS else ErrorPlace.STEP)
        } finally {
            if (this.request?.id == id) this.request = null
        }
    }
}

internal val StakingAction.localizedTitle: String
    get() = AppLocalization.string(
        when (this) {
            StakingAction.STAKE -> "staking.stake"
            StakingAction.UNSTAKE -> "staking.unstake"
            StakingAction.WITHDRAW -> "staking.withdraw"
            StakingAction.CLAIM_REWARDS -> "staking.claim_rewards"
        },
    )

internal val StakingPositionStatus.localizedTitle: String
    get() = AppLocalization.string(
        when (this) {
            StakingPositionStatus.ACTIVE -> "staking.active"
            StakingPositionStatus.ACTIVATING -> "staking.activating"
            StakingPositionStatus.UNBONDING -> "staking.unbonding"
            StakingPositionStatus.WITHDRAWABLE -> "staking.withdrawable"
            StakingPositionStatus.INACTIVE -> "staking.inactive"
        },
    )
