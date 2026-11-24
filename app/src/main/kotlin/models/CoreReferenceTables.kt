package dev.spectra.models

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import uniffi.spectra_core.AssetWikiEntry
import uniffi.spectra_core.Chain
import uniffi.spectra_core.ChainWikiEntry
import uniffi.spectra_core.DonationDestination
import uniffi.spectra_core.EndpointSettingsGroup
import uniffi.spectra_core.InputBounds
import uniffi.spectra_core.StakingChainEntry
import uniffi.spectra_core.donationDestinations
import uniffi.spectra_core.endpointSettings
import uniffi.spectra_core.inputBounds
import uniffi.spectra_core.listAllBuiltinTokenDeployments
import uniffi.spectra_core.listAssetWiki
import uniffi.spectra_core.listChainWiki
import uniffi.spectra_core.listStakingChains

/**
 * Process-lifetime copies of core's compile-time tables, fetched once on first
 * use. Do not cache secret inputs or helpers that are already cheap lookups.
 */
object CoreReferenceTables {
    val assetWiki: List<AssetWikiEntry> by lazy { listAssetWiki() }
    val chainWiki: List<ChainWikiEntry> by lazy { listChainWiki() }

    /** The bounds core holds edits to, for the controls that set them. */
    val bounds: InputBounds by lazy { inputBounds() }

    private val stakingByChain: Map<Chain, StakingChainEntry> by lazy { listStakingChains().associateBy { it.chain } }
    private val endpointSettingsByChain by lazy { endpointSettings().associateBy { it.chainId } }

    /** The project's donation addresses, validated by core when its catalog loads. */
    val donationDestinations: List<DonationDestination> by lazy { donationDestinations() }

    /** What staking means on each chain that stakes. */
    fun stakingEntry(chain: Chain): StakingChainEntry? = stakingByChain[chain]

    /** The catalog's endpoints for [chain], grouped as settings lists them. */
    fun endpointGroups(chain: Chain): List<EndpointSettingsGroup> = endpointSettingsByChain[chain]?.groups ?: emptyList()
}

/**
 * Immutable catalog presentation, indexed once by deployment id. A holding's
 * id is core's; this only looks up what the catalog draws for it. Keyed by
 * identity, never by ticker: a custom token calling itself `ETH` is not Ether.
 */
object AssetPresentationCatalog {
    private val deployments by lazy { listAllBuiltinTokenDeployments() }
    private val artworkByDeployment: Map<String, String> by lazy {
        deployments.associate { it.deploymentId to it.artworkName }
    }
    private val colorByDeployment: Map<String, Color> by lazy {
        val colors = HashMap<String, Color>()
        for (entry in ChainCatalog.entries) colors[entry.nativeDeploymentId] = entry.color.color
        for (token in deployments) token.color?.let { colors[token.deploymentId] = it.color }
        colors
    }

    fun artwork(deploymentId: String?): String = deploymentId?.let { artworkByDeployment[it] } ?: ""

    fun color(deploymentId: String): Color = colorByDeployment[deploymentId] ?: Color.Gray
}

/** A buy or exchange provider, from `assets/BuyProviders.json`. Names and URLs do not translate. */
data class BuyProvider(val id: String, val name: String, val url: String, val label: String)

data class BuyProviders(val onramps: List<BuyProvider>, val exchanges: List<BuyProvider>) {
    companion object {
        @Volatile private var cached: BuyProviders? = null

        /** The directory; adding a provider is one row in the JSON. */
        fun load(context: Context): BuyProviders = cached ?: run {
            val json = context.assets.open("BuyProviders.json").bufferedReader().use { JSONObject(it.readText()) }
            fun list(key: String): List<BuyProvider> {
                val array = json.getJSONArray(key)
                return (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    BuyProvider(item.getString("id"), item.getString("name"), item.getString("url"), item.getString("label"))
                }
            }
            BuyProviders(list("onramps"), list("exchanges")).also { cached = it }
        }
    }
}
