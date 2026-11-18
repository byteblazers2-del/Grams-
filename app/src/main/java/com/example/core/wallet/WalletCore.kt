package com.example.core.wallet

import android.content.Context
import com.example.core.data.BalanceRepository
import com.example.core.keystore.AuthGate
import com.example.core.keystore.KeySecurityLevel
import com.example.core.keystore.SecureKeyStore
import com.example.core.keystore.VaultAuthRequiredException
import com.example.core.keystore.VaultKeyInvalidatedException
import com.example.core.keystore.VaultPinLockedOutException
import com.example.core.keystore.VaultWrongPinException
import com.example.core.model.AccountOnChainStatus
import com.example.core.model.AuthReason
import com.example.core.model.AuthResult
import com.example.core.model.BackupChallenge
import com.example.core.model.BalanceState
import com.example.core.model.CandidateAccount
import com.example.core.model.GRAM_DECIMALS
import com.example.core.model.HistorySyncSummary
import com.example.core.model.ImportDiscoveryResult
import com.example.core.model.JettonAllowList
import com.example.core.model.MnemonicPhrase
import com.example.core.model.MnemonicScheme
import com.example.core.model.Money
import com.example.core.model.Network
import com.example.core.model.NewWallet
import com.example.core.model.ParsedAddress
import com.example.core.model.SlippageTolerance
import com.example.core.model.SwapAsset
import com.example.core.model.SwapPreview
import com.example.core.model.SwapQuote
import com.example.core.model.TonAddress
import com.example.core.model.TransferPreview
import com.example.core.model.TxAssetKind
import com.example.core.model.TxDirection
import com.example.core.model.TxRecord
import com.example.core.model.TxStatus
import com.example.core.model.VaultMetadata
import com.example.core.model.VaultSecurityMode
import com.example.core.model.VaultState
import com.example.core.model.WalletVersion
import com.example.core.network.SwapEngine
import com.example.core.tx.TxManager
import java.math.BigInteger
import java.util.Arrays
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed interface AddressCheckResult {
  data class Valid(
    val parsedAddress: ParsedAddress,
    val uriAmount: Money? = null,
    val uriComment: String? = null,
  ) : AddressCheckResult

  data class Invalid(val reason: String) : AddressCheckResult
}

interface WalletCore {
  val vaultState: StateFlow<VaultState>
  val wallets: StateFlow<List<VaultMetadata>>
  val isDevAuthBypassed: Boolean

  fun isDeviceSecure(): Boolean

  fun activeSecurityMode(): VaultSecurityMode

  suspend fun createWallet(
    initialNetwork: Network = Network.MAINNET,
    appPin: CharArray? = null,
  ): Result<NewWallet>

  suspend fun addWatchOnlyWallet(
    addressInput: String,
    customName: String? = null,
  ): Result<VaultMetadata>

  suspend fun switchWallet(
    walletId: String,
    appPin: CharArray? = null,
  ): Result<VaultMetadata>

  suspend fun renameWallet(
    walletId: String,
    newName: String,
  ): Result<VaultMetadata>

  suspend fun deleteWallet(walletId: String): Result<VaultState>

  suspend fun unlockWallet(
    network: Network = Network.MAINNET,
    appPin: CharArray? = null,
  ): Result<VaultMetadata>

  suspend fun revealRecoveryPhrase(appPin: CharArray? = null): Result<MnemonicPhrase>

  fun createBackupChallenge(): BackupChallenge

  suspend fun confirmBackup(
    challenge: BackupChallenge,
    answers: Map<Int, CharArray>,
    appPin: CharArray? = null,
  ): Result<Boolean>

  suspend fun discoverImportAccounts(phrase: MnemonicPhrase): Result<ImportDiscoveryResult>

  suspend fun selectImportedAccount(
    phrase: MnemonicPhrase,
    candidate: CandidateAccount,
    initialNetwork: Network = Network.MAINNET,
    appPin: CharArray? = null,
  ): Result<VaultMetadata>

  suspend fun switchNetwork(network: Network)

  fun lockWallet()

  fun resetVault()

  fun getReceiveAddress(network: Network? = null): TonAddress?

  fun buildReceiveTransferUri(
    network: Network? = null,
    amount: Money? = null,
    comment: String? = null,
  ): String?

  fun validateReceiveInput(input: String, network: Network? = null): AddressCheckResult

  fun observeBalance(): Flow<BalanceState>

  suspend fun refreshBalance(force: Boolean = false)

  suspend fun prepareTransfer(
    recipientInput: String,
    amountText: String,
    comment: String? = null,
    assetKind: TxAssetKind = TxAssetKind.GRAM,
    jettonMasterRaw: String? = null,
  ): Result<TransferPreview>

  suspend fun confirmAndSend(
    preview: TransferPreview,
    appPin: CharArray? = null,
  ): Result<TxRecord>

  fun observeTransactions(): Flow<List<TxRecord>>

  suspend fun recoverPendingTransactions()

  suspend fun refreshTransactionStatus(txId: String): TxRecord?

  fun preferredNetwork(): Network

  fun inspectKeySecurityLevel(): KeySecurityLevel

  suspend fun syncTransactionHistory(
    limit: Int = 25,
    ignoreLtShortcut: Boolean = false,
  ): Result<HistorySyncSummary>

  suspend fun verifyAppPin(appPin: CharArray): Result<Unit>

  suspend fun setAppPin(
    newPin: CharArray,
    currentPin: CharArray? = null,
  ): Result<VaultMetadata>

  suspend fun switchToDeviceBiometrics(currentPin: CharArray? = null): Result<VaultMetadata>

  suspend fun fetchSwapTokens(): List<SwapAsset>

  suspend fun quoteSwap(
    inputAsset: SwapAsset,
    outputAsset: SwapAsset,
    inputAmountText: String,
    slippage: SlippageTolerance,
  ): Result<SwapQuote>

  suspend fun prepareSwapReview(
    quote: SwapQuote,
    slippage: SlippageTolerance,
  ): Result<SwapPreview>

  suspend fun confirmAndExecuteSwap(
    preview: SwapPreview,
    appPin: CharArray? = null,
  ): Result<TxRecord>
}

