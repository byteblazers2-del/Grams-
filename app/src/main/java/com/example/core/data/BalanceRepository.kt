package com.example.core.data

import com.example.core.model.ASSET_ID_GRAM
import com.example.core.model.ASSET_ID_USDT
import com.example.core.model.AccountOnChainStatus
import com.example.core.model.BalanceState
import com.example.core.model.JettonAllowList
import com.example.core.model.JettonHolding
import com.example.core.model.JettonVerificationStatus
import com.example.core.model.Money
import com.example.core.model.Network
import com.example.core.model.RemoteAccountBalance
import com.example.core.model.RemoteJettonBalance
import com.example.core.model.RemoteJettonHoldingsPage
import com.example.core.model.TonAddress
import com.example.core.model.USDT_DECIMALS
import com.example.core.network.AllProvidersFailedException
import com.example.core.network.BalanceFetcher
import com.example.core.network.RateLimitedException
import com.example.core.network.ServerErrorException
import com.example.core.network.TransportException
import java.math.BigInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

class BalanceRepository(
  private val balanceDao: BalanceCacheDao,
  private val balanceFetcher: BalanceFetcher,
  private val clockMs: () -> Long = { System.currentTimeMillis() },
  private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
  private val jettonCacheDao: JettonBalanceCacheDao? = null,
) {
  private data class BalanceKey(
    val rawAddress: String,
    val network: Network,
  )

  private data class FetchStatus(
    val isLoading: Boolean = false,
    val lastAttemptFailed: Boolean = false,
    val errorMessage: String? = null,
  )

  private val fetchStatuses = MutableStateFlow<Map<BalanceKey, FetchStatus>>(emptyMap())

  fun observeBalance(address: TonAddress): Flow<BalanceState> {
    val key = BalanceKey(address.raw, address.network)
    val jettonEntitiesFlow =
      jettonCacheDao?.observeHoldings(address.raw, address.network.name) ?: flowOf(emptyList())
    return combine(
      balanceDao.observeBalance(address.raw, address.network.name, ASSET_ID_GRAM),
      balanceDao.observeBalance(address.raw, address.network.name, ASSET_ID_USDT),
      jettonEntitiesFlow,
      fetchStatuses,
    ) { cachedGram, cachedUsdt, cachedJettons, statusMap ->
      val status = statusMap[key] ?: FetchStatus()
      val parsedGram =
        cachedGram?.let {
          runCatching { Money.parseUnits(it.amountUnits, it.decimals) }.getOrNull()
        }
      val legacyUsdt =
        cachedUsdt?.let {
          runCatching { Money.parseUnits(it.amountUnits, USDT_DECIMALS) }.getOrNull()
        }
      val legacyUsdtWallet =
        cachedUsdt?.extraAddress?.let {
          runCatching { TonAddress.parse(it, address.network).getOrNull() }.getOrNull()
        }

      val mappedHoldings =
        cachedJettons
          .mapNotNull { entity -> entity.toDomain(address.network) }
          .filter { holding ->
            holding.verification != JettonVerificationStatus.BLACKLISTED &&
              (holding.balance.units > BigInteger.ZERO || holding.isUsdtAllowlisted)
          }
          .toMutableList()

      val usdtSpec = JettonAllowList.usdtFor(address.network)
      val allowlistedUsdtHolding = mappedHoldings.firstOrNull { it.isUsdtAllowlisted }
      if (allowlistedUsdtHolding == null && usdtSpec != null && legacyUsdt != null) {
        mappedHoldings.add(
          JettonHolding(
            masterAddress = usdtSpec.masterAddress,
            jettonWalletAddress = legacyUsdtWallet,
            symbol = usdtSpec.symbol,
            name = usdtSpec.name,
            balance = legacyUsdt,
            imageUrl = null,
            verification = JettonVerificationStatus.ALLOWLISTED,
            providerName = cachedUsdt.providerName,
            fetchedAtEpochMs = cachedUsdt.fetchedAt,
          )
        )
      }

      val sortedHoldings = sortHoldings(mappedHoldings)
      val effectiveUsdtBalance = allowlistedUsdtHolding?.balance ?: legacyUsdt
      val effectiveUsdtWallet = allowlistedUsdtHolding?.jettonWalletAddress ?: legacyUsdtWallet

      val isExpired =
        cachedGram != null && (clockMs() - cachedGram.fetchedAt) > cacheTtlMs
      val isStale = parsedGram != null && (status.lastAttemptFailed || isExpired)

      BalanceState(
        network = address.network,
        address = address,
        gramBalance = parsedGram,
        usdtBalance = effectiveUsdtBalance,
        usdtJettonWallet = effectiveUsdtWallet,
        jettonHoldings = sortedHoldings,
        lastTxLt = cachedGram?.lastTxLt,
        accountStatus = cachedGram?.let { AccountOnChainStatus.fromWire(it.accountStatus) },
        isLoading = status.isLoading,
        isStale = isStale,
        fetchedAtEpochMs = cachedGram?.fetchedAt,
        providerName = cachedGram?.providerName,
        errorMessage = status.errorMessage,
      )
    }
  }

  suspend fun getCachedLastTxLt(address: TonAddress): String? =
    balanceDao.getBalance(address.raw, address.network.name, ASSET_ID_GRAM)?.lastTxLt

  suspend fun refreshBalance(
    address: TonAddress,
    force: Boolean = false,
  ): Result<RemoteAccountBalance> {
    val key = BalanceKey(address.raw, address.network)
    val cached = balanceDao.getBalance(address.raw, address.network.name, ASSET_ID_GRAM)
    if (!force && cached != null && (clockMs() - cached.fetchedAt) < cacheTtlMs) {
      val cachedMoney = runCatching { Money.parseUnits(cached.amountUnits, cached.decimals) }.getOrNull()
      if (cachedMoney != null) {
        updateStatus(key) { FetchStatus(isLoading = false, lastAttemptFailed = false, errorMessage = null) }
        return Result.success(
          RemoteAccountBalance(
            address = address,
            balance = cachedMoney,
            status = AccountOnChainStatus.fromWire(cached.accountStatus),
            providerName = cached.providerName,
            fetchedAtEpochMs = cached.fetchedAt,
            lastTxLt = cached.lastTxLt,
          )
        )
      }
    }

    updateStatus(key) { current -> current.copy(isLoading = true, errorMessage = null) }

    val result = balanceFetcher.fetchAccountBalance(address)
    result
      .onSuccess { remote ->
        balanceDao.upsert(
          BalanceCacheEntity(
            accountAddr = address.raw,
            network = address.network.name,
            assetId = ASSET_ID_GRAM,
            amountUnits = remote.balance.units.toString(),
            decimals = remote.balance.decimals,
            accountStatus = remote.status.wireValue,
            providerName = remote.providerName,
            fetchedAt = remote.fetchedAtEpochMs,
            lastTxLt = remote.lastTxLt ?: cached?.lastTxLt,
          )
        )
        refreshJettonsInternal(address, remote.status)
        updateStatus(key) {
          FetchStatus(isLoading = false, lastAttemptFailed = false, errorMessage = null)
        }
      }
      .onFailure { err ->
        updateStatus(key) {
          FetchStatus(
            isLoading = false,
            lastAttemptFailed = true,
            errorMessage = sanitizeNetworkError(err),
          )
        }
      }
    return result
  }

  suspend fun refreshAllJettons(address: TonAddress): Result<RemoteJettonHoldingsPage> {
    val result = balanceFetcher.fetchAllJettons(address)
    result.onSuccess { page ->
      val usdtSpec = JettonAllowList.usdtFor(address.network)
      val filteredHoldings =
        page.holdings.filter { holding ->
          holding.verification != JettonVerificationStatus.BLACKLISTED &&
            !JettonAllowList.isLikelyScamMetadata(holding.name, holding.symbol) &&
            (holding.balance.units > BigInteger.ZERO || holding.isUsdtAllowlisted)
        }

      jettonCacheDao?.replaceForAccount(
        accountAddr = address.raw,
        network = address.network.name,
        entities = filteredHoldings.map { it.toEntity(address) },
      )

      if (usdtSpec != null) {
        val usdtHolding = filteredHoldings.firstOrNull { it.isUsdtAllowlisted }
        balanceDao.upsert(
          BalanceCacheEntity(
            accountAddr = address.raw,
            network = address.network.name,
            assetId = ASSET_ID_USDT,
            amountUnits = (usdtHolding?.balance ?: Money.zero(usdtSpec.decimals)).units.toString(),
            decimals = usdtSpec.decimals,
            accountStatus = AccountOnChainStatus.ACTIVE.wireValue,
            providerName = usdtHolding?.providerName ?: page.providerName,
            fetchedAt = usdtHolding?.fetchedAtEpochMs ?: page.fetchedAtEpochMs,
            extraAddress = usdtHolding?.jettonWalletAddress?.raw,
          )
        )
      }
    }
    return result
  }

  suspend fun refreshUsdtBalance(address: TonAddress): Result<RemoteJettonBalance> {
    val spec =
      JettonAllowList.usdtFor(address.network)
        ?: return Result.failure(IllegalArgumentException("USDT is only supported on Mainnet"))
    val result = balanceFetcher.fetchJettonBalance(address, spec)
    result.onSuccess { jettonBal ->
      balanceDao.upsert(
        BalanceCacheEntity(
          accountAddr = address.raw,
          network = address.network.name,
          assetId = ASSET_ID_USDT,
          amountUnits = jettonBal.balance.units.toString(),
          decimals = spec.decimals,
          accountStatus = AccountOnChainStatus.ACTIVE.wireValue,
          providerName = jettonBal.providerName,
          fetchedAt = jettonBal.fetchedAtEpochMs,
          extraAddress = jettonBal.jettonWalletAddress?.raw,
        )
      )
    }
    return result
  }

  private suspend fun refreshJettonsInternal(
    address: TonAddress,
    accountStatus: AccountOnChainStatus,
  ) {
    val spec = JettonAllowList.usdtFor(address.network)
    if (accountStatus == AccountOnChainStatus.NONEXIST) {
      jettonCacheDao?.clearForAccount(address.raw, address.network.name)
      if (spec != null) {
        balanceDao.upsert(
          BalanceCacheEntity(
            accountAddr = address.raw,
            network = address.network.name,
            assetId = ASSET_ID_USDT,
            amountUnits = "0",
            decimals = spec.decimals,
            accountStatus = accountStatus.wireValue,
            providerName = "TON",
            fetchedAt = clockMs(),
            extraAddress = null,
          )
        )
      }
      return
    }
    val allResult = refreshAllJettons(address)
    if (allResult.isFailure && spec != null) {
      refreshUsdtBalance(address)
    }
  }

  suspend fun clearCache() {
    fetchStatuses.value = emptyMap()
    balanceDao.clearAll()
    jettonCacheDao?.clearAll()
  }

  private fun sortHoldings(holdings: List<JettonHolding>): List<JettonHolding> =
    holdings.sortedWith(
      compareBy<JettonHolding> {
        when {
          it.isUsdtAllowlisted -> 0
          it.verification == JettonVerificationStatus.VERIFIED -> 1
          else -> 2
        }
      }
        .thenByDescending { it.balance.units > BigInteger.ZERO }
        .thenBy { it.symbol.lowercase() }
    )

  private fun JettonBalanceCacheEntity.toDomain(network: Network): JettonHolding? {
    val master = TonAddress.parse(masterRaw, network).getOrNull() ?: return null
    val wallet = jettonWalletRaw?.let { TonAddress.parse(it, network).getOrNull() }
    val safeDecimals = decimals.coerceIn(0, 36)
    val parsedBalance = runCatching { Money.parseUnits(amountUnits, safeDecimals) }.getOrNull() ?: return null
    val verificationStatus = JettonVerificationStatus.fromStoredOrDefault(verification)
    return JettonHolding(
      masterAddress = master,
      jettonWalletAddress = wallet,
      symbol = symbol,
      name = name,
      balance = parsedBalance,
      imageUrl = imageUrl,
      verification = verificationStatus,
      providerName = providerName,
      fetchedAtEpochMs = fetchedAt,
    )
  }

  private fun JettonHolding.toEntity(owner: TonAddress): JettonBalanceCacheEntity =
    JettonBalanceCacheEntity(
      accountAddr = owner.raw,
      network = owner.network.name,
      masterRaw = masterAddress.raw,
      jettonWalletRaw = jettonWalletAddress?.raw,
      symbol = symbol,
      name = name,
      decimals = balance.decimals,
      amountUnits = balance.units.toString(),
      imageUrl = imageUrl,
      verification = verification.name,
      providerName = providerName,
      fetchedAt = fetchedAtEpochMs,
    )

  private fun updateStatus(key: BalanceKey, transform: (FetchStatus) -> FetchStatus) {
    fetchStatuses.update { map ->
      val current = map[key] ?: FetchStatus()
      map + (key to transform(current))
    }
  }

  private fun sanitizeNetworkError(error: Throwable): String {
    val root =
      (error as? AllProvidersFailedException)?.failures?.lastOrNull() ?: error
    return when (root) {
      is RateLimitedException -> "Network rate limit reached. Try again shortly."
      is ServerErrorException -> "Network nodes temporarily unavailable."
      is TransportException -> "Connection timed out or offline."
      else -> "Unable to sync balance from TON network."
    }
  }

  companion object {
    const val DEFAULT_CACHE_TTL_MS = 25_000L
  }
}
