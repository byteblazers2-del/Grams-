package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.GramVaultDatabase
import com.example.core.keystore.AndroidKeyStoreCipherProvider
import com.example.core.keystore.AuthGate
import com.example.core.keystore.KeySecurityLevel
import com.example.core.keystore.KeystoreCipherProvider
import com.example.core.keystore.SecureKeyStore
import com.example.core.keystore.VaultAuthRequiredException
import com.example.core.keystore.VaultKeyInvalidatedException
import com.example.core.model.AccountOnChainStatus
import com.example.core.model.AllowedJettonSpec
import com.example.core.model.AuthReason
import com.example.core.model.AuthResult
import com.example.core.model.BalanceState
import com.example.core.model.JettonAllowList
import com.example.core.model.Money
import com.example.core.model.Network
import com.example.core.model.RemoteAccountBalance
import com.example.core.model.RemoteJettonBalance
import com.example.core.model.RemoteJettonHistoryPage
import com.example.core.model.TonAddress
import com.example.core.model.TxAssetKind
import com.example.core.model.TxDirection
import com.example.core.model.TxStatus
import com.example.core.model.USDT_DECIMALS
import com.example.core.network.BroadcastResult
import com.example.core.network.ChainGateway
import com.example.core.network.OnChainMessageStatus
import com.example.core.network.RateLimitedException
import com.example.core.network.RemoteChainTransfer
import com.example.core.network.RemoteFeeEstimate
import com.example.core.network.RemoteTxHistoryPage
import com.example.core.network.RemoteTxLookup
import com.example.core.network.RemoteWalletInfo
import com.example.core.network.TransportException
import com.example.core.tx.TxManager
import com.example.core.wallet.AddressCheckResult
import com.example.core.wallet.TransferBuilder
import com.example.core.wallet.VaultMetadataStore
import com.example.core.wallet.WalletCoreImpl
import com.example.ui.screens.SendFormScreen
import com.example.ui.theme.GramVaultTheme
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StressAndRecoveryRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun crash_recovery_across_signed_broadcasted_and_uncertain_tx_states() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    File(context.filesDir, "vault").deleteRecursively()
    val dbFile = File(context.cacheDir, "crash_recovery_stress.db").apply { delete() }

    val nowMs = 1_794_750_000_000L
    val cipherProvider = TestAesCipherProvider()
    val authGate = TestAuthGate()
    val dest = TonAddress("0:" + "44".repeat(32), Network.MAINNET)

    val gateway =
      MutableStressGateway(nowProvider = { nowMs }).apply {
        walletStatuses[dest.raw] = AccountOnChainStatus.ACTIVE
        senderBalance = Money.ofNano(10_000_000_000L)
        senderStatus = AccountOnChainStatus.ACTIVE
        senderSeqno = 1
      }

    fun openStack(): Triple<GramVaultDatabase, CoroutineScope, Pair<TxManager, WalletCoreImpl>> {
      val db =
        Room.databaseBuilder(context, GramVaultDatabase::class.java, dbFile.absolutePath)
          .allowMainThreadQueries()
          .build()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
      val txManager = TxManager(txDao = db.txDao(), chainGateway = gateway, clockMs = { nowMs }, scope = scope)
      val core =
        WalletCoreImpl(
          applicationContext = context,
          authGate = authGate,
          secureKeyStore = SecureKeyStore(context, cipherProvider),
          metadataStore = VaultMetadataStore(context),
          txManager = txManager,
          clockMs = { nowMs },
        )
      return Triple(db, scope, txManager to core)
    }

    try {
      // --- PHASE A: Network cut during broadcast -> SIGNED persisted -> crash & restart -> identical BOC rebroadcast ---
      val (db1, scope1, pair1) = openStack()
      val (_, core1) = pair1
      val created = core1.createWallet(Network.MAINNET).getOrThrow()
      created.mnemonic.wipe()
      val senderAddr = created.mainnetAddress
      gateway.senderAddressRaw = senderAddr.raw

      val preview1 = core1.prepareTransfer(dest.toFriendly(true), "1.0", "Crash SIGNED").getOrThrow()
      gateway.sendBocFailure = TransportException("TON Center", IOException("Connection reset during broadcast"))
      val tx1AfterCut = core1.confirmAndSend(preview1).getOrThrow()
      assertEquals(TxStatus.SIGNED, tx1AfterCut.state)
      val persistedEntity1 = db1.txDao().getById(tx1AfterCut.id)!!
      val originalSignedBoc = persistedEntity1.bocBase64
      assertNotNull("SIGNED tx must retain signed BOC on disk across process kill", originalSignedBoc)

      // Simulate process kill before recovery
      scope1.cancel()
      db1.close()

      // Cold restart: network is back up
      gateway.sendBocFailure = null
      gateway.sendBocCount = 0
      val (db2, scope2, pair2) = openStack()
      val (_, core2) = pair2
      core2.unlockWallet(Network.MAINNET).getOrThrow()
      core2.recoverPendingTransactions()

      assertEquals(1, gateway.sendBocCount)
      assertEquals(
        "Restart recovery must rebroadcast the exact original signed BOC",
        originalSignedBoc,
        gateway.lastSentBoc,
      )
      val tx1AfterRestart = core2.refreshTransactionStatus(tx1AfterCut.id)!!
      assertEquals(TxStatus.BROADCASTED, tx1AfterRestart.state)
      assertNull("Broadcasted tx must clear raw BOC", db2.txDao().getById(tx1AfterCut.id)!!.bocBase64)

      // Confirm tx1 so seqno advances to 2
      gateway.nextLookup =
        RemoteTxLookup(
          status = OnChainMessageStatus.CONFIRMED,
          txHash = "hash_tx_1",
          lt = "1001",
          fee = Money.ofNano(4_000_000L),
          providerName = "TON Center",
        )
      assertEquals(TxStatus.CONFIRMED, core2.refreshTransactionStatus(tx1AfterCut.id)!!.state)
      gateway.senderSeqno = 2

      // --- PHASE B: Crash while BROADCASTED -> restart reconciles via lookup without re-sending BOC ---
      gateway.nextLookup = RemoteTxLookup(OnChainMessageStatus.PENDING, null, null, null, "TonAPI")
      val preview2 = core2.prepareTransfer(dest.toFriendly(true), "1.5", "Crash BROADCASTED").getOrThrow()
      val tx2Broadcasted = core2.confirmAndSend(preview2).getOrThrow()
      assertEquals(TxStatus.BROADCASTED, tx2Broadcasted.state)
      val sendsAfterTx2 = gateway.sendBocCount

      scope2.cancel()
      db2.close()

      // Network confirms tx2 while process was dead
      gateway.nextLookup =
        RemoteTxLookup(
          status = OnChainMessageStatus.CONFIRMED,
          txHash = "hash_tx_2",
          lt = "1002",
          fee = Money.ofNano(4_000_000L),
          providerName = "TonAPI",
        )
      val (db3, scope3, pair3) = openStack()
      val (_, core3) = pair3
      core3.unlockWallet(Network.MAINNET).getOrThrow()
      core3.recoverPendingTransactions()
      assertEquals("BROADCASTED tx must never be re-broadcasted on restart", sendsAfterTx2, gateway.sendBocCount)
      assertEquals(TxStatus.CONFIRMED, db3.txDao().getById(tx2Broadcasted.id)!!.state.let { TxStatus.valueOf(it) })

      // --- PHASE C: Seqno advances without trace -> UNCERTAIN persisted across crash/restart until trace confirms ---
      gateway.senderSeqno = 3
      gateway.nextLookup = RemoteTxLookup(OnChainMessageStatus.PENDING, null, null, null, "TonAPI")
      val preview3 = core3.prepareTransfer(dest.toFriendly(true), "2.0", "Crash UNCERTAIN").getOrThrow()
      val tx3 = core3.confirmAndSend(preview3).getOrThrow()
      val sendsBeforeUncertain = gateway.sendBocCount

      // Remote seqno advances to 4 while lookup still says PENDING
      gateway.senderSeqno = 4
      val tx3Uncertain = core3.refreshTransactionStatus(tx3.id)!!
      assertEquals(TxStatus.UNCERTAIN, tx3Uncertain.state)

      scope3.cancel()
      db3.close()

      // Cold restart while UNCERTAIN: lookup still PENDING -> stays UNCERTAIN, ZERO rebroadcasts
      val (db4, scope4, pair4) = openStack()
      val (_, core4) = pair4
      core4.unlockWallet(Network.MAINNET).getOrThrow()
      core4.recoverPendingTransactions()
      assertEquals("UNCERTAIN tx must never rebroadcast on restart", sendsBeforeUncertain, gateway.sendBocCount)
      assertEquals(TxStatus.UNCERTAIN, TxStatus.valueOf(db4.txDao().getById(tx3.id)!!.state))

      // Eventually chain lookup resolves -> CONFIRMED
      gateway.nextLookup =
        RemoteTxLookup(
          status = OnChainMessageStatus.CONFIRMED,
          txHash = "hash_tx_3",
          lt = "1003",
          fee = Money.ofNano(4_500_000L),
          providerName = "TON Center",
        )
      assertEquals(TxStatus.CONFIRMED, core4.refreshTransactionStatus(tx3.id)!!.state)

      scope4.cancel()
      db4.close()
    } finally {
      dbFile.delete()
    }
  }

  @Test
  fun sync_account_history_deduplicates_duplicate_items_and_survives_partial_provider_failures() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        Room.inMemoryDatabaseBuilder(context, GramVaultDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
      try {
        val nowMs = 1_794_750_000_000L
        val account = TonAddress("0:" + "11".repeat(32), Network.MAINNET)
        val sender = TonAddress("0:" + "22".repeat(32), Network.MAINNET)
        val gateway = MutableStressGateway(nowProvider = { nowMs }).apply { senderAddressRaw = account.raw }
        val txManager = TxManager(txDao = db.txDao(), chainGateway = gateway, clockMs = { nowMs }, scope = scope)

        // First sync hits HTTP 429 rate limit cleanly without corrupting Room
        gateway.historyFailure = RateLimitedException("TON Center", retryAfterMs = 5_000L)
        val failedSync = txManager.syncAccountHistory(account)
        assertTrue(failedSync.isFailure)
        assertTrue(failedSync.exceptionOrNull() is RateLimitedException)
        assertTrue(db.txDao().observeByAccount(account.raw, Network.MAINNET.name).first().isEmpty())

        // Second sync succeeds for TON history (containing duplicate identical entries) while Jetton history fails (partial)
        gateway.historyFailure = null
        gateway.jettonHistoryFailure = RateLimitedException("TonAPI", retryAfterMs = 3_000L)
        val duplicateTransfer =
          RemoteChainTransfer(
            txHash = "aa".repeat(32),
            lt = "9001",
            utimeEpochSec = 1_794_750_010L,
            direction = TxDirection.IN,
            amount = Money.ofNano(2_500_000_000L),
            fee = Money.ofNano(1_000_000L),
            counterpartyRaw = sender.raw,
            comment = "Deposit",
            status = OnChainMessageStatus.CONFIRMED,
            inMsgHash = "bb".repeat(32),
            inMsgNormalizedHash = "bb".repeat(32),
          )
        gateway.historyTransfers = listOf(duplicateTransfer)

        val partialSummary = txManager.syncAccountHistory(account).getOrThrow()
        assertEquals(1, partialSummary.newIncomingTxs.size)
        // Re-syncing an overlapping history page must remain idempotent with 1 row
        val overlapResync = txManager.syncAccountHistory(account).getOrThrow()
        assertEquals(0, overlapResync.newIncomingTxs.size)
        val stored = db.txDao().observeByAccount(account.raw, Network.MAINNET.name).first()
        assertEquals("Overlapping history sync must collapse cleanly to 1 Room record", 1, stored.size)
        assertEquals("2.50", Money.parseUnits(stored.single().amountUnits, 9).format())
      } finally {
        scope.cancel()
        db.close()
      }
    }

  @Test
  fun edge_cases_send_max_self_transfer_and_testnet_rejection_on_mainnet() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    File(context.filesDir, "vault").deleteRecursively()
    val db =
      Room.inMemoryDatabaseBuilder(context, GramVaultDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val nowMs = 1_794_750_000_000L
      val authGate = TestAuthGate()
      val cipher = TestAesCipherProvider()
      val gateway =
        MutableStressGateway(nowProvider = { nowMs }).apply {
          senderBalance = Money.ofNano(2_500_000_000L) // 2.50 GRAM
          senderStatus = AccountOnChainStatus.ACTIVE
          senderSeqno = 4
          senderJettonWallet = TonAddress("0:" + "cd".repeat(32), Network.MAINNET)
          senderUsdtBalance = Money.fromDecimalString("50.00", USDT_DECIMALS)
          nextFeeNano = 5_000_000L
        }
      val txManager = TxManager(txDao = db.txDao(), chainGateway = gateway, clockMs = { nowMs }, scope = scope)
      val core =
        WalletCoreImpl(
          applicationContext = context,
          authGate = authGate,
          secureKeyStore = SecureKeyStore(context, cipher),
          metadataStore = VaultMetadataStore(context),
          txManager = txManager,
          clockMs = { nowMs },
        )

      val created = core.createWallet(Network.MAINNET).getOrThrow()
      created.mnemonic.wipe()
      val ownMainnet = created.mainnetAddress
      gateway.senderAddressRaw = ownMainnet.raw
      gateway.walletStatuses[ownMainnet.raw] = AccountOnChainStatus.ACTIVE

      // 1. Testnet addresses & transfer URIs strictly rejected on Mainnet
      val testnetNonBounce = created.testnetAddress.toFriendly(bounceable = false)
      val testnetBounce = created.testnetAddress.toFriendly(bounceable = true)
      assertTrue(core.validateReceiveInput(testnetNonBounce, Network.MAINNET) is com.example.core.wallet.AddressCheckResult.Invalid)
      assertTrue(core.validateReceiveInput(testnetBounce, Network.MAINNET) is com.example.core.wallet.AddressCheckResult.Invalid)
      assertTrue(core.validateReceiveInput("ton://transfer/$testnetNonBounce?amount=1000000000", Network.MAINNET) is com.example.core.wallet.AddressCheckResult.Invalid)
      assertTrue(core.prepareTransfer(testnetNonBounce, "0.50", null).isFailure)

      // 2. Self-transfer to own Mainnet address succeeds for both GRAM and USDT
      val selfGramPreview = core.prepareTransfer(ownMainnet.toFriendly(false), "0.25", "Self GRAM").getOrThrow()
      assertEquals(ownMainnet.raw, selfGramPreview.destination.address.raw)
      val selfGramTx = core.confirmAndSend(selfGramPreview).getOrThrow()
      assertEquals(TxStatus.BROADCASTED, selfGramTx.state)

      // Confirm pending selfGramTx cleanly before preparing the next transfer
      gateway.nextLookup =
        RemoteTxLookup(
          status = OnChainMessageStatus.CONFIRMED,
          txHash = "self_gram_hash",
          lt = "5001",
          fee = Money.ofNano(5_000_000L),
          providerName = "TonAPI",
        )
      assertEquals(TxStatus.CONFIRMED, core.refreshTransactionStatus(selfGramTx.id)!!.state)

      gateway.senderSeqno = 5
      val selfUsdtPreview =
        core.prepareTransfer(ownMainnet.toFriendly(false), "5.00", "Self USDT", TxAssetKind.JETTON).getOrThrow()
      assertEquals(TxAssetKind.JETTON, selfUsdtPreview.assetKind)
      assertEquals(ownMainnet.raw, selfUsdtPreview.destination.address.raw)

      // 3. Send Max boundary: full balance (2.50 GRAM) without reserving fee is rejected; Send Max (2.48 GRAM) succeeds
      val overMax = core.prepareTransfer(ownMainnet.toFriendly(false), "2.50", null, TxAssetKind.GRAM)
      assertTrue(overMax.isFailure)
      assertTrue(overMax.exceptionOrNull()?.message?.contains("Insufficient GRAM", ignoreCase = true) == true)

      val maxGramReserve = TransferBuilder.LOCAL_FALLBACK_FEE_NANO * 2L
      val expectedMaxGram = Money(gateway.senderBalance.units - java.math.BigInteger.valueOf(maxGramReserve), 9).format()
      assertEquals("2.47", expectedMaxGram)
      val maxGramPreview = core.prepareTransfer(ownMainnet.toFriendly(false), expectedMaxGram, null, TxAssetKind.GRAM).getOrThrow()
      assertTrue(maxGramPreview.totalDebit.units <= gateway.senderBalance.units)

      // 4. UI SendFormScreen MAX button test for GRAM, USDT, and tiny dust balance hiding MAX
      var amountInput by mutableStateOf("")
      var selectedAsset by mutableStateOf(TxAssetKind.GRAM)
      var balanceState by mutableStateOf(
        BalanceState(
          network = Network.MAINNET,
          address = ownMainnet,
          gramBalance = Money.ofNano(2_500_000_000L),
          usdtBalance = Money.fromDecimalString("50.00", USDT_DECIMALS),
        )
      )
      composeTestRule.setContent {
        GramVaultTheme {
          SendFormScreen(
            activeNetwork = Network.MAINNET,
            balanceState = balanceState,
            recipientInput = ownMainnet.toFriendly(false),
            amountInput = amountInput,
            commentInput = "",
            isBusy = false,
            errorMessage = null,
            onRecipientChange = {},
            onAmountChange = { amountInput = it },
            onCommentChange = {},
            onValidateRecipient = { AddressCheckResult.Valid(TonAddress.parseDetailed(it, Network.MAINNET).getOrThrow()) },
            onReviewTransfer = {},
            onBack = {},
            selectedAsset = selectedAsset,
            onAssetChange = { selectedAsset = it },
          )
        }
      }

      composeTestRule.onNodeWithTag("send_max_button").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      assertEquals("2.47", amountInput)

      composeTestRule.runOnUiThread { amountInput = "" }
      composeTestRule.onNodeWithTag("send_asset_usdt").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("send_available_balance_text").assertTextContains("50.00", substring = true)
      composeTestRule.onNodeWithTag("send_max_button").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      assertEquals("50.00", amountInput)

      // When GRAM balance <= reserve (2 * 0.015 = 0.03 GRAM), MAX button disappears
      composeTestRule.runOnUiThread {
        selectedAsset = TxAssetKind.GRAM
        balanceState = balanceState.copy(gramBalance = Money.ofNano(10_000_000L))
      }
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("MAX").assertDoesNotExist()
    } finally {
      scope.cancel()
      db.close()
    }
  }

  private class TestAuthGate : AuthGate {
    override fun isDeviceSecure(): Boolean = true
    override suspend fun authenticate(reason: AuthReason): AuthResult = AuthResult.Authenticated
  }

  private class TestAesCipherProvider : KeystoreCipherProvider {
    private var authKeyBytes: ByteArray? = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var hwKeyBytes: ByteArray? = ByteArray(32).also { SecureRandom().nextBytes(it) }
    var keyInvalidated: Boolean = false

    override fun ensureKeyExists(requireUserAuth: Boolean) {
      if (requireUserAuth && authKeyBytes == null) authKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
      if (!requireUserAuth && hwKeyBytes == null) hwKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    }

    override fun encrypt(plaintext: ByteArray, requireUserAuth: Boolean): ByteArray {
      if (keyInvalidated) throw VaultKeyInvalidatedException()
      ensureKeyExists(requireUserAuth)
      val key = if (requireUserAuth) authKeyBytes else hwKeyBytes
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
      return AndroidKeyStoreCipherProvider.encodeEnvelope(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(envelope: ByteArray, requireUserAuth: Boolean): ByteArray {
      if (keyInvalidated) throw VaultKeyInvalidatedException()
      val key = (if (requireUserAuth) authKeyBytes else hwKeyBytes) ?: throw VaultKeyInvalidatedException()
      val (iv, ct) = AndroidKeyStoreCipherProvider.decodeEnvelope(envelope)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
      return cipher.doFinal(ct)
    }

    override fun deleteKey(requireUserAuth: Boolean) {
      if (requireUserAuth) authKeyBytes = null else hwKeyBytes = null
    }

    override fun inspectKeySecurityLevel(requireUserAuth: Boolean): KeySecurityLevel = KeySecurityLevel.TRUSTED_ENVIRONMENT
  }

  private class MutableStressGateway(
    private val nowProvider: () -> Long,
  ) : ChainGateway {
    var senderAddressRaw: String = ""
    var senderBalance: Money = Money.ofNano(5_000_000_000L)
    var senderStatus: AccountOnChainStatus = AccountOnChainStatus.ACTIVE
    var senderSeqno: Int = 0
    var senderJettonWallet: TonAddress? = null
    var senderUsdtBalance: Money = Money.zero(USDT_DECIMALS)
    val walletStatuses = mutableMapOf<String, AccountOnChainStatus>()
    var nextFeeNano: Long = 5_000_000L
    var sendBocFailure: Throwable? = null
    var sendBocCount: Int = 0
    var lastSentBoc: String? = null
    var nextLookup: RemoteTxLookup = RemoteTxLookup(OnChainMessageStatus.PENDING, null, null, null, "TonAPI")
    var historyTransfers: List<RemoteChainTransfer> = emptyList()
    var historyFailure: Throwable? = null
    var jettonHistoryFailure: Throwable? = null

    override suspend fun fetchAccountBalance(address: TonAddress): Result<RemoteAccountBalance> {
      val status = if (address.raw == senderAddressRaw) senderStatus else walletStatuses[address.raw] ?: AccountOnChainStatus.NONEXIST
      val bal = if (address.raw == senderAddressRaw) senderBalance else Money.zero()
      return Result.success(RemoteAccountBalance(address, bal, status, "TonAPI", nowProvider()))
    }

    override suspend fun fetchJettonBalance(ownerAddress: TonAddress, spec: AllowedJettonSpec): Result<RemoteJettonBalance> =
      Result.success(
        RemoteJettonBalance(
          ownerAddress = ownerAddress,
          jettonMaster = spec.masterAddress,
          jettonWalletAddress = senderJettonWallet,
          balance = senderUsdtBalance,
          providerName = "TonAPI",
          fetchedAtEpochMs = nowProvider(),
        )
      )

    override suspend fun fetchWalletInfo(address: TonAddress): Result<RemoteWalletInfo> {
      val status = if (address.raw == senderAddressRaw) senderStatus else walletStatuses[address.raw] ?: AccountOnChainStatus.NONEXIST
      val seqno = if (address.raw == senderAddressRaw) senderSeqno else 0
      val bal = if (address.raw == senderAddressRaw) senderBalance else Money.zero()
      return Result.success(RemoteWalletInfo(address, bal, status, seqno, "TonAPI", nowProvider()))
    }

    override suspend fun estimateExternalMessageFee(address: TonAddress, bocBase64: String) =
      Result.success(RemoteFeeEstimate(Money.ofNano(nextFeeNano), "TonAPI"))

    override suspend fun sendBoc(network: Network, bocBase64: String): Result<BroadcastResult> {
      lastSentBoc = bocBase64
      sendBocFailure?.let { return Result.failure(it) }
      sendBocCount++
      return Result.success(BroadcastResult("broadcast_${sendBocCount}", "TON Center"))
    }

    override suspend fun lookupMessageStatus(network: Network, messageHashHex: String, normalizedHashHex: String) =
      Result.success(nextLookup)

    override suspend fun lookupJettonTraceStatus(network: Network, messageHashHex: String, normalizedHashHex: String) =
      Result.success(nextLookup)

    override suspend fun fetchAccountHistory(address: TonAddress, afterLt: String?, limit: Int): Result<RemoteTxHistoryPage> {
      historyFailure?.let { return Result.failure(it) }
      return Result.success(RemoteTxHistoryPage(historyTransfers.take(limit), "TON Center"))
    }

    override suspend fun fetchJettonHistory(ownerAddress: TonAddress, spec: AllowedJettonSpec, limit: Int): Result<RemoteJettonHistoryPage> {
      jettonHistoryFailure?.let { return Result.failure(it) }
      return Result.success(RemoteJettonHistoryPage(emptyList(), "TON Center"))
    }
  }
}
