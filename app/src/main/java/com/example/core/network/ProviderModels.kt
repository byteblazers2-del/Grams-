package com.example.core.network

import com.example.core.model.AccountOnChainStatus
import com.example.core.model.AllowedJettonSpec
import com.example.core.model.JettonAllowList
import com.example.core.model.JettonHolding
import com.example.core.model.JettonVerificationStatus
import com.example.core.model.Money
import com.example.core.model.Network
import com.example.core.model.RemoteAccountBalance
import com.example.core.model.RemoteJettonBalance
import com.example.core.model.RemoteJettonHistoryPage
import com.example.core.model.RemoteJettonHoldingsPage
import com.example.core.model.RemoteJettonTransfer
import com.example.core.model.TonAddress
import com.example.core.model.TxDirection

data class RemoteWalletInfo(
  val address: TonAddress,
  val balance: Money,
  val status: AccountOnChainStatus,
  val seqno: Int,
  val providerName: String,
  val fetchedAtEpochMs: Long,
)

data class RemoteFeeEstimate(
  val estimatedFee: Money,
  val providerName: String,
)

data class BroadcastResult(
  val messageHashHex: String,
  val providerName: String,
)

enum class OnChainMessageStatus {
  PENDING,
  CONFIRMED,
  FAILED,
}

data class RemoteTxLookup(
  val status: OnChainMessageStatus,
  val txHash: String?,
  val lt: String?,
  val fee: Money?,
  val providerName: String,
  val failureReason: String? = null,
)

data class RemoteChainTransfer(
  val txHash: String,
  val lt: String,
  val utimeEpochSec: Long,
  val direction: TxDirection,
  val amount: Money,
  val fee: Money?,
  val counterpartyRaw: String,
  val comment: String?,
  val status: OnChainMessageStatus,
  val inMsgHash: String?,
  val inMsgNormalizedHash: String?,
)

data class RemoteTxHistoryPage(
  val transfers: List<RemoteChainTransfer>,
  val providerName: String,
)

interface BalanceFetcher {
  suspend fun fetchAccountBalance(address: TonAddress): Result<RemoteAccountBalance>

  suspend fun fetchJettonBalance(
    ownerAddress: TonAddress,
    spec: AllowedJettonSpec,
  ): Result<RemoteJettonBalance>

  suspend fun fetchAllJettons(ownerAddress: TonAddress): Result<RemoteJettonHoldingsPage> {
    val specs = JettonAllowList.allFor(ownerAddress.network)
    if (specs.isEmpty()) {
      return Result.success(
        RemoteJettonHoldingsPage(
          holdings = emptyList(),
          providerName = "TON",
          fetchedAtEpochMs = System.currentTimeMillis(),
        )
      )
    }
    val holdings = mutableListOf<JettonHolding>()
    var provider = "TON"
    var fetchedAt = 0L
    var lastError: Throwable? = null
    for (spec in specs) {
      fetchJettonBalance(ownerAddress, spec)
        .onSuccess { bal ->
          provider = bal.providerName
          fetchedAt = maxOf(fetchedAt, bal.fetchedAtEpochMs)
          holdings.add(
            JettonHolding(
              masterAddress = bal.jettonMaster,
              jettonWalletAddress = bal.jettonWalletAddress,
              symbol = spec.symbol,
              name = spec.name,
              balance = bal.balance,
              imageUrl = null,
              verification = JettonVerificationStatus.ALLOWLISTED,
              providerName = bal.providerName,
              fetchedAtEpochMs = bal.fetchedAtEpochMs,
            )
          )
        }
        .onFailure { lastError = it }
    }
    return if (holdings.isNotEmpty() || lastError == null) {
      Result.success(
        RemoteJettonHoldingsPage(
          holdings = holdings,
          providerName = provider,
          fetchedAtEpochMs = if (fetchedAt > 0L) fetchedAt else System.currentTimeMillis(),
        )
      )
    } else {
      Result.failure(lastError!!)
    }
  }
}

interface ChainGateway : BalanceFetcher {
  suspend fun fetchWalletInfo(address: TonAddress): Result<RemoteWalletInfo>

  suspend fun estimateExternalMessageFee(
    address: TonAddress,
    bocBase64: String,
  ): Result<RemoteFeeEstimate>

  suspend fun sendBoc(network: Network, bocBase64: String): Result<BroadcastResult>

  suspend fun lookupMessageStatus(
    network: Network,
    messageHashHex: String,
    normalizedHashHex: String,
  ): Result<RemoteTxLookup>

  suspend fun lookupJettonTraceStatus(
    network: Network,
    messageHashHex: String,
    normalizedHashHex: String,
  ): Result<RemoteTxLookup>

  suspend fun fetchAccountHistory(
    address: TonAddress,
    afterLt: String? = null,
    limit: Int = 25,
  ): Result<RemoteTxHistoryPage>

  suspend fun fetchJettonHistory(
    ownerAddress: TonAddress,
    spec: AllowedJettonSpec,
    limit: Int = 25,
  ): Result<RemoteJettonHistoryPage>

