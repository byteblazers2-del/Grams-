package com.example.core.model

import com.example.core.network.OnChainMessageStatus
import java.net.URI

const val ASSET_ID_USDT = "USDT"
const val USDT_DECIMALS = 6
const val DEFAULT_JETTON_DECIMALS = 9
const val MAX_JETTON_DECIMALS = 18

enum class JettonVerificationStatus {
  ALLOWLISTED,
  VERIFIED,
  UNVERIFIED,
  BLACKLISTED;

  val isVerified: Boolean
    get() = this == ALLOWLISTED || this == VERIFIED

  companion object {
    fun fromTonApiWire(value: String?): JettonVerificationStatus =
      when (value?.trim()?.lowercase()) {
        "whitelist", "whitelisted" -> VERIFIED
        "blacklist", "blacklisted" -> BLACKLISTED
        else -> UNVERIFIED
      }

    fun fromStoredOrDefault(value: String?): JettonVerificationStatus =
      value?.let { runCatching { valueOf(it) }.getOrNull() } ?: UNVERIFIED
  }
}

object JettonUrlSanitizer {
  private const val MAX_URL_LENGTH = 512
  private const val IPFS_GATEWAY_PREFIX = "https://ipfs.io/ipfs/"
  private val SAFE_IPFS_PATH = Regex("^[a-zA-Z0-9._\\-/]+$")

  fun sanitizeImageUrl(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed.length > MAX_URL_LENGTH) return null
    if (trimmed.any { it.isISOControl() || it.isWhitespace() }) return null

    if (trimmed.startsWith("ipfs://", ignoreCase = true)) {
      val path = trimmed.substring(7).trimStart('/').removePrefix("ipfs/").trimStart('/')
      if (path.isEmpty() || path.contains("..") || !SAFE_IPFS_PATH.matches(path)) return null
      return "$IPFS_GATEWAY_PREFIX$path"
    }

    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    val host = uri.host?.trim().orEmpty()
    if (host.isEmpty() || host.equals("localhost", ignoreCase = true)) return null
    return trimmed
  }
}

object JettonMetadataSanitizer {
  fun sanitizeSymbol(raw: String?, fallback: String = "JETTON"): String {
    val cleaned =
      raw.orEmpty()
        .replace(Regex("[\\p{Cntrl}\\s]+"), " ")
        .trim()
    if (cleaned.isEmpty()) return fallback
    return cleaned.take(16)
  }

  fun sanitizeName(raw: String?, fallbackSymbol: String): String {
    val cleaned =
      raw.orEmpty()
        .replace(Regex("[\\p{Cntrl}\\r\\n]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (cleaned.isEmpty()) return fallbackSymbol
    return cleaned.take(48)
  }
}

data class AllowedJettonSpec(
  val assetId: String,
  val symbol: String,
  val name: String,
  val network: Network,
  val masterFriendly: String,
  val decimals: Int,
) {
  val masterAddress: TonAddress by lazy {
    TonAddress.parse(masterFriendly, network).getOrThrow()
  }
}

object JettonAllowList {
  // Official Tether USD (USDt) TON Mainnet Jetton Master contract
  const val MAINNET_USDT_MASTER = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"

  val MAINNET_USDT =
    AllowedJettonSpec(
      assetId = ASSET_ID_USDT,
      symbol = "USDT",
      name = "Tether USD",
      network = Network.MAINNET,
      masterFriendly = MAINNET_USDT_MASTER,
      decimals = USDT_DECIMALS,
    )

  private val SCAM_LINK_REGEX =
    Regex("(https?://|t\\.me/|www\\.|claim\\s+|airdrop|voucher|\\b[a-z0-9-]+\\.(com|io|org|net|xyz|top|site|vip|cc|app)\\b)", RegexOption.IGNORE_CASE)

  fun usdtFor(network: Network): AllowedJettonSpec? =
    when (network) {
      Network.MAINNET -> MAINNET_USDT
      Network.TESTNET -> null
    }

  fun allFor(network: Network): List<AllowedJettonSpec> =
    when (network) {
      Network.MAINNET -> listOf(MAINNET_USDT)
      Network.TESTNET -> emptyList()
    }

  fun findVerified(
    masterAddress: TonAddress,
    reportedDecimals: Int? = null,
  ): AllowedJettonSpec? {
    val candidate =
      allFor(masterAddress.network).firstOrNull { spec ->
        spec.masterAddress.raw.equals(masterAddress.raw, ignoreCase = true)
      } ?: return null

    if (reportedDecimals != null && reportedDecimals != candidate.decimals) {
      return null
    }
    return candidate
  }

  fun isLikelyScamMetadata(name: String?, symbol: String?): Boolean {
    val combined = "${name.orEmpty()} ${symbol.orEmpty()}"
    return SCAM_LINK_REGEX.containsMatchIn(combined)
  }
}

data class JettonHolding(
  val masterAddress: TonAddress,
  val jettonWalletAddress: TonAddress?,
  val symbol: String,
  val name: String,
  val balance: Money,
  val imageUrl: String? = null,
  val verification: JettonVerificationStatus = JettonVerificationStatus.UNVERIFIED,
  val providerName: String = "TON",
  val fetchedAtEpochMs: Long = 0L,
) {
  val masterRaw: String
    get() = masterAddress.raw

  val isUsdtAllowlisted: Boolean
    get() =
      verification == JettonVerificationStatus.ALLOWLISTED &&
        JettonAllowList.findVerified(masterAddress, balance.decimals) != null
}

data class RemoteJettonHoldingsPage(
  val holdings: List<JettonHolding>,
  val providerName: String,
  val fetchedAtEpochMs: Long,
)

data class RemoteJettonBalance(
  val ownerAddress: TonAddress,
  val jettonMaster: TonAddress,
  val jettonWalletAddress: TonAddress?,
  val balance: Money,
  val providerName: String,
  val fetchedAtEpochMs: Long,
)

data class RemoteJettonTransfer(
  val txHash: String,
  val lt: String,
  val utimeEpochSec: Long,
  val direction: TxDirection,
  val jettonMasterRaw: String,
  val amount: Money,
  val counterpartyRaw: String,
  val comment: String?,
  val status: OnChainMessageStatus,
  val traceId: String? = null,
  val jettonSymbol: String? = null,
  val jettonName: String? = null,
  val jettonImageUrl: String? = null,
  val jettonVerification: JettonVerificationStatus = JettonVerificationStatus.UNVERIFIED,
)

data class RemoteJettonHistoryPage(
  val transfers: List<RemoteJettonTransfer>,
  val providerName: String,
)

