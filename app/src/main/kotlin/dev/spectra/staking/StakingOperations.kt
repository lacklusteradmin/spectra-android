package dev.spectra.staking

import dev.spectra.app.WalletServiceBridge
import uniffi.spectra_core.Chain
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.SendProgress
import uniffi.spectra_core.StakingPosition
import uniffi.spectra_core.StakingRequest
import uniffi.spectra_core.StakingValidator

/**
 * The operations the staking page calls on core. A test can suspend one of
 * them to exercise cancellation without opening a network connection.
 */
internal class StakingOperations(
    val validators: suspend (Chain) -> List<StakingValidator>,
    val positions: suspend (walletId: String, chain: Chain, targets: List<String>, password: String?) -> List<StakingPosition>,
    val saved: suspend () -> List<SendArtifact>,
    val build: suspend (StakingRequest, password: String?) -> SendArtifact,
    val endpoints: suspend (Chain) -> List<String>,
    val sign: suspend (id: String, reviewDigest: String, password: String?) -> SendArtifact,
    val broadcast: suspend (id: String, endpoints: List<String>) -> SendArtifact,
    val inspect: suspend (id: String) -> SendArtifact,
    /** Core's answer for a staged artifact, read by its id. */
    val progress: suspend (SendArtifact) -> SendProgress,
    val recheck: suspend (id: String, password: String?) -> SendArtifact,
    val repair: suspend (id: String, password: String?) -> SendArtifact,
) {
    companion object {
        fun live(bridge: WalletServiceBridge): StakingOperations = StakingOperations(
            validators = { bridge.ready().fetchStakingValidators(it) },
            positions = { walletId, chain, targets, password ->
                bridge.ready().fetchStakingPositions(walletId, chain, targets, password)
            },
            saved = { bridge.ready().listSends() },
            build = { request, password -> bridge.ready().buildStaking(request, password) },
            endpoints = { bridge.ready().stakingBroadcastEndpoints(it) },
            sign = { id, digest, password -> bridge.ready().signSend(id, digest, password) },
            broadcast = { id, endpoints -> bridge.ready().broadcastSend(id, endpoints) },
            inspect = { bridge.ready().inspectSend(it) },
            progress = { bridge.ready().sendProgress(it.id) },
            recheck = { id, password -> bridge.ready().recheckStaking(id, password) },
            repair = { id, password -> bridge.ready().repairStaking(id, password) },
        )
    }
}