  suspend fun fetchAllJettonHistory(
    ownerAddress: TonAddress,
    limit: Int = 25,
  ): Result<RemoteJettonHistoryPage> {
    val specs = JettonAllowList.allFor(ownerAddress.network)
    if (specs.isEmpty()) {
      return Result.success(RemoteJettonHistoryPage(emptyList(), "TON"))
    }
    val allTransfers = mutableListOf<RemoteJettonTransfer>()
    var providerName = "TON"
    var lastError: Throwable? = null
    for (spec in specs) {
      fetchJettonHistory(ownerAddress, spec, limit)
        .onSuccess { page ->
          providerName = page.providerName
          allTransfers.addAll(page.transfers)
        }
        .onFailure { lastError = it }
    }
    return if (allTransfers.isNotEmpty() || lastError == null) {
      Result.success(RemoteJettonHistoryPage(allTransfers, providerName))
    } else {
      Result.failure(lastError!!)
    }
  }
}

interface TonNetworkProvider {
  val name: String

  suspend fun fetchAccountBalance(address: TonAddress): RemoteAccountBalance

  suspend fun fetchJettonBalance(
    ownerAddress: TonAddress,
    spec: AllowedJettonSpec,
  ): RemoteJettonBalance

  suspend fun fetchAllJettons(ownerAddress: TonAddress): RemoteJettonHoldingsPage {
    val specs = JettonAllowList.allFor(ownerAddress.network)
    val holdings =
      specs.map { spec ->
        val bal = fetchJettonBalance(ownerAddress, spec)
        JettonHolding(
          masterAddress = bal.jettonMaster,
          jettonWalletAddress = bal.jettonWalletAddress,
          symbol = spec.symbol,
          name = spec.name,
          balance = bal.balance,
          imageUrl = null,
          verification = JettonVerificationStatus.ALLOWLISTED,
          providerName = bal.providerName,
          fetchedAtEpochMs = bal.fetchedAtEpochMs,
        )
      }
    return RemoteJettonHoldingsPage(
      holdings = holdings,
      providerName = name,
      fetchedAtEpochMs = holdings.maxOfOrNull { it.fetchedAtEpochMs } ?: System.currentTimeMillis(),
    )
  }

  suspend fun fetchWalletInfo(address: TonAddress): RemoteWalletInfo

  suspend fun estimateExternalMessageFee(address: TonAddress, bocBase64: String): RemoteFeeEstimate

  suspend fun sendBoc(network: Network, bocBase64: String): BroadcastResult

  suspend fun lookupMessageStatus(
    network: Network,
    messageHashHex: String,
    normalizedHashHex: String,
  ): RemoteTxLookup

  suspend fun lookupJettonTraceStatus(
    network: Network,
    messageHashHex: String,
    normalizedHashHex: String,
  ): RemoteTxLookup

  suspend fun fetchAccountHistory(
    address: TonAddress,
    afterLt: String? = null,
    limit: Int = 25,
  ): RemoteTxHistoryPage

  suspend fun fetchJettonHistory(
    ownerAddress: TonAddress,
    spec: AllowedJettonSpec,
    limit: Int = 25,
  ): RemoteJettonHistoryPage

  suspend fun fetchAllJettonHistory(
    ownerAddress: TonAddress,
    limit: Int = 25,
  ): RemoteJettonHistoryPage {
    val specs = JettonAllowList.allFor(ownerAddress.network)
    val allTransfers = mutableListOf<RemoteJettonTransfer>()
    for (spec in specs) {
      allTransfers.addAll(fetchJettonHistory(ownerAddress, spec, limit).transfers)
    }
    return RemoteJettonHistoryPage(transfers = allTransfers, providerName = name)
  }
}

sealed class ProviderException(
  message: String,
  val providerName: String,
  val isRetryable: Boolean,
  cause: Throwable? = null,
) : Exception(message, cause)

class RateLimitedException(
  providerName: String,
  val retryAfterMs: Long? = null,
) : ProviderException("$providerName rate limited (HTTP 429)", providerName, isRetryable = true)

class ServerErrorException(
  providerName: String,
  val httpCode: Int,
) : ProviderException("$providerName HTTP error $httpCode", providerName, isRetryable = true)

class RejectedTransactionException(
  providerName: String,
  val httpCode: Int,
  detail: String,
) : ProviderException("$providerName rejected transaction ($httpCode): $detail", providerName, isRetryable = false)

class TransportException(
  providerName: String,
  cause: Throwable,
) : ProviderException("$providerName transport error: ${cause.message}", providerName, isRetryable = true, cause = cause)

class InvalidPayloadException(
  providerName: String,
  detail: String,
  cause: Throwable? = null,
) : ProviderException("$providerName invalid payload: $detail", providerName, isRetryable = true, cause = cause)

class AllProvidersFailedException(
  val failures: List<Throwable>,
) : Exception(
    failures.lastOrNull()?.message ?: "All TON network providers failed",
    failures.lastOrNull(),
  )

data class NetworkEndpoints(
  val mainnetBaseUrl: String,
  val testnetBaseUrl: String,
) {
  fun baseUrlFor(network: Network): String =
    when (network) {
      Network.MAINNET -> mainnetBaseUrl
      Network.TESTNET -> testnetBaseUrl
    }
}