class WalletCoreImpl(
  applicationContext: Context,
  private val authGate: AuthGate,
  private val secureKeyStore: SecureKeyStore = SecureKeyStore(applicationContext),
  private val metadataStore: VaultMetadataStore = VaultMetadataStore(applicationContext),
  private val balanceRepository: BalanceRepository? = null,
  private val txManager: TxManager? = null,
  private val swapEngine: SwapEngine? = null,
  private val devSkipAuth: Boolean = false,
  private val clockMs: () -> Long = { System.currentTimeMillis() },
  private val networkClockMs: () -> Long = clockMs,
) : WalletCore {

  private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val _wallets = MutableStateFlow(metadataStore.loadAll())
  override val wallets: StateFlow<List<VaultMetadata>> = _wallets.asStateFlow()
  private val _vaultState = MutableStateFlow(loadInitialState())
  override val vaultState: StateFlow<VaultState> = _vaultState.asStateFlow()
  override val isDevAuthBypassed: Boolean
    get() = devSkipAuth

  private fun refreshWalletsFlow() {
    _wallets.value = metadataStore.loadAll()
  }

  private fun loadInitialState(): VaultState {
    val meta = metadataStore.load() ?: return VaultState.Uninitialized
    return if (meta.isWatchOnly || secureKeyStore.hasVault(meta.walletId)) {
      VaultState.Locked(meta)
    } else {
      val updatedRegistry = metadataStore.removeWallet(meta.walletId)
      _wallets.value = updatedRegistry.wallets
      val fallback = metadataStore.load()
      if (fallback != null && (fallback.isWatchOnly || secureKeyStore.hasVault(fallback.walletId))) {
        VaultState.Locked(fallback)
      } else {
        VaultState.Uninitialized
      }
    }
  }

  private fun nextPersonalWalletIdentity(): Pair<String, String> {
    val existing = metadataStore.loadAll()
    if (existing.isEmpty()) {
      return VaultMetadata.LEGACY_WALLET_ID to VaultMetadata.DEFAULT_WALLET_NAME
    }
    val id = "w_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val personalCount = existing.count { !it.isWatchOnly } + 1
    return id to "Wallet $personalCount"
  }

  private fun nextWatchWalletName(customName: String?): String {
    val trimmed = customName?.trim()?.take(32).orEmpty()
    if (trimmed.isNotEmpty()) return trimmed
    val watchCount = metadataStore.loadAll().count { it.isWatchOnly } + 1
    return if (watchCount == 1) "Watch Wallet" else "Watch Wallet $watchCount"
  }

  override fun isDeviceSecure(): Boolean = authGate.isDeviceSecure()

  override fun inspectKeySecurityLevel(): KeySecurityLevel {
    val meta =
      when (val current = _vaultState.value) {
        is VaultState.Unlocked -> current.metadata
        is VaultState.Locked -> current.metadata
        is VaultState.KeyInvalidated -> current.metadata
        VaultState.Uninitialized -> return KeySecurityLevel.UNKNOWN
      }
    if (meta.isWatchOnly) return KeySecurityLevel.UNKNOWN
    return secureKeyStore.inspectVaultKeySecurityLevel(meta.securityMode, meta.walletId)
  }

  override fun activeSecurityMode(): VaultSecurityMode {
    val existing = metadataStore.load()?.securityMode
    if (existing != null) return existing
    if (devSkipAuth) return VaultSecurityMode.DEV_UNAUTH_KEYSTORE
    return if (authGate.isDeviceSecure()) VaultSecurityMode.DEVICE_KEYSTORE
    else VaultSecurityMode.APP_PIN_WITH_KEYSTORE
  }

  override suspend fun createWallet(initialNetwork: Network, appPin: CharArray?): Result<NewWallet> {
    if (initialNetwork != Network.MAINNET) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("GramVault supports Mainnet only"))
    }
    val targetMode =
      when {
        devSkipAuth -> VaultSecurityMode.DEV_UNAUTH_KEYSTORE
        authGate.isDeviceSecure() -> VaultSecurityMode.DEVICE_KEYSTORE
        else -> VaultSecurityMode.APP_PIN_WITH_KEYSTORE
      }

    when (targetMode) {
      VaultSecurityMode.DEV_UNAUTH_KEYSTORE -> Unit
      VaultSecurityMode.DEVICE_KEYSTORE -> {
        when (val auth = authGate.authenticate(AuthReason.CREATE_WALLET)) {
          is AuthResult.Authenticated -> Unit
          is AuthResult.Cancelled -> {
            appPin?.let { Arrays.fill(it, '\u0000') }
            return Result.failure(VaultException("Authentication cancelled"))
          }
          is AuthResult.NoDeviceSecurity ->
            return Result.failure(VaultException("Device lock screen is required"))
          is AuthResult.Failed -> {
            appPin?.let { Arrays.fill(it, '\u0000') }
            return Result.failure(VaultException(auth.message))
          }
        }
      }
      VaultSecurityMode.APP_PIN_WITH_KEYSTORE -> {
        if (!SecureKeyStore.isValidPinFormat(appPin)) {
          appPin?.let { Arrays.fill(it, '\u0000') }
          return Result.failure(VaultException("Set a 6 to 8 digit App PIN to protect your wallet"))
        }
      }
    }

    return runCatching {
      val (newWalletId, newWalletName) = nextPersonalWalletIdentity()
      val mnemonic = withContext(Dispatchers.Default) { MnemonicService.generate12WordMnemonic() }
      val derived = withContext(Dispatchers.Default) { MnemonicService.deriveMultichainKey(mnemonic) }
      val mnemonicUtf8 = MnemonicService.encodePhraseToUtf8(mnemonic)
      try {
        val mainnetAddr = AddressService.deriveV5R1Address(derived.publicKey32, Network.MAINNET)
        val testnetAddr = AddressService.deriveV5R1Address(derived.publicKey32, Network.TESTNET)
        val pubHex = AddressService.bytesToHex(derived.publicKey32)

        withContext(Dispatchers.IO) {
          when (targetMode) {
            VaultSecurityMode.DEVICE_KEYSTORE ->
              secureKeyStore.saveWithDeviceAuth(derived.seed32, mnemonicUtf8, newWalletId)
            VaultSecurityMode.APP_PIN_WITH_KEYSTORE ->
              secureKeyStore.saveWithAppPin(derived.seed32, mnemonicUtf8, appPin!!, newWalletId)
            VaultSecurityMode.DEV_UNAUTH_KEYSTORE ->
              secureKeyStore.saveWithoutUserAuth(derived.seed32, mnemonicUtf8, newWalletId)
          }
        }
        val meta =
          VaultMetadata(
            publicKeyHex = pubHex,
            mainnetRaw = mainnetAddr.raw,
            testnetRaw = testnetAddr.raw,
            isBackedUp = false,
            createdAtEpochMs = clockMs(),
            securityMode = targetMode,
            walletVersion = WalletVersion.V5R1,
            mnemonicScheme = MnemonicScheme.MULTICHAIN,
            lastActiveNetwork = initialNetwork,
            walletId = newWalletId,
            walletName = newWalletName,
            isWatchOnly = false,
          )
        withContext(Dispatchers.IO) { metadataStore.save(meta) }
        refreshWalletsFlow()

        _vaultState.value =
          VaultState.Unlocked(
            metadata = meta,
            activeNetwork = initialNetwork,
          )
        NewWallet(
          mainnetAddress = mainnetAddr,
          testnetAddress = testnetAddr,
          publicKeyHex = pubHex,
          mnemonic = mnemonic,
        )
      } catch (t: Throwable) {
        mnemonic.wipe()
        throw t
      } finally {
        derived.wipe()
        Arrays.fill(mnemonicUtf8, 0.toByte())
        appPin?.let { Arrays.fill(it, '\u0000') }
      }
    }
  }

  override suspend fun discoverImportAccounts(phrase: MnemonicPhrase): Result<ImportDiscoveryResult> {
    if (phrase.wordCount != 12 && phrase.wordCount != 24) {
      return Result.failure(VaultException("Recovery phrase must contain 12 or 24 words"))
    }
    return runCatching {
      withContext(Dispatchers.Default) {
        val schemes = MnemonicService.detectValidSchemes(phrase)
        if (schemes.isEmpty()) {
          throw VaultException("Invalid recovery phrase words or checksum")
        }
        val candidates =
          schemes.flatMap { scheme ->
            val derived = MnemonicService.deriveKeyForScheme(phrase, scheme)
            try {
              AddressService.discoverCandidateAccounts(derived.publicKey32, scheme)
            } finally {
              derived.wipe()
            }
          }
        ImportDiscoveryResult(
          candidates = candidates,
          isAmbiguous24 = schemes.size > 1,
        )
      }
    }
  }

  override suspend fun selectImportedAccount(
    phrase: MnemonicPhrase,
    candidate: CandidateAccount,
    initialNetwork: Network,
    appPin: CharArray?,
  ): Result<VaultMetadata> {
    if (initialNetwork != Network.MAINNET) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("GramVault supports Mainnet only"))
    }
    val targetMode =
      when {
        devSkipAuth -> VaultSecurityMode.DEV_UNAUTH_KEYSTORE
        authGate.isDeviceSecure() -> VaultSecurityMode.DEVICE_KEYSTORE
        else -> VaultSecurityMode.APP_PIN_WITH_KEYSTORE
      }

    when (targetMode) {
      VaultSecurityMode.DEV_UNAUTH_KEYSTORE -> Unit
      VaultSecurityMode.DEVICE_KEYSTORE -> {
        when (val auth = authGate.authenticate(AuthReason.IMPORT_WALLET)) {
          is AuthResult.Authenticated -> Unit
          is AuthResult.Cancelled -> {
            appPin?.let { Arrays.fill(it, '\u0000') }
            return Result.failure(VaultException("Authentication cancelled"))
          }
          is AuthResult.NoDeviceSecurity ->
            return Result.failure(VaultException("Device lock screen is required"))
          is AuthResult.Failed -> {
            appPin?.let { Arrays.fill(it, '\u0000') }
            return Result.failure(VaultException(auth.message))
          }
        }
      }
      VaultSecurityMode.APP_PIN_WITH_KEYSTORE -> {
        if (!SecureKeyStore.isValidPinFormat(appPin)) {
          appPin?.let { Arrays.fill(it, '\u0000') }
          return Result.failure(VaultException("Set a 6 to 8 digit App PIN to protect your wallet"))
        }
      }
    }

    return runCatching {
      val derived =
        withContext(Dispatchers.Default) { MnemonicService.deriveKeyForScheme(phrase, candidate.scheme) }
      val mnemonicUtf8 = MnemonicService.encodePhraseToUtf8(phrase)
      try {
        val (newWalletId, newWalletName) = nextPersonalWalletIdentity()
        val pubHex = AddressService.bytesToHex(derived.publicKey32)
        require(pubHex == candidate.publicKeyHex) { "Selected account key mismatch" }
        val mainnetAddr = AddressService.deriveAddress(derived.publicKey32, candidate.version, Network.MAINNET)
        val testnetAddr = AddressService.deriveAddress(derived.publicKey32, candidate.version, Network.TESTNET)

        withContext(Dispatchers.IO) {
          when (targetMode) {
            VaultSecurityMode.DEVICE_KEYSTORE ->
              secureKeyStore.saveWithDeviceAuth(derived.seed32, mnemonicUtf8, newWalletId)
            VaultSecurityMode.APP_PIN_WITH_KEYSTORE ->
              secureKeyStore.saveWithAppPin(derived.seed32, mnemonicUtf8, appPin!!, newWalletId)
            VaultSecurityMode.DEV_UNAUTH_KEYSTORE ->
              secureKeyStore.saveWithoutUserAuth(derived.seed32, mnemonicUtf8, newWalletId)
          }
        }
        val meta =
          VaultMetadata(
            publicKeyHex = pubHex,
            mainnetRaw = mainnetAddr.raw,
            testnetRaw = testnetAddr.raw,
            isBackedUp = true,
            createdAtEpochMs = clockMs(),
            securityMode = targetMode,
            walletVersion = candidate.version,
            mnemonicScheme = candidate.scheme,
            lastActiveNetwork = initialNetwork,
            walletId = newWalletId,
            walletName = newWalletName,
            isWatchOnly = false,
          )
        withContext(Dispatchers.IO) { metadataStore.save(meta) }
        refreshWalletsFlow()

        _vaultState.value =
          VaultState.Unlocked(
            metadata = meta,
            activeNetwork = initialNetwork,
          )
        meta
      } finally {
        derived.wipe()
        Arrays.fill(mnemonicUtf8, 0.toByte())
        appPin?.let { Arrays.fill(it, '\u0000') }
      }
    }
  }

  override suspend fun addWatchOnlyWallet(
    addressInput: String,
    customName: String?,
  ): Result<VaultMetadata> {
    val checked = validateReceiveInput(addressInput, Network.MAINNET)
    if (checked is AddressCheckResult.Invalid) {
      return Result.failure(VaultException(checked.reason))
    }
    val validAddress = (checked as AddressCheckResult.Valid).parsedAddress.address
    val existing = metadataStore.loadAll()
    val duplicate =
      existing.firstOrNull { it.isWatchOnly && it.mainnetRaw == validAddress.raw }
    if (duplicate != null) {
      return Result.failure(
        VaultException("Watch-only address is already tracked as \"${duplicate.walletName}\"")
      )
    }

    val walletId = "w_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val walletName = nextWatchWalletName(customName)
    val meta =
      VaultMetadata(
        publicKeyHex = "",
        mainnetRaw = validAddress.raw,
        testnetRaw = TonAddress(validAddress.raw, Network.TESTNET).raw,
        isBackedUp = true,
        createdAtEpochMs = clockMs(),
        securityMode =
          if (devSkipAuth) VaultSecurityMode.DEV_UNAUTH_KEYSTORE
          else VaultSecurityMode.DEVICE_KEYSTORE,
        walletVersion = WalletVersion.V5R1,
        mnemonicScheme = MnemonicScheme.MULTICHAIN,
        lastActiveNetwork = Network.MAINNET,
        walletId = walletId,
        walletName = walletName,
        isWatchOnly = true,
      )
    withContext(Dispatchers.IO) {
      secureKeyStore.clearPinSession()
      metadataStore.save(meta)
    }
    refreshWalletsFlow()
    _vaultState.value = VaultState.Unlocked(metadata = meta, activeNetwork = Network.MAINNET)
    return Result.success(meta)
  }

  override suspend fun switchWallet(walletId: String, appPin: CharArray?): Result<VaultMetadata> {
    val target =
      metadataStore.loadAll().firstOrNull { it.walletId == walletId }
        ?: run {
          appPin?.let { Arrays.fill(it, '\u0000') }
          return Result.failure(VaultException("Selected wallet was not found"))
        }

    val unlockedResult = unlockSpecificWallet(target, Network.MAINNET, appPin, persistActiveSelection = true)
    return unlockedResult
  }

  override suspend fun renameWallet(walletId: String, newName: String): Result<VaultMetadata> {
    val cleanName = newName.trim().take(32)
    if (cleanName.isEmpty()) {
      return Result.failure(VaultException("Enter a valid wallet name"))
    }
    val existing =
      metadataStore.loadAll().firstOrNull { it.walletId == walletId }
        ?: return Result.failure(VaultException("Wallet not found"))
    val updated = existing.copy(walletName = cleanName)
    withContext(Dispatchers.IO) { metadataStore.updateMetadataPreservingActive(updated) }
    refreshWalletsFlow()
    val currentState = _vaultState.value
    if (currentState is VaultState.Unlocked && currentState.metadata.walletId == walletId) {
      _vaultState.value = currentState.copy(metadata = updated)
    } else if (currentState is VaultState.Locked && currentState.metadata.walletId == walletId) {
      _vaultState.value = VaultState.Locked(updated)
    } else if (currentState is VaultState.KeyInvalidated && currentState.metadata.walletId == walletId) {
      _vaultState.value = VaultState.KeyInvalidated(updated)
    }
    return Result.success(updated)
  }

  override suspend fun deleteWallet(walletId: String): Result<VaultState> {
    val existing = metadataStore.loadAll()
    val target =
      existing.firstOrNull { it.walletId == walletId }
        ?: return Result.failure(VaultException("Wallet not found"))
    if (existing.size <= 1) {
      resetVault()
      return Result.success(VaultState.Uninitialized)
    }

    val currentlyUnlockedId = (_vaultState.value as? VaultState.Unlocked)?.metadata?.walletId
    val updatedRegistry =
      withContext(Dispatchers.IO) {
        if (!target.isWatchOnly) {
          secureKeyStore.clearWalletVault(target.walletId)
        }
        metadataStore.removeWallet(target.walletId)
      }
    refreshWalletsFlow()

    val nextActive =
      updatedRegistry.wallets.firstOrNull { it.walletId == updatedRegistry.activeWalletId }
        ?: updatedRegistry.wallets.firstOrNull()

    val nextState =
      when {
        nextActive == null -> VaultState.Uninitialized
        currentlyUnlockedId != null && currentlyUnlockedId != target.walletId && nextActive.walletId == currentlyUnlockedId ->
          VaultState.Unlocked(metadata = nextActive, activeNetwork = Network.MAINNET)
        nextActive.isWatchOnly || devSkipAuth ->
          VaultState.Unlocked(metadata = nextActive, activeNetwork = Network.MAINNET)
        else -> VaultState.Locked(nextActive)
      }
    _vaultState.value = nextState
    return Result.success(nextState)
  }

  override suspend fun unlockWallet(network: Network, appPin: CharArray?): Result<VaultMetadata> {
    if (network != Network.MAINNET) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("GramVault supports Mainnet only"))
    }
    val currentMeta = metadataStore.load()
    if (currentMeta == null) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("No wallet exists on this device"))
    }
    return unlockSpecificWallet(currentMeta, network, appPin, persistActiveSelection = false)
  }

  private suspend fun unlockSpecificWallet(
    targetMeta: VaultMetadata,
    network: Network,
    appPin: CharArray?,
    persistActiveSelection: Boolean,
  ): Result<VaultMetadata> {
    if (targetMeta.isWatchOnly) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      secureKeyStore.clearPinSession()
      if (persistActiveSelection) {
        withContext(Dispatchers.IO) { metadataStore.setActiveWallet(targetMeta.walletId) }
        refreshWalletsFlow()
      }
      _vaultState.value = VaultState.Unlocked(metadata = targetMeta, activeNetwork = network)
      return Result.success(targetMeta)
    }

    when (targetMeta.securityMode) {
      VaultSecurityMode.DEV_UNAUTH_KEYSTORE -> {
        if (!devSkipAuth) {
          appPin?.let { Arrays.fill(it, '\u0000') }
          return Result.failure(VaultException("Development vault cannot be unlocked in release build"))
        }
      }
      VaultSecurityMode.DEVICE_KEYSTORE -> {
        when (val auth = authGate.authenticate(AuthReason.UNLOCK_WALLET)) {
          is AuthResult.Authenticated -> Unit
          is AuthResult.Cancelled -> return Result.failure(VaultException("Authentication cancelled"))
          is AuthResult.NoDeviceSecurity ->
            return Result.failure(VaultException("Device lock screen is required"))
          is AuthResult.Failed -> return Result.failure(VaultException(auth.message))
        }
      }
      VaultSecurityMode.APP_PIN_WITH_KEYSTORE -> {
        if (appPin == null) {
          return Result.failure(VaultAuthRequiredException())
        }
        if (!SecureKeyStore.isValidPinFormat(appPin)) {
          Arrays.fill(appPin, '\u0000')
          return Result.failure(VaultException("Wallet PIN must be 6 to 8 digits"))
        }
      }
    }

    val useAppPin = targetMeta.securityMode == VaultSecurityMode.APP_PIN_WITH_KEYSTORE
    val requireUserAuth = targetMeta.securityMode != VaultSecurityMode.DEV_UNAUTH_KEYSTORE

    return try {
      val derivedPubHex =
        withContext(Dispatchers.IO) {
          secureKeyStore.withDecryptedSeed(
            pin = appPin,
            useAppPinMode = useAppPin,
            requireUserAuth = requireUserAuth,
            walletId = targetMeta.walletId,
          ) { seed32 ->
            val pub = org.ton.kotlin.crypto.PrivateKeyEd25519(seed32).publicKey().key.toByteArray()
            AddressService.bytesToHex(pub)
          }
        }
      require(derivedPubHex == targetMeta.publicKeyHex) { "Key mismatch detected in vault" }
      if (persistActiveSelection) {
        withContext(Dispatchers.IO) { metadataStore.setActiveWallet(targetMeta.walletId) }
        refreshWalletsFlow()
      }
      _vaultState.value =
        VaultState.Unlocked(
          metadata = targetMeta,
          activeNetwork = network,
        )
      cleanupScope.launch { txManager?.recoverPendingTransactions(targetMeta.addressFor(network)) }
      Result.success(targetMeta)
    } catch (e: VaultKeyInvalidatedException) {
      if (!persistActiveSelection) {
        _vaultState.value = VaultState.KeyInvalidated(targetMeta)
      }
      Result.failure(e)
    } catch (e: VaultPinLockedOutException) {
      Result.failure(e)
    } catch (e: VaultWrongPinException) {
      Result.failure(e)
    } catch (e: VaultAuthRequiredException) {
      if (!persistActiveSelection) {
        _vaultState.value = VaultState.Locked(targetMeta)
      }
      Result.failure(e)
    } catch (t: Throwable) {
      Result.failure(t)
    } finally {
      appPin?.let { Arrays.fill(it, '\u0000') }
    }
  }

  override suspend fun revealRecoveryPhrase(appPin: CharArray?): Result<MnemonicPhrase> {
    val current = _vaultState.value as? VaultState.Unlocked
    if (current == null) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Unlock your wallet first"))
    }
    if (current.metadata.isWatchOnly) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Watch-only wallet has no recovery phrase"))
    }
    when (current.metadata.securityMode) {
      VaultSecurityMode.DEV_UNAUTH_KEYSTORE -> {
        if (!devSkipAuth) {
          appPin?.let { Arrays.fill(it, '\u0000') }
          return Result.failure(VaultException("Development vault cannot be accessed in release build"))
        }
      }
      VaultSecurityMode.DEVICE_KEYSTORE -> {
        when (val auth = authGate.authenticate(AuthReason.VIEW_RECOVERY_PHRASE)) {
          is AuthResult.Authenticated -> Unit
          is AuthResult.Cancelled -> return Result.failure(VaultException("Authentication cancelled"))
          is AuthResult.NoDeviceSecurity ->
            return Result.failure(VaultException("Device lock screen is required"))
          is AuthResult.Failed -> return Result.failure(VaultException(auth.message))
        }
      }
      VaultSecurityMode.APP_PIN_WITH_KEYSTORE -> {
        if (appPin == null) {
          return Result.failure(VaultAuthRequiredException())
        }
        if (!SecureKeyStore.isValidPinFormat(appPin)) {
          Arrays.fill(appPin, '\u0000')
          return Result.failure(VaultException("Wallet PIN must be 6 to 8 digits"))
        }
      }
    }

    val useAppPin = current.metadata.securityMode == VaultSecurityMode.APP_PIN_WITH_KEYSTORE
    val requireUserAuth = current.metadata.securityMode != VaultSecurityMode.DEV_UNAUTH_KEYSTORE

    return try {
      val phrase =
        withContext(Dispatchers.IO) {
          secureKeyStore.withDecryptedMnemonic(
            pin = appPin,
            useAppPinMode = useAppPin,
            requireUserAuth = requireUserAuth,
            walletId = current.metadata.walletId,
          ) { utf8 ->
            MnemonicService.decodePhraseFromUtf8(utf8)
          }
        }
      Result.success(phrase)
    } catch (e: VaultKeyInvalidatedException) {
      _vaultState.value = VaultState.KeyInvalidated(current.metadata)
      Result.failure(e)
    } catch (t: Throwable) {
      Result.failure(t)
    } finally {
      appPin?.let { Arrays.fill(it, '\u0000') }
    }
  }

  override fun createBackupChallenge(): BackupChallenge =
    MnemonicService.createBackupChallenge(wordCount = 12, sampleCount = 3)

  override suspend fun confirmBackup(
    challenge: BackupChallenge,
    answers: Map<Int, CharArray>,
    appPin: CharArray?,
  ): Result<Boolean> {
    val current = _vaultState.value as? VaultState.Unlocked
    if (current == null) {
      answers.values.forEach { a -> Arrays.fill(a, '\u0000') }
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Unlock your wallet first"))
    }
    if (current.metadata.isWatchOnly) {
      answers.values.forEach { a -> Arrays.fill(a, '\u0000') }
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Watch-only wallet has no recovery phrase"))
    }
    if (current.metadata.securityMode == VaultSecurityMode.DEV_UNAUTH_KEYSTORE && !devSkipAuth) {
      answers.values.forEach { a -> Arrays.fill(a, '\u0000') }
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Development vault cannot be accessed in release build"))
    }
    val useAppPin = current.metadata.securityMode == VaultSecurityMode.APP_PIN_WITH_KEYSTORE
    val requireUserAuth = current.metadata.securityMode != VaultSecurityMode.DEV_UNAUTH_KEYSTORE
    if (useAppPin && appPin != null && !SecureKeyStore.isValidPinFormat(appPin)) {
      answers.values.forEach { a -> Arrays.fill(a, '\u0000') }
      Arrays.fill(appPin, '\u0000')
      return Result.failure(VaultException("Wallet PIN must be 6 to 8 digits"))
    }

    return try {
      val passed =
        try {
          withContext(Dispatchers.IO) {
            secureKeyStore.withDecryptedMnemonic(
              pin = appPin,
              useAppPinMode = useAppPin,
              requireUserAuth = requireUserAuth,
              walletId = current.metadata.walletId,
            ) { utf8 ->
              val phrase = MnemonicService.decodePhraseFromUtf8(utf8)
              try {
                MnemonicService.verifyBackupChallenge(phrase, challenge, answers)
              } finally {
                phrase.wipe()
              }
            }
          }
        } catch (e: VaultAuthRequiredException) {
          if (useAppPin || !requireUserAuth) throw e
          when (val auth = authGate.authenticate(AuthReason.VIEW_RECOVERY_PHRASE)) {
            is AuthResult.Authenticated -> {
              withContext(Dispatchers.IO) {
                secureKeyStore.withDecryptedMnemonic(
                  pin = null,
                  useAppPinMode = false,
                  requireUserAuth = true,
                  walletId = current.metadata.walletId,
                ) { utf8 ->
                  val phrase = MnemonicService.decodePhraseFromUtf8(utf8)
                  try {
                    MnemonicService.verifyBackupChallenge(phrase, challenge, answers)
                  } finally {
                    phrase.wipe()
                  }
                }
              }
            }
            is AuthResult.Cancelled -> return Result.failure(VaultException("Authentication cancelled"))
            is AuthResult.NoDeviceSecurity ->
              return Result.failure(VaultException("Device lock screen is required"))
            is AuthResult.Failed -> return Result.failure(VaultException(auth.message))
          }
        }

      if (passed) {
        val updatedMeta = current.metadata.copy(isBackedUp = true)
        withContext(Dispatchers.IO) { metadataStore.save(updatedMeta) }
        refreshWalletsFlow()
        _vaultState.value = current.copy(metadata = updatedMeta)
      }
      Result.success(passed)
    } catch (e: VaultKeyInvalidatedException) {
      _vaultState.value = VaultState.KeyInvalidated(current.metadata)
      Result.failure(e)
    } catch (t: Throwable) {
      Result.failure(t)
    } finally {
      answers.values.forEach { Arrays.fill(it, '\u0000') }
      appPin?.let { Arrays.fill(it, '\u0000') }
    }
  }

  override suspend fun verifyAppPin(appPin: CharArray): Result<Unit> {
    val current = _vaultState.value as? VaultState.Unlocked
    if (current == null) {
      Arrays.fill(appPin, '\u0000')
      return Result.failure(VaultException("Unlock your wallet first"))
    }
    if (current.metadata.isWatchOnly) {
      Arrays.fill(appPin, '\u0000')
      return Result.failure(VaultException("Watch-only wallet does not use a PIN"))
    }
    if (current.metadata.securityMode != VaultSecurityMode.APP_PIN_WITH_KEYSTORE) {
      Arrays.fill(appPin, '\u0000')
      return Result.failure(VaultException("Vault is not using an App PIN"))
    }
    if (!SecureKeyStore.isValidPinFormat(appPin)) {
      Arrays.fill(appPin, '\u0000')
      return Result.failure(VaultException("Wallet PIN must be 6 to 8 digits"))
    }
    return try {
      withContext(Dispatchers.IO) {
        secureKeyStore.withDecryptedSeed(
          pin = appPin,
          useAppPinMode = true,
          requireUserAuth = true,
          walletId = current.metadata.walletId,
        ) { }
      }
      Result.success(Unit)
    } catch (e: VaultKeyInvalidatedException) {
      _vaultState.value = VaultState.KeyInvalidated(current.metadata)
      Result.failure(e)
    } catch (t: Throwable) {
      Result.failure(t)
    } finally {
      Arrays.fill(appPin, '\u0000')
    }
  }

  override suspend fun setAppPin(
    newPin: CharArray,
    currentPin: CharArray?,
  ): Result<VaultMetadata> {
    val current = _vaultState.value as? VaultState.Unlocked
    if (current == null) {
      Arrays.fill(newPin, '\u0000')
      currentPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Unlock your wallet first"))
    }
    if (current.metadata.isWatchOnly) {
      Arrays.fill(newPin, '\u0000')
      currentPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Watch-only wallet does not use a PIN"))
    }
    if (!SecureKeyStore.isValidPinFormat(newPin)) {
      Arrays.fill(newPin, '\u0000')
      currentPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Wallet PIN must be 6 to 8 digits"))
    }
    when (current.metadata.securityMode) {
      VaultSecurityMode.DEV_UNAUTH_KEYSTORE -> {
        if (!devSkipAuth) {
          Arrays.fill(newPin, '\u0000')
          currentPin?.let { Arrays.fill(it, '\u0000') }
          return Result.failure(VaultException("Development vault cannot be modified in release build"))
        }
      }
      VaultSecurityMode.DEVICE_KEYSTORE -> {
        when (val auth = authGate.authenticate(AuthReason.CHANGE_SECURITY)) {
          is AuthResult.Authenticated -> Unit
          is AuthResult.Cancelled -> {
            Arrays.fill(newPin, '\u0000')
            currentPin?.let { Arrays.fill(it, '\u0000') }
            return Result.failure(VaultException("Authentication cancelled"))
          }
          is AuthResult.NoDeviceSecurity -> {
            Arrays.fill(newPin, '\u0000')
            currentPin?.let { Arrays.fill(it, '\u0000') }
            return Result.failure(VaultException("Device lock screen is required"))
          }
          is AuthResult.Failed -> {
            Arrays.fill(newPin, '\u0000')
            currentPin?.let { Arrays.fill(it, '\u0000') }
            return Result.failure(VaultException(auth.message))
          }
        }
      }
      VaultSecurityMode.APP_PIN_WITH_KEYSTORE -> {
        if (currentPin == null) {
          Arrays.fill(newPin, '\u0000')
          return Result.failure(VaultAuthRequiredException())
        }
        if (!SecureKeyStore.isValidPinFormat(currentPin)) {
          Arrays.fill(newPin, '\u0000')
          Arrays.fill(currentPin, '\u0000')
          return Result.failure(VaultException("Wallet PIN must be 6 to 8 digits"))
        }
      }
    }

    val useCurrentAppPin = current.metadata.securityMode == VaultSecurityMode.APP_PIN_WITH_KEYSTORE
    val requireUserAuth = current.metadata.securityMode != VaultSecurityMode.DEV_UNAUTH_KEYSTORE

    return try {
      withContext(Dispatchers.IO) {
        secureKeyStore.withDecryptedVaultSecrets(
          pin = currentPin,
          useAppPinMode = useCurrentAppPin,
          requireUserAuth = requireUserAuth,
          walletId = current.metadata.walletId,
        ) { seed32, mnemonicUtf8 ->
          secureKeyStore.saveWithAppPin(seed32, mnemonicUtf8, newPin, current.metadata.walletId)
        }
      }
      val updatedMeta = current.metadata.copy(securityMode = VaultSecurityMode.APP_PIN_WITH_KEYSTORE)
      withContext(Dispatchers.IO) { metadataStore.save(updatedMeta) }
      refreshWalletsFlow()
      _vaultState.value = current.copy(metadata = updatedMeta)
      Result.success(updatedMeta)
    } catch (e: VaultKeyInvalidatedException) {
      _vaultState.value = VaultState.KeyInvalidated(current.metadata)
      Result.failure(e)
    } catch (t: Throwable) {
      Result.failure(t)
    } finally {
      Arrays.fill(newPin, '\u0000')
      currentPin?.let { Arrays.fill(it, '\u0000') }
    }
  }

  override suspend fun switchToDeviceBiometrics(currentPin: CharArray?): Result<VaultMetadata> {
    val current = _vaultState.value as? VaultState.Unlocked
    if (current == null) {
      currentPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Unlock your wallet first"))
    }
    if (current.metadata.isWatchOnly) {
      currentPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Watch-only wallet does not use device key encryption"))
    }
    if (!authGate.isDeviceSecure()) {
      currentPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Enable a screen lock or biometric in Android Settings first"))
    }
    when (current.metadata.securityMode) {
      VaultSecurityMode.DEV_UNAUTH_KEYSTORE -> {
        if (!devSkipAuth) {
          currentPin?.let { Arrays.fill(it, '\u0000') }
          return Result.failure(VaultException("Development vault cannot be modified in release build"))
        }
      }
      VaultSecurityMode.APP_PIN_WITH_KEYSTORE -> {
        if (currentPin == null) {
          return Result.failure(VaultAuthRequiredException())
        }
        if (!SecureKeyStore.isValidPinFormat(currentPin)) {
          Arrays.fill(currentPin, '\u0000')
          return Result.failure(VaultException("Wallet PIN must be 6 to 8 digits"))
        }
        try {
          withContext(Dispatchers.IO) {
            secureKeyStore.withDecryptedSeed(
              pin = currentPin,
              useAppPinMode = true,
              requireUserAuth = true,
              walletId = current.metadata.walletId,
            ) { }
          }
        } catch (e: VaultKeyInvalidatedException) {
          Arrays.fill(currentPin, '\u0000')
          _vaultState.value = VaultState.KeyInvalidated(current.metadata)
          return Result.failure(e)
        } catch (t: Throwable) {
          Arrays.fill(currentPin, '\u0000')
          return Result.failure(t)
        }
      }
      VaultSecurityMode.DEVICE_KEYSTORE -> Unit
    }

    when (val auth = authGate.authenticate(AuthReason.CHANGE_SECURITY)) {
      is AuthResult.Authenticated -> Unit
      is AuthResult.Cancelled -> {
        currentPin?.let { Arrays.fill(it, '\u0000') }
        return Result.failure(VaultException("Authentication cancelled"))
      }
      is AuthResult.NoDeviceSecurity -> {
        currentPin?.let { Arrays.fill(it, '\u0000') }
        return Result.failure(VaultException("Enable a screen lock or biometric in Android Settings first"))
      }
      is AuthResult.Failed -> {
        currentPin?.let { Arrays.fill(it, '\u0000') }
        return Result.failure(VaultException(auth.message))
      }
    }

    val useCurrentAppPin = current.metadata.securityMode == VaultSecurityMode.APP_PIN_WITH_KEYSTORE
    val requireUserAuth = current.metadata.securityMode != VaultSecurityMode.DEV_UNAUTH_KEYSTORE

    return try {
      withContext(Dispatchers.IO) {
        secureKeyStore.withDecryptedVaultSecrets(
          pin = currentPin,
          useAppPinMode = useCurrentAppPin,
          requireUserAuth = requireUserAuth,
          walletId = current.metadata.walletId,
        ) { seed32, mnemonicUtf8 ->
          secureKeyStore.saveWithDeviceAuth(seed32, mnemonicUtf8, current.metadata.walletId)
        }
        secureKeyStore.clearPinSession()
      }
      val updatedMeta = current.metadata.copy(securityMode = VaultSecurityMode.DEVICE_KEYSTORE)
      withContext(Dispatchers.IO) { metadataStore.save(updatedMeta) }
      refreshWalletsFlow()
      _vaultState.value = current.copy(metadata = updatedMeta)
      Result.success(updatedMeta)
    } catch (e: VaultKeyInvalidatedException) {
      _vaultState.value = VaultState.KeyInvalidated(current.metadata)
      Result.failure(e)
    } catch (t: Throwable) {
      Result.failure(t)
    } finally {
      currentPin?.let { Arrays.fill(it, '\u0000') }
    }
  }

  override suspend fun switchNetwork(network: Network) {
    if (network != Network.MAINNET) return
    val current = _vaultState.value as? VaultState.Unlocked ?: return
    val updatedMeta =
      if (current.metadata.lastActiveNetwork != Network.MAINNET) {
        current.metadata.copy(lastActiveNetwork = Network.MAINNET).also { meta ->
          withContext(Dispatchers.IO) { metadataStore.save(meta) }
          refreshWalletsFlow()
        }
      } else {
        current.metadata
      }
    if (current.activeNetwork == Network.MAINNET) {
      if (updatedMeta != current.metadata) {
        _vaultState.value = current.copy(metadata = updatedMeta)
      }
      return
    }
    _vaultState.value =
      current.copy(metadata = updatedMeta, activeNetwork = Network.MAINNET)
    cleanupScope.launch { txManager?.recoverPendingTransactions(updatedMeta.addressFor(Network.MAINNET)) }
  }

  override fun lockWallet() {
    secureKeyStore.clearPinSession()
    val current = _vaultState.value as? VaultState.Unlocked ?: return
    _vaultState.value = VaultState.Locked(current.metadata)
  }

  override fun resetVault() {
    secureKeyStore.clearVault()
    metadataStore.clear()
    refreshWalletsFlow()
    cleanupScope.launch {
      balanceRepository?.clearCache()
      txManager?.clearAll()
    }
    _vaultState.value = VaultState.Uninitialized
  }

  override fun getReceiveAddress(network: Network?): TonAddress? {
    val current = _vaultState.value as? VaultState.Unlocked ?: return null
    val targetNetwork = network ?: current.activeNetwork
    return current.metadata.addressFor(targetNetwork)
  }

  override fun buildReceiveTransferUri(
    network: Network?,
    amount: Money?,
    comment: String?,
  ): String? {
    val addr = getReceiveAddress(network) ?: return null
    return TonTransferUri.build(
      recipient = addr,
      bounceable = false,
      amount = amount,
      comment = comment,
    )
  }

  override fun validateReceiveInput(input: String, network: Network?): AddressCheckResult {
    val targetNetwork =
      network ?: (_vaultState.value as? VaultState.Unlocked)?.activeNetwork ?: Network.MAINNET
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
      return AddressCheckResult.Invalid("Enter a TON address or ton://transfer/ link")
    }
    return if (trimmed.startsWith("ton://", ignoreCase = true)) {
      TonTransferUri.parse(trimmed, targetNetwork)
        .fold(
          onSuccess = { parsedUri ->
            AddressCheckResult.Valid(
              parsedAddress = parsedUri.parsedRecipient,
              uriAmount = parsedUri.amount,
              uriComment = parsedUri.comment,
            )
          },
          onFailure = { err ->
            AddressCheckResult.Invalid(err.message ?: "Invalid TON transfer link")
          },
        )
    } else {
      TonAddress.parseDetailed(trimmed, targetNetwork)
        .fold(
          onSuccess = { parsed -> AddressCheckResult.Valid(parsedAddress = parsed) },
          onFailure = { err -> AddressCheckResult.Invalid(err.message ?: "Invalid TON address") },
        )
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeBalance(): Flow<BalanceState> =
    vaultState.flatMapLatest { state ->
      when (state) {
        is VaultState.Unlocked -> {
          val address = state.metadata.addressFor(state.activeNetwork)
          balanceRepository?.observeBalance(address)
            ?: flowOf(BalanceState(network = state.activeNetwork, address = address))
        }
        else -> flowOf(BalanceState())
      }
    }

  override suspend fun refreshBalance(force: Boolean) {
    val current = _vaultState.value as? VaultState.Unlocked ?: return
    val address = current.metadata.addressFor(current.activeNetwork)
    balanceRepository?.refreshBalance(address, force = force)
  }

  override suspend fun prepareTransfer(
    recipientInput: String,
    amountText: String,
    comment: String?,
    assetKind: TxAssetKind,
    jettonMasterRaw: String?,
  ): Result<TransferPreview> {
    val current =
      _vaultState.value as? VaultState.Unlocked
        ?: return Result.failure(VaultException("Unlock your wallet first"))
    if (current.metadata.isWatchOnly) {
      return Result.failure(VaultException("Watch-only wallet cannot send transactions"))
    }
    val manager =
      txManager ?: return Result.failure(VaultException("Transfer service unavailable"))
    val activeNetwork = current.activeNetwork
    val fromAddress = current.metadata.addressFor(activeNetwork)

    val checked = validateReceiveInput(recipientInput, activeNetwork)
    if (checked is AddressCheckResult.Invalid) {
      return Result.failure(VaultException(checked.reason))
    }
    val validAddress = checked as AddressCheckResult.Valid

    val targetJettonMaster =
      if (assetKind == TxAssetKind.JETTON) {
        if (activeNetwork != Network.MAINNET) {
          return Result.failure(VaultException("Jettons are only supported on Mainnet"))
        }
        val fallbackUsdt =
          JettonAllowList.usdtFor(activeNetwork)
            ?: return Result.failure(VaultException("USDT is only supported on Mainnet"))
        if (!jettonMasterRaw.isNullOrBlank()) {
          TonAddress.parse(jettonMasterRaw, activeNetwork).getOrElse {
            return Result.failure(VaultException("Invalid Jetton master address"))
          }
        } else {
          fallbackUsdt.masterAddress
        }
      } else {
        null
      }

    val allowSpec = targetJettonMaster?.let { JettonAllowList.findVerified(it) }
    val cleanAmountText = amountText.trim()

    val preParsedAmount: Money? =
      if (assetKind == TxAssetKind.GRAM) {
        val gramAmount =
          if (cleanAmountText.isNotEmpty()) {
            runCatching { Money.fromDecimalString(cleanAmountText, GRAM_DECIMALS) }
              .getOrElse {
                return Result.failure(
                  VaultException("Enter a valid GRAM amount (up to $GRAM_DECIMALS decimals)")
                )
              }
          } else {
            validAddress.uriAmount
              ?: return Result.failure(VaultException("Enter a GRAM amount to send"))
          }
        if (gramAmount.units <= BigInteger.ZERO) {
          return Result.failure(VaultException("Send amount must be greater than zero"))
        }
        gramAmount
      } else {
        if (cleanAmountText.isEmpty()) {
          val sym = allowSpec?.symbol ?: "Jetton"
          return Result.failure(VaultException("Enter a $sym amount to send"))
        }
        if (allowSpec != null) {
          val allowAmt =
            runCatching { Money.fromDecimalString(cleanAmountText, allowSpec.decimals) }
              .getOrElse {
                return Result.failure(
                  VaultException(
                    "Enter a valid ${allowSpec.symbol} amount (up to ${allowSpec.decimals} decimals)"
                  )
                )
              }
          if (allowAmt.units <= BigInteger.ZERO) {
            return Result.failure(VaultException("Send amount must be greater than zero"))
          }
          allowAmt
        } else {
          val decimalCheck =
            cleanAmountText.toBigDecimalOrNull()
              ?: return Result.failure(VaultException("Enter a valid Jetton amount"))
          if (decimalCheck <= java.math.BigDecimal.ZERO) {
            return Result.failure(VaultException("Send amount must be greater than zero"))
          }
          null
        }
      }

    val normalizedComment =
      comment?.trim()?.takeIf { it.isNotEmpty() } ?: validAddress.uriComment?.trim()?.takeIf { it.isNotEmpty() }
    if (normalizedComment != null) {
      val commentBytes = normalizedComment.toByteArray(Charsets.UTF_8).size
      if (commentBytes > TransferBuilder.MAX_COMMENT_UTF8_BYTES) {
        return Result.failure(
          VaultException(
            "Comment is too long ($commentBytes bytes; maximum ${TransferBuilder.MAX_COMMENT_UTF8_BYTES} bytes)"
          )
        )
      }
    }

    return runCatching {
      manager.ensureNoActivePendingTx(fromAddress)

      val chainCtx =
        manager
          .fetchTransferChainContext(fromAddress, validAddress.parsedAddress.address)
          .getOrThrow()

      val senderNeedsStateInit =
        chainCtx.senderStatus == AccountOnChainStatus.NONEXIST ||
          chainCtx.senderStatus == AccountOnChainStatus.UNINIT

      val nowSec = networkClockMs() / 1000L
      val validUntilEpochSec = nowSec + TransferBuilder.DEFAULT_VALID_UNTIL_TTL_SECONDS
      val publicKey32 = hexToBytes(current.metadata.publicKeyHex)

      if (assetKind == TxAssetKind.GRAM) {
        val amount = preParsedAmount!!
        val effectiveBounce =
          chainCtx.destinationStatus != AccountOnChainStatus.NONEXIST &&
            chainCtx.destinationStatus != AccountOnChainStatus.UNINIT

        val dummyBoc =
          withContext(Dispatchers.Default) {
            TransferBuilder.buildDummyTransferBocForEstimation(
              senderAddress = fromAddress,
              publicKey32 = publicKey32,
              walletVersion = current.metadata.walletVersion,
              destination = validAddress.parsedAddress.address,
              amount = amount,
              bounce = effectiveBounce,
              seqno = chainCtx.senderSeqno,
              validUntilEpochSec = validUntilEpochSec,
              needsStateInit = senderNeedsStateInit,
              comment = normalizedComment,
            )
          }

        val remoteFee = manager.estimateTransferFee(fromAddress, dummyBoc)
        val estimatedFee = remoteFee ?: Money.ofNano(TransferBuilder.LOCAL_FALLBACK_FEE_NANO)
        val totalDebit = amount + estimatedFee
        if (chainCtx.senderBalance.units < totalDebit.units) {
          throw VaultException(
            "Insufficient GRAM balance: available ${chainCtx.senderBalance.format()} GRAM, needed ${totalDebit.format()} GRAM (including fee)"
          )
        }

        TransferPreview(
          network = activeNetwork,
          fromAddress = fromAddress,
          destination = validAddress.parsedAddress,
          destinationStatus = chainCtx.destinationStatus,
          effectiveBounce = effectiveBounce,
          amount = amount,
          estimatedFee = estimatedFee,
          totalDebit = totalDebit,
          comment = normalizedComment,
          seqno = chainCtx.senderSeqno,
          senderNeedsStateInit = senderNeedsStateInit,
          validUntilEpochSec = validUntilEpochSec,
          feeIsEstimateFallback = (remoteFee == null),
          assetKind = TxAssetKind.GRAM,
        )
      } else {
        val masterAddr = targetJettonMaster!!
        val holding = manager.fetchJettonHoldingContext(fromAddress, masterAddr).getOrThrow()
        val amount =
          preParsedAmount
            ?: runCatching {
              Money.fromDecimalString(cleanAmountText, holding.balance.decimals)
            }.getOrElse {
              throw VaultException(
                "Enter a valid ${holding.symbol} amount (up to ${holding.balance.decimals} decimals)"
              )
            }
        if (amount.units <= BigInteger.ZERO) {
          throw VaultException("Send amount must be greater than zero")
        }
        val jettonWallet =
          holding.jettonWalletAddress
            ?: throw VaultException("No active ${holding.symbol} Jetton wallet found on this account")
        if (holding.balance.units < amount.units) {
          throw VaultException(
            "Insufficient ${holding.symbol} balance: available ${holding.balance.format()} ${holding.symbol}, needed ${amount.format()} ${holding.symbol}"
          )
        }

        val attachedGram = Money.ofNano(TransferBuilder.DEFAULT_JETTON_ATTACHED_GRAM_NANO)
        val dummyBoc =
          withContext(Dispatchers.Default) {
            TransferBuilder.buildDummyJettonTransferBocForEstimation(
              senderAddress = fromAddress,
              senderJettonWallet = jettonWallet,
              publicKey32 = publicKey32,
              walletVersion = current.metadata.walletVersion,
              destination = validAddress.parsedAddress.address,
              jettonAmount = amount,
              seqno = chainCtx.senderSeqno,
              validUntilEpochSec = validUntilEpochSec,
              needsStateInit = senderNeedsStateInit,
              comment = normalizedComment,
              attachedGramNano = attachedGram.units.toLong(),
            )
          }

        val remoteFee = manager.estimateTransferFee(fromAddress, dummyBoc)
        val estimatedFee = remoteFee ?: Money.ofNano(TransferBuilder.LOCAL_JETTON_FALLBACK_FEE_NANO)
        val requiredGram = attachedGram + estimatedFee
        if (chainCtx.senderBalance.units < requiredGram.units) {
          throw VaultException(
            "Insufficient GRAM for ${holding.symbol} gas: available ${chainCtx.senderBalance.format()} GRAM, needed ${requiredGram.format()} GRAM"
          )
        }

        TransferPreview(
          network = activeNetwork,
          fromAddress = fromAddress,
          destination = validAddress.parsedAddress,
          destinationStatus = chainCtx.destinationStatus,
          effectiveBounce = true,
          amount = amount,
          estimatedFee = estimatedFee,
          totalDebit = amount,
          comment = normalizedComment,
          seqno = chainCtx.senderSeqno,
          senderNeedsStateInit = senderNeedsStateInit,
          validUntilEpochSec = validUntilEpochSec,
          feeIsEstimateFallback = (remoteFee == null),
          assetKind = TxAssetKind.JETTON,
          jettonMasterRaw = holding.masterAddress.raw,
          senderJettonWallet = jettonWallet,
          attachedGramNano = attachedGram,
          jettonSymbol = holding.symbol,
          jettonName = holding.name,
          jettonImageUrl = holding.imageUrl,
          jettonVerification = holding.verification,
        )
      }
    }
  }

  override suspend fun confirmAndSend(
    preview: TransferPreview,
    appPin: CharArray?,
  ): Result<TxRecord> {
    val current = _vaultState.value as? VaultState.Unlocked
    if (current == null) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Unlock your wallet first"))
    }
    if (current.metadata.isWatchOnly) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Watch-only wallet cannot sign transactions"))
    }
    val manager = txManager
    if (manager == null) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Transfer service unavailable"))
    }
    if (current.activeNetwork != preview.network) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Active network changed. Review transfer again."))
    }

    val expectedFrom = current.metadata.addressFor(preview.network)
    if (expectedFrom.raw != preview.fromAddress.raw) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Active wallet mismatch"))
    }

    val nowMs = networkClockMs()
    val nowEpochSec = nowMs / 1000L
    if (nowEpochSec >= preview.validUntilEpochSec) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Transfer review expired. Prepare transfer again."))
    }

    when (current.metadata.securityMode) {
      VaultSecurityMode.DEV_UNAUTH_KEYSTORE -> {
        if (!devSkipAuth) {
          appPin?.let { Arrays.fill(it, '\u0000') }
          return Result.failure(VaultException("Development vault cannot sign in release build"))
        }
      }
      VaultSecurityMode.DEVICE_KEYSTORE -> {
        when (val auth = authGate.authenticate(AuthReason.SIGN_ACTION)) {
          is AuthResult.Authenticated -> Unit
          is AuthResult.Cancelled -> return Result.failure(VaultException("Signing cancelled"))
          is AuthResult.NoDeviceSecurity ->
            return Result.failure(VaultException("Device lock screen is required"))
          is AuthResult.Failed -> return Result.failure(VaultException(auth.message))
        }
      }
      VaultSecurityMode.APP_PIN_WITH_KEYSTORE -> {
        if (appPin == null) {
          return Result.failure(VaultAuthRequiredException())
        }
        if (!SecureKeyStore.isValidPinFormat(appPin)) {
          Arrays.fill(appPin, '\u0000')
          return Result.failure(VaultException("Wallet PIN must be 6 to 8 digits"))
        }
      }
    }

    val useAppPin = current.metadata.securityMode == VaultSecurityMode.APP_PIN_WITH_KEYSTORE
    val requireUserAuth = current.metadata.securityMode != VaultSecurityMode.DEV_UNAUTH_KEYSTORE

    return try {
      manager.ensureNoActivePendingTx(preview.fromAddress)

      // Revalidate live chain seqno, contract initialization status, and balance immediately
      // before decrypting the private key or signing a new transaction.
      val latestChainInfo =
        manager.fetchCurrentWalletInfo(preview.fromAddress).getOrElse { err ->
          throw VaultException(
            "Unable to verify latest wallet sequence on TON before signing: ${err.message ?: "network unavailable"}"
          )
        }
      if (latestChainInfo.seqno != preview.seqno) {
        throw VaultException(
          "Wallet sequence changed on TON (expected #${preview.seqno}, chain is #${latestChainInfo.seqno}). Review transfer again."
        )
      }
      val latestNeedsStateInit =
        latestChainInfo.status == AccountOnChainStatus.NONEXIST ||
          latestChainInfo.status == AccountOnChainStatus.UNINIT
      if (latestNeedsStateInit != preview.senderNeedsStateInit) {
        throw VaultException("Wallet contract status changed on TON. Review transfer again.")
      }
      if (preview.assetKind == TxAssetKind.GRAM && latestChainInfo.balance.units < preview.totalDebit.units) {
        throw VaultException(
          "Insufficient GRAM balance on TON: available ${latestChainInfo.balance.format()} GRAM, needed ${preview.totalDebit.format()} GRAM"
        )
      }

      val signEpochSec = networkClockMs() / 1000L
      val txValidUntilSec = signEpochSec + TransferBuilder.DEFAULT_VALID_UNTIL_TTL_SECONDS
      val publicKey32 = hexToBytes(current.metadata.publicKeyHex)

      val signed =
        withContext(Dispatchers.IO) {
          secureKeyStore.withDecryptedSeed(
            pin = appPin,
            useAppPinMode = useAppPin,
            requireUserAuth = requireUserAuth,
            walletId = current.metadata.walletId,
          ) { seed32 ->
            if (preview.assetKind == TxAssetKind.JETTON) {
              val jettonWallet =
                preview.senderJettonWallet
                  ?: throw VaultException("Missing sender Jetton wallet address")
              val attachedNano =
                preview.attachedGramNano?.units?.toLong()
                  ?: TransferBuilder.DEFAULT_JETTON_ATTACHED_GRAM_NANO
              TransferBuilder.buildSignedJettonTransfer(
                seed32 = seed32,
                publicKey32 = publicKey32,
                senderAddress = preview.fromAddress,
                senderJettonWallet = jettonWallet,
                walletVersion = current.metadata.walletVersion,
                destination = preview.destination.address,
                jettonAmount = preview.amount,
                seqno = preview.seqno,
                validUntilEpochSec = txValidUntilSec,
                needsStateInit = preview.senderNeedsStateInit,
                comment = preview.comment,
                attachedGramNano = attachedNano,
              )
            } else {
              TransferBuilder.buildSignedTransfer(
                seed32 = seed32,
                publicKey32 = publicKey32,
                senderAddress = preview.fromAddress,
                walletVersion = current.metadata.walletVersion,
                destination = preview.destination.address,
                amount = preview.amount,
                bounce = preview.effectiveBounce,
                seqno = preview.seqno,
                validUntilEpochSec = txValidUntilSec,
                needsStateInit = preview.senderNeedsStateInit,
                comment = preview.comment,
              )
            }
          }
        }

      val record =
        TxRecord(
          id = UUID.randomUUID().toString(),
          network = preview.network,
          accountRaw = preview.fromAddress.raw,
          messageHash = signed.messageHashHex,
          normalizedHash = signed.normalizedHashHex,
          txHash = null,
          seqno = signed.seqno,
          direction = TxDirection.OUT,
          kind = preview.assetKind,
          amount = preview.amount,
          fee = preview.estimatedFee,
          counterpartyRaw = preview.destination.address.raw,
          comment = preview.comment,
          state = TxStatus.SIGNED,
          validUntilEpochSec = signed.validUntilEpochSec,
          lt = null,
          createdAtEpochMs = nowMs,
          updatedAtEpochMs = nowMs,
          jettonMaster = preview.jettonMasterRaw,
          jettonSymbol = preview.jettonSymbol,
          jettonName = preview.jettonName,
          jettonImageUrl = preview.jettonImageUrl,
          jettonVerification = preview.jettonVerification,
        )

      manager.persistSignedAndBroadcast(record, signed.externalBocBase64)
    } catch (e: VaultKeyInvalidatedException) {
      _vaultState.value = VaultState.KeyInvalidated(current.metadata)
      Result.failure(e)
    } catch (t: Throwable) {
      Result.failure(t)
    } finally {
      appPin?.let { Arrays.fill(it, '\u0000') }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeTransactions(): Flow<List<TxRecord>> =
    vaultState.flatMapLatest { state ->
      val manager = txManager
      if (state is VaultState.Unlocked && manager != null) {
        val address = state.metadata.addressFor(state.activeNetwork)
        manager.observeTransactions(address.raw, state.activeNetwork)
      } else {
        flowOf(emptyList())
      }
    }

  override suspend fun recoverPendingTransactions() {
    val current = _vaultState.value as? VaultState.Unlocked ?: return
    txManager?.recoverPendingTransactions(current.metadata.addressFor(current.activeNetwork))
  }

  override suspend fun refreshTransactionStatus(txId: String): TxRecord? =
    txManager?.refreshTransactionStatus(txId)

  override fun preferredNetwork(): Network = Network.MAINNET

  override suspend fun syncTransactionHistory(
    limit: Int,
    ignoreLtShortcut: Boolean,
  ): Result<HistorySyncSummary> {
    val current =
      _vaultState.value as? VaultState.Unlocked
        ?: return Result.failure(VaultException("Unlock your wallet first"))
    val manager =
      txManager ?: return Result.failure(VaultException("Transaction history service unavailable"))
    val address = current.metadata.addressFor(current.activeNetwork)
    val knownLt =
      if (ignoreLtShortcut) null else balanceRepository?.getCachedLastTxLt(address)
    return manager.syncAccountHistory(address, limit = limit, knownAccountLt = knownLt)
  }

  override suspend fun fetchSwapTokens(): List<SwapAsset> {
    val engine = swapEngine ?: return com.example.core.network.SwapCoffeeService.curatedDefaultAssets()
    val unlocked = _vaultState.value as? VaultState.Unlocked
    val holdings =
      if (unlocked != null && balanceRepository != null) {
        val addr = unlocked.metadata.addressFor(unlocked.activeNetwork)
        balanceRepository.observeBalance(addr).firstOrNull()?.jettonHoldings ?: emptyList()
      } else {
        emptyList()
      }
    return runCatching { engine.fetchAvailableTokens(holdings) }
      .getOrElse { com.example.core.network.SwapCoffeeService.curatedDefaultAssets() }
  }

  override suspend fun quoteSwap(
    inputAsset: SwapAsset,
    outputAsset: SwapAsset,
    inputAmountText: String,
    slippage: SlippageTolerance,
  ): Result<SwapQuote> {
    val current =
      _vaultState.value as? VaultState.Unlocked
        ?: return Result.failure(VaultException("Unlock your wallet first"))
    if (current.activeNetwork != Network.MAINNET) {
      return Result.failure(VaultException("Swaps are only supported on TON Mainnet"))
    }
    val engine = swapEngine ?: return Result.failure(VaultException("Swap service unavailable"))
    val cleanAmount = inputAmountText.trim()
    val parsedAmount =
      runCatching { Money.fromDecimalString(cleanAmount, inputAsset.decimals) }
        .getOrElse {
          return Result.failure(
            VaultException("Enter a valid ${inputAsset.symbol} amount (up to ${inputAsset.decimals} decimals)")
          )
        }
    if (parsedAmount.units <= BigInteger.ZERO) {
      return Result.failure(VaultException("Swap amount must be greater than zero"))
    }
    return engine.fetchQuote(
      inputAsset = inputAsset,
      outputAsset = outputAsset,
      inputAmount = parsedAmount,
      slippage = slippage,
    )
  }

  override suspend fun prepareSwapReview(
    quote: SwapQuote,
    slippage: SlippageTolerance,
  ): Result<SwapPreview> {
    val current =
      _vaultState.value as? VaultState.Unlocked
        ?: return Result.failure(VaultException("Unlock your wallet first"))
    if (current.metadata.isWatchOnly) {
      return Result.failure(VaultException("Watch-only wallet cannot swap tokens"))
    }
    if (current.activeNetwork != Network.MAINNET) {
      return Result.failure(VaultException("Swaps are only supported on TON Mainnet"))
    }
    val manager = txManager ?: return Result.failure(VaultException("Transfer service unavailable"))
    val engine = swapEngine ?: return Result.failure(VaultException("Swap service unavailable"))
    val fromAddress = current.metadata.addressFor(current.activeNetwork)

    return runCatching {
      manager.ensureNoActivePendingTx(fromAddress)

      val walletInfo = manager.fetchCurrentWalletInfo(fromAddress).getOrThrow()
      var senderJettonBalance: Money? = null
      if (!quote.inputAsset.isNative) {
        val masterAddr =
          TonAddress.parse(quote.inputAsset.addressOrNative, current.activeNetwork).getOrThrow()
        val holding = manager.fetchJettonHoldingContext(fromAddress, masterAddr).getOrThrow()
        if (holding.balance.units < quote.inputAmount.units) {
          throw VaultException(
            "Insufficient ${quote.inputAsset.symbol} balance: available ${holding.balance.format()} ${quote.inputAsset.symbol}, needed ${quote.inputAmount.format()} ${quote.inputAsset.symbol}"
          )
        }
        senderJettonBalance = holding.balance
      }

      val internalMsgs =
        engine
          .buildSwapMessages(
            senderAddress = fromAddress,
            quote = quote,
            slippage = slippage,
          )
          .getOrThrow()

      val rawMessages =
        internalMsgs.map { msg ->
          RawInternalMessage(
            destination = msg.destination,
            amountNano = msg.attachedTonNano,
            bounce = msg.bounce,
            bodyCell = msg.payloadCell,
            sendMode = msg.sendMode,
          )
        }

      val totalAttachedNano = internalMsgs.fold(BigInteger.ZERO) { acc, m -> acc + m.attachedTonNano }
      val totalAttachedTon = Money(totalAttachedNano, GRAM_DECIMALS)

      val senderNeedsStateInit =
        walletInfo.status == AccountOnChainStatus.NONEXIST ||
          walletInfo.status == AccountOnChainStatus.UNINIT
      val nowSec = networkClockMs() / 1000L
      val validUntilEpochSec = nowSec + TransferBuilder.DEFAULT_VALID_UNTIL_TTL_SECONDS
      val publicKey32 = hexToBytes(current.metadata.publicKeyHex)

      val dummyBoc =
        withContext(Dispatchers.Default) {
          TransferBuilder.buildDummyMultiTransferBocForEstimation(
            senderAddress = fromAddress,
            publicKey32 = publicKey32,
            walletVersion = current.metadata.walletVersion,
            messages = rawMessages,
            seqno = walletInfo.seqno,
            validUntilEpochSec = validUntilEpochSec,
            needsStateInit = senderNeedsStateInit,
          )
        }

      val remoteFee = manager.estimateTransferFee(fromAddress, dummyBoc)
      val estimatedFee = remoteFee ?: Money.ofNano(TransferBuilder.LOCAL_SWAP_FEE_NANO)
      val totalTonRequired = totalAttachedTon + estimatedFee

      if (walletInfo.balance.units < totalTonRequired.units) {
        throw VaultException(
          "Insufficient GRAM for swap and gas: available ${walletInfo.balance.format()} GRAM, needed ${totalTonRequired.format()} GRAM"
        )
      }

      SwapPreview(
        network = current.activeNetwork,
        fromAddress = fromAddress,
        quote = quote.copy(slippage = slippage),
        internalMessages = internalMsgs,
        totalAttachedTon = totalAttachedTon,
        estimatedNetworkFee = estimatedFee,
        totalTonRequired = totalTonRequired,
        seqno = walletInfo.seqno,
        senderNeedsStateInit = senderNeedsStateInit,
        validUntilEpochSec = validUntilEpochSec,
        feeIsEstimateFallback = (remoteFee == null),
        senderJettonBalance = senderJettonBalance,
      )
    }
  }

  override suspend fun confirmAndExecuteSwap(
    preview: SwapPreview,
    appPin: CharArray?,
  ): Result<TxRecord> {
    val current = _vaultState.value as? VaultState.Unlocked
    if (current == null) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Unlock your wallet first"))
    }
    if (current.metadata.isWatchOnly) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Watch-only wallet cannot swap tokens"))
    }
    val manager = txManager
    if (manager == null) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Transfer service unavailable"))
    }
    if (current.activeNetwork != preview.network) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Active network changed. Review swap again."))
    }
    val expectedFrom = current.metadata.addressFor(preview.network)
    if (expectedFrom.raw != preview.fromAddress.raw) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Active wallet mismatch"))
    }
    val nowMs = networkClockMs()
    val nowEpochSec = nowMs / 1000L
    if (nowEpochSec >= preview.validUntilEpochSec) {
      appPin?.let { Arrays.fill(it, '\u0000') }
      return Result.failure(VaultException("Swap quote expired. Review swap again."))
    }

    when (current.metadata.securityMode) {
      VaultSecurityMode.DEV_UNAUTH_KEYSTORE -> {
        if (!devSkipAuth) {
          appPin?.let { Arrays.fill(it, '\u0000') }
          return Result.failure(VaultException("Development vault cannot sign in release build"))
        }
      }
      VaultSecurityMode.DEVICE_KEYSTORE -> {
        when (val auth = authGate.authenticate(AuthReason.SIGN_ACTION)) {
          is AuthResult.Authenticated -> Unit
          is AuthResult.Cancelled -> return Result.failure(VaultException("Signing cancelled"))
          is AuthResult.NoDeviceSecurity ->
            return Result.failure(VaultException("Device lock screen is required"))
          is AuthResult.Failed -> return Result.failure(VaultException(auth.message))
        }
      }
      VaultSecurityMode.APP_PIN_WITH_KEYSTORE -> {
        if (appPin == null) {
          return Result.failure(VaultAuthRequiredException())
        }
        if (!SecureKeyStore.isValidPinFormat(appPin)) {
          Arrays.fill(appPin, '\u0000')
          return Result.failure(VaultException("Wallet PIN must be 6 to 8 digits"))
        }
      }
    }

    val useAppPin = current.metadata.securityMode == VaultSecurityMode.APP_PIN_WITH_KEYSTORE
    val requireUserAuth = current.metadata.securityMode != VaultSecurityMode.DEV_UNAUTH_KEYSTORE

    return try {
      manager.ensureNoActivePendingTx(preview.fromAddress)

      val latestChainInfo =
        manager.fetchCurrentWalletInfo(preview.fromAddress).getOrElse { err ->
          throw VaultException(
            "Unable to verify latest wallet sequence on TON before signing: ${err.message ?: "network unavailable"}"
          )
        }
      if (latestChainInfo.seqno != preview.seqno) {
        throw VaultException(
          "Wallet sequence changed on TON (expected #${preview.seqno}, chain is #${latestChainInfo.seqno}). Review swap again."
        )
      }
      val latestNeedsStateInit =
        latestChainInfo.status == AccountOnChainStatus.NONEXIST ||
          latestChainInfo.status == AccountOnChainStatus.UNINIT
      if (latestNeedsStateInit != preview.senderNeedsStateInit) {
        throw VaultException("Wallet contract status changed on TON. Review swap again.")
      }
      if (latestChainInfo.balance.units < preview.totalTonRequired.units) {
        throw VaultException(
          "Insufficient GRAM balance on TON: available ${latestChainInfo.balance.format()} GRAM, needed ${preview.totalTonRequired.format()} GRAM"
        )
      }

      val signEpochSec = networkClockMs() / 1000L
      val txValidUntilSec = signEpochSec + TransferBuilder.DEFAULT_VALID_UNTIL_TTL_SECONDS
      val publicKey32 = hexToBytes(current.metadata.publicKeyHex)
      val rawMessages =
        preview.internalMessages.map { msg ->
          RawInternalMessage(
            destination = msg.destination,
            amountNano = msg.attachedTonNano,
            bounce = msg.bounce,
            bodyCell = msg.payloadCell,
            sendMode = msg.sendMode,
          )
        }

      val signed =
        withContext(Dispatchers.IO) {
          secureKeyStore.withDecryptedSeed(
            pin = appPin,
            useAppPinMode = useAppPin,
            requireUserAuth = requireUserAuth,
            walletId = current.metadata.walletId,
          ) { seed32 ->
            TransferBuilder.buildSignedMultiTransfer(
              seed32 = seed32,
              publicKey32 = publicKey32,
              senderAddress = preview.fromAddress,
              walletVersion = current.metadata.walletVersion,
              messages = rawMessages,
              seqno = preview.seqno,
              validUntilEpochSec = txValidUntilSec,
              needsStateInit = preview.senderNeedsStateInit,
            )
          }
        }

      val q = preview.quote
      val txComment =
        "Swap ${q.inputAmount.format()} ${q.inputAsset.symbol} → ~${q.expectedOutputAmount.format()} ${q.outputAsset.symbol}"
      val primaryCounterparty = preview.internalMessages.first().destination.raw
      val isNativeIn = preview.isNativeInput
      val record =
        TxRecord(
          id = UUID.randomUUID().toString(),
          network = preview.network,
          accountRaw = preview.fromAddress.raw,
          messageHash = signed.messageHashHex,
          normalizedHash = signed.normalizedHashHex,
          txHash = null,
          seqno = signed.seqno,
          direction = TxDirection.OUT,
          kind = if (isNativeIn) TxAssetKind.GRAM else TxAssetKind.JETTON,
          amount = q.inputAmount,
          fee = preview.estimatedNetworkFee,
          counterpartyRaw = primaryCounterparty,
          comment = txComment,
          state = TxStatus.SIGNED,
          validUntilEpochSec = signed.validUntilEpochSec,
          lt = null,
          createdAtEpochMs = nowMs,
          updatedAtEpochMs = nowMs,
          jettonMaster = if (isNativeIn) null else q.inputAsset.rawAddress,
          jettonSymbol = if (isNativeIn) null else q.inputAsset.symbol,
          jettonName = if (isNativeIn) null else q.inputAsset.name,
          jettonImageUrl = if (isNativeIn) null else q.inputAsset.imageUrl,
          jettonVerification = if (isNativeIn) null else q.inputAsset.verification,
        )

      manager.persistSignedAndBroadcast(record, signed.externalBocBase64)
    } catch (e: VaultKeyInvalidatedException) {
      _vaultState.value = VaultState.KeyInvalidated(current.metadata)
      Result.failure(e)
    } catch (t: Throwable) {
      Result.failure(t)
    } finally {
      appPin?.let { Arrays.fill(it, '\u0000') }
    }
  }

  private fun hexToBytes(hex: String): ByteArray {
    val clean = hex.trim()
    require(clean.length % 2 == 0) { "Invalid hex string length" }
    return ByteArray(clean.length / 2) { i ->
      clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
  }
}
