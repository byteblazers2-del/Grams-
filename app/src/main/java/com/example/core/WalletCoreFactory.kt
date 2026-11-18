package com.example.core

import android.content.Context
import com.gramvault.wallet.BuildConfig
import com.example.core.data.AddressBookRepository
import com.example.core.data.BalanceRepository
import com.example.core.data.GramVaultDatabase
import com.example.core.keystore.AuthGate
import com.example.core.keystore.SecureKeyStore
import com.example.core.network.ChainTimeOracle
import com.example.core.network.PriceService
import com.example.core.network.ProviderRouter
import com.example.core.network.SwapCoffeeService
import com.example.core.network.SwapEngine
import com.example.core.tx.TxManager
import com.example.core.wallet.AppSettingsStore
import com.example.core.wallet.VaultMetadataStore
import com.example.core.wallet.WalletCore
import com.example.core.wallet.WalletCoreImpl
import com.example.ui.components.isDebugEmulatorEnvironment

data class WalletDependencies(
  val walletCore: WalletCore,
  val addressBookRepository: AddressBookRepository,
  val priceService: PriceService,
  val swapEngine: SwapEngine,
  val appSettingsStore: AppSettingsStore,
  val timeOracle: ChainTimeOracle,
)

object WalletCoreFactory {
  fun create(applicationContext: Context, authGate: AuthGate): WalletCore =
    createDependencies(applicationContext, authGate).walletCore

  fun createDependencies(applicationContext: Context, authGate: AuthGate): WalletDependencies {
    val context = applicationContext.applicationContext
    val isDevEmulator = isDebugEmulatorEnvironment()
    val secureKeyStore = SecureKeyStore(context)
    val metadataStore = VaultMetadataStore(context)
    val database = GramVaultDatabase.getInstance(context)
    val timeOracle = ChainTimeOracle()
    val okHttpClient = ProviderRouter.createDefaultHttpClient(timeOracle = timeOracle)
    val providerRouter = ProviderRouter.createDefault(okHttpClient = okHttpClient)
    val swapEngine = SwapCoffeeService(okHttpClient = okHttpClient)
    val balanceRepository =
      BalanceRepository(
        balanceDao = database.balanceCacheDao(),
        balanceFetcher = providerRouter,
        jettonCacheDao = database.jettonBalanceCacheDao(),
      )
    val txManager =
      TxManager(
        txDao = database.txDao(),
        chainGateway = providerRouter,
        balanceRepository = balanceRepository,
        networkClockMs = { timeOracle.currentNetworkEpochMs() },
      )
    val walletCore =
      WalletCoreImpl(
        applicationContext = context,
        authGate = authGate,
        secureKeyStore = secureKeyStore,
        metadataStore = metadataStore,
        balanceRepository = balanceRepository,
        txManager = txManager,
        swapEngine = swapEngine,
        devSkipAuth = isDevEmulator,
        networkClockMs = { timeOracle.currentNetworkEpochMs() },
      )
    return WalletDependencies(
      walletCore = walletCore,
      addressBookRepository = AddressBookRepository(database.addressBookDao()),
      priceService = PriceService(okHttpClient = okHttpClient),
      swapEngine = swapEngine,
      appSettingsStore = AppSettingsStore(context, defaultAutoLock = !isDevEmulator),
      timeOracle = timeOracle,
    )
  }
}
