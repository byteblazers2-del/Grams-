package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.BalanceRepository
import com.example.core.data.GramVaultDatabase
import com.example.core.keystore.AndroidKeyStoreCipherProvider
import com.example.core.keystore.AuthGate
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
import com.example.core.model.TransferPreview
import com.example.core.model.TxAssetKind
import com.example.core.model.TxDirection
import com.example.core.model.TxStatus
import com.example.core.model.USDT_DECIMALS
import com.example.core.model.VaultMetadata
import com.example.core.network.BroadcastResult
import com.example.core.network.ChainGateway
import com.example.core.network.FiatRates
import com.example.core.network.OnChainMessageStatus
import com.example.core.network.RemoteChainTransfer
import com.example.core.network.RemoteFeeEstimate
import com.example.core.network.RemoteTxHistoryPage
import com.example.core.network.RemoteTxLookup
import com.example.core.network.RemoteWalletInfo
import com.example.core.tx.TxManager
import com.example.core.wallet.AddressCheckResult
import com.example.core.wallet.VaultMetadataStore
import com.example.core.wallet.WalletCoreImpl
import com.example.ui.screens.SendConfirmScreen
import com.example.ui.screens.SendFormScreen
import com.example.ui.screens.WalletHomeScreen
import com.example.ui.theme.GramVaultTheme
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiJettonRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun balance_repository_caches_sorts_and_filters_multiple_jettons_in_room_v5() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db =
      Room.inMemoryDatabaseBuilder(context, GramVaultDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    try {
      val owner = TonAddress("0:" + "11".repeat(32), Network.MAINNET)
      val notMaster = TonAddress("0:" + "22".repeat(32), Network.MAINNET)
      val dogsMaster = TonAddress("0:" + "33".repeat(32), Network.MAINNET)
      val badMaster = TonAddress("0:" + "44".repeat(32), Network.MAINNET)
      val usdtSpec = JettonAllowList.MAINNET_USDT

      val gateway = FakeMultiJettonGateway(nowProvider = { 1_715_000_000_000L })
      gateway.senderAddressRaw = owner.raw
      gateway.holdings =
        listOf(
          JettonHolding(
            masterAddress = dogsMaster,
            jettonWalletAddress = TonAddress("0:" + "63".repeat(32), Network.MAINNET),
            symbol = "DOGS",
            name = "Dogs Community",
            balance = Money(BigInteger.valueOf(3500L), 0),
            imageUrl = null,
            verification = JettonVerificationStatus.UNVERIFIED,
            providerName = "TonAPI",
            fetchedAtEpochMs = 1_715_000_000_000L,
          ),
          JettonHolding(
            masterAddress = badMaster,
            jettonWalletAddress = TonAddress("0:" + "64".repeat(32), Network.MAINNET),
            symbol = "BAD",
            name = "Blacklisted",
            balance = Money(BigInteger.valueOf(999L), 9),
            imageUrl = null,
            verification = JettonVerificationStatus.BLACKLISTED,
            providerName = "TonAPI",
            fetchedAtEpochMs = 1_715_000_000_000L,
          ),
          JettonHolding(
            masterAddress = notMaster,
            jettonWalletAddress = TonAddress("0:" + "62".repeat(32), Network.MAINNET),
            symbol = "NOT",
            name = "Notcoin",
            balance = Money.fromDecimalString("250.5", 9),
            imageUrl = "https://cdn.example.com/not.png",
            verification = JettonVerificationStatus.VERIFIED,
            providerName = "TonAPI",
            fetchedAtEpochMs = 1_715_000_000_000L,
          ),
          JettonHolding(
            masterAddress = usdtSpec.masterAddress,
            jettonWalletAddress = TonAddress("0:" + "61".repeat(32), Network.MAINNET),
            symbol = usdtSpec.symbol,
            name = usdtSpec.name,
            balance = Money.fromDecimalString("18.25", USDT_DECIMALS),
            imageUrl = "https://cdn.tether.to/usdt.png",
            verification = JettonVerificationStatus.ALLOWLISTED,
            providerName = "TonAPI",
            fetchedAtEpochMs = 1_715_000_000_000L,
          ),
        )

      val repo =
        BalanceRepository(
          balanceDao = db.balanceCacheDao(),
          balanceFetcher = gateway,
          clockMs = { 1_715_000_000_000L },
          jettonCacheDao = db.jettonBalanceCacheDao(),
        )
      repo.refreshBalance(owner, force = true).getOrThrow()

      val state = repo.observeBalance(owner).first { it.gramBalance != null && it.jettonHoldings.size == 3 }
      assertEquals("18.25", state.usdtBalance?.format())
      // Sort order: ALLOWLISTED USDT -> VERIFIED NOT -> UNVERIFIED DOGS (BLACKLISTED dropped)
      assertEquals(listOf("USDT", "NOT", "DOGS"), state.jettonHoldings.map { it.symbol })
      assertEquals("250.50", state.holdingFor(notMaster.raw)?.balance?.format())
      assertEquals("3500", state.holdingFor(dogsMaster.raw)?.balance?.format())
      assertEquals(null, state.holdingFor(badMaster.raw))
    } finally {
      db.close()
    }
  }

  @Test
  fun tx_manager_sync_detects_non_usdt_jetton_deposits_filters_blacklist_and_isolates_masters() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        Room.inMemoryDatabaseBuilder(context, GramVaultDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      try {
        val owner = TonAddress("0:" + "11".repeat(32), Network.MAINNET)
        val counterparty = TonAddress("0:" + "77".repeat(32), Network.MAINNET)
        val notMasterRaw = "0:" + "22".repeat(32)
        val badMasterRaw = "0:" + "44".repeat(32)

        val gateway = FakeMultiJettonGateway(nowProvider = { 1_715_000_000_000L })
        gateway.senderAddressRaw = owner.raw
        gateway.allJettonHistoryTransfers =
          listOf(
            RemoteJettonTransfer(
              txHash = "not_in_hash_1",
              lt = "12001",
              utimeEpochSec = 1_715_000_100L,
              direction = TxDirection.IN,
              jettonMasterRaw = notMasterRaw,
              amount = Money.fromDecimalString("250", 9),
              counterpartyRaw = counterparty.raw,
              comment = "Reward NOT",
              status = OnChainMessageStatus.CONFIRMED,
              jettonSymbol = "NOT",
              jettonName = "Notcoin",
              jettonImageUrl = "https://cdn.example.com/not.png",
              jettonVerification = JettonVerificationStatus.VERIFIED,
            ),
            RemoteJettonTransfer(
              txHash = "bad_spam_hash",
              lt = "12002",
              utimeEpochSec = 1_715_000_110L,
              direction = TxDirection.IN,
              jettonMasterRaw = badMasterRaw,
              amount = Money.fromDecimalString("10000", 9),
              counterpartyRaw = counterparty.raw,
              comment = "Spam",
              status = OnChainMessageStatus.CONFIRMED,
              jettonSymbol = "SCAM",
              jettonName = "Blacklisted Scam",
              jettonImageUrl = null,
              jettonVerification = JettonVerificationStatus.BLACKLISTED,
            ),
          )

        val txManager =
          TxManager(
            txDao = db.txDao(),
            chainGateway = gateway,
            clockMs = { 1_715_000_000_000L },
          )

        val summary = txManager.syncAccountHistory(owner, limit = 25).getOrThrow()
        assertEquals(1, summary.newIncomingTxs.size)
        val incomingNot = summary.newIncomingTxs.first()
        assertEquals(TxAssetKind.JETTON, incomingNot.kind)
        assertEquals("NOT", incomingNot.assetSymbol)
        assertEquals("Notcoin", incomingNot.jettonName)
        assertEquals("250.00", incomingNot.amount.format())
        assertEquals(JettonVerificationStatus.VERIFIED, incomingNot.jettonVerification)

        val persisted = db.txDao().observeByAccount(owner.raw, Network.MAINNET.name).first()
        assertEquals(1, persisted.size)
        assertEquals(notMasterRaw, persisted.first().jettonMaster)
        assertEquals("NOT", persisted.first().jettonSymbol)
      } finally {
        db.close()
      }
    }

  @Test
  fun wallet_core_prepares_and_sends_custom_jetton_and_blocks_blacklisted_master() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    File(context.filesDir, "vault").deleteRecursively()
    val db =
      Room.inMemoryDatabaseBuilder(context, GramVaultDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val txScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      var nowMs = 1_715_000_000_000L
      val authGate = MultiTestAuthGate()
      val cipherProvider = MultiTestCipherProvider()
      val gateway = FakeMultiJettonGateway(nowProvider = { nowMs })
      val balanceRepo =
        BalanceRepository(
          balanceDao = db.balanceCacheDao(),
          balanceFetcher = gateway,
          clockMs = { nowMs },
          jettonCacheDao = db.jettonBalanceCacheDao(),
        )
      val txManager =
        TxManager(
          txDao = db.txDao(),
          chainGateway = gateway,
          balanceRepository = balanceRepo,
          clockMs = { nowMs },
          scope = txScope,
        )
      val core =
        WalletCoreImpl(
          applicationContext = context,
          authGate = authGate,
          secureKeyStore = SecureKeyStore(context, cipherProvider),
          metadataStore = VaultMetadataStore(context),
          balanceRepository = balanceRepo,
          txManager = txManager,
          clockMs = { nowMs },
        )

      val created = core.createWallet(Network.MAINNET).getOrThrow()
      created.mnemonic.wipe()
      val sender = created.mainnetAddress
      val recipient = TonAddress("0:" + "88".repeat(32), Network.MAINNET).toFriendly(bounceable = false)
      val notMaster = TonAddress("0:" + "22".repeat(32), Network.MAINNET)
      val badMaster = TonAddress("0:" + "44".repeat(32), Network.MAINNET)

      gateway.senderAddressRaw = sender.raw
      gateway.senderBalance = Money.ofNano(5_000_000_000L) // 5 GRAM
      gateway.holdings =
        listOf(
          JettonHolding(
            masterAddress = notMaster,
            jettonWalletAddress = TonAddress("0:" + "62".repeat(32), Network.MAINNET),
            symbol = "NOT",
            name = "Notcoin",
            balance = Money.fromDecimalString("100", 9),
            imageUrl = "https://cdn.example.com/not.png",
            verification = JettonVerificationStatus.VERIFIED,
            providerName = "TonAPI",
            fetchedAtEpochMs = nowMs,
          ),
          JettonHolding(
            masterAddress = badMaster,
            jettonWalletAddress = TonAddress("0:" + "64".repeat(32), Network.MAINNET),
            symbol = "BAD",
            name = "Blacklisted",
            balance = Money.fromDecimalString("50", 9),
            imageUrl = null,
            verification = JettonVerificationStatus.BLACKLISTED,
            providerName = "TonAPI",
            fetchedAtEpochMs = nowMs,
          ),
        )

      // Blacklisted master must be rejected
      val blocked =
        core.prepareTransfer(
          recipientInput = recipient,
          amountText = "5",
          comment = null,
          assetKind = TxAssetKind.JETTON,
          jettonMasterRaw = badMaster.raw,
        )
      assertTrue(blocked.isFailure)

      // Insufficient NOT balance must be rejected
      val overdraw =
        core.prepareTransfer(
          recipientInput = recipient,
          amountText = "250",
          comment = null,
          assetKind = TxAssetKind.JETTON,
          jettonMasterRaw = notMaster.raw,
        )
      assertTrue(overdraw.isFailure)

      // Valid NOT transfer -> prepares TransferPreview + sends TEP-74 BOC
      val preview =
        core.prepareTransfer(
          recipientInput = recipient,
          amountText = "12.5",
          comment = "Send NOT",
          assetKind = TxAssetKind.JETTON,
          jettonMasterRaw = notMaster.raw,
        ).getOrThrow()

      assertEquals(TxAssetKind.JETTON, preview.assetKind)
      assertEquals(notMaster.raw, preview.jettonMasterRaw)
      assertEquals("NOT", preview.assetSymbol)
      assertEquals("Notcoin", preview.jettonName)
      assertEquals(9, preview.amount.decimals)
      assertEquals("12.50", preview.amount.format())
      assertEquals(JettonVerificationStatus.VERIFIED, preview.jettonVerification)

      val txRecord = core.confirmAndSend(preview).getOrThrow()
      assertEquals(TxStatus.BROADCASTED, txRecord.state)
      assertEquals(notMaster.raw, txRecord.jettonMaster)
      assertEquals("NOT", txRecord.assetSymbol)
      assertEquals("12.50", txRecord.amount.format())
      assertEquals(1, gateway.sendBocCount)
    } finally {
      txScope.cancel()
      db.close()
    }
  }

  @Test
  fun home_send_form_and_confirm_render_multi_jettons_verification_badges_and_no_fake_fiat() {
    val owner = TonAddress("0:" + "11".repeat(32), Network.MAINNET)
    val notMaster = TonAddress("0:" + "22".repeat(32), Network.MAINNET)
    val memeMaster = TonAddress("0:" + "33".repeat(32), Network.MAINNET)
    val usdtSpec = JettonAllowList.MAINNET_USDT

    val metadata =
      VaultMetadata(
        publicKeyHex = "00".repeat(32),
        mainnetRaw = owner.raw,
        testnetRaw = owner.raw,
        isBackedUp = true,
        createdAtEpochMs = 1_715_000_000_000L,
      )
    val balanceState =
      BalanceState(
        network = Network.MAINNET,
        address = owner,
        gramBalance = Money.ofNano(5_000_000_000L),
        usdtBalance = Money.fromDecimalString("10.00", USDT_DECIMALS),
        usdtJettonWallet = TonAddress("0:" + "61".repeat(32), Network.MAINNET),
        jettonHoldings =
          listOf(
            JettonHolding(
              masterAddress = usdtSpec.masterAddress,
              jettonWalletAddress = TonAddress("0:" + "61".repeat(32), Network.MAINNET),
              symbol = usdtSpec.symbol,
              name = usdtSpec.name,
              balance = Money.fromDecimalString("10.00", USDT_DECIMALS),
              verification = JettonVerificationStatus.ALLOWLISTED,
              fetchedAtEpochMs = 1_715_000_000_000L,
            ),
            JettonHolding(
              masterAddress = notMaster,
              jettonWalletAddress = TonAddress("0:" + "62".repeat(32), Network.MAINNET),
              symbol = "NOT",
              name = "Notcoin",
              balance = Money.fromDecimalString("125.5", 9),
              imageUrl = null,
              verification = JettonVerificationStatus.VERIFIED,
              fetchedAtEpochMs = 1_715_000_000_000L,
            ),
            JettonHolding(
              masterAddress = memeMaster,
              jettonWalletAddress = TonAddress("0:" + "63".repeat(32), Network.MAINNET),
              symbol = "MEME",
              name = "Community Meme",
              balance = Money(BigInteger.valueOf(4200L), 0),
              imageUrl = null,
              verification = JettonVerificationStatus.UNVERIFIED,
              fetchedAtEpochMs = 1_715_000_000_000L,
            ),
          ),
      )
    val rates = FiatRates(gramUsdPrice = BigDecimal("5.00"), usdtUsdPrice = BigDecimal.ONE)

    // 1. WalletHomeScreen lists GRAM, USDT, Notcoin (Verified), and Community Meme (Unverified)
    composeTestRule.setContent {
      GramVaultTheme {
        WalletHomeScreen(
          metadata = metadata,
          activeNetwork = Network.MAINNET,
          bannerMessage = null,
          onClearBanner = {},
          onOpenBackup = {},
          onLockWallet = {},
          balanceState = balanceState,
          fiatRates = rates,
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Notcoin").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Community Meme").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Unverified").performScrollTo().assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("asset_jetton_balance_${notMaster.raw.takeLast(8)}")
      .performScrollTo()
      .assertTextContains("125.50 NOT")
  }

  @Test
  fun send_form_switches_jetton_fills_exact_max_and_omits_fiat_for_non_usdt() {
    val owner = TonAddress("0:" + "11".repeat(32), Network.MAINNET)
    val notMaster = TonAddress("0:" + "22".repeat(32), Network.MAINNET)
    val memeMaster = TonAddress("0:" + "33".repeat(32), Network.MAINNET)
    val usdtSpec = JettonAllowList.MAINNET_USDT
    val balanceState =
      BalanceState(
        network = Network.MAINNET,
        address = owner,
        gramBalance = Money.ofNano(5_000_000_000L),
        usdtBalance = Money.fromDecimalString("10.00", USDT_DECIMALS),
        usdtJettonWallet = TonAddress("0:" + "61".repeat(32), Network.MAINNET),
        jettonHoldings =
          listOf(
            JettonHolding(
              masterAddress = usdtSpec.masterAddress,
              jettonWalletAddress = TonAddress("0:" + "61".repeat(32), Network.MAINNET),
              symbol = usdtSpec.symbol,
              name = usdtSpec.name,
              balance = Money.fromDecimalString("10.00", USDT_DECIMALS),
              verification = JettonVerificationStatus.ALLOWLISTED,
              fetchedAtEpochMs = 1_715_000_000_000L,
            ),
            JettonHolding(
              masterAddress = notMaster,
              jettonWalletAddress = TonAddress("0:" + "62".repeat(32), Network.MAINNET),
              symbol = "NOT",
              name = "Notcoin",
              balance = Money.fromDecimalString("125.5", 9),
              verification = JettonVerificationStatus.VERIFIED,
              fetchedAtEpochMs = 1_715_000_000_000L,
            ),
            JettonHolding(
              masterAddress = memeMaster,
              jettonWalletAddress = TonAddress("0:" + "63".repeat(32), Network.MAINNET),
              symbol = "MEME",
              name = "Community Meme",
              balance = Money(BigInteger.valueOf(4200L), 0),
              verification = JettonVerificationStatus.UNVERIFIED,
              fetchedAtEpochMs = 1_715_000_000_000L,
            ),
          ),
      )
    val rates = FiatRates(gramUsdPrice = BigDecimal("5.00"), usdtUsdPrice = BigDecimal.ONE)

    var amountText = ""
    composeTestRule.setContent {
      var uiAsset by remember { mutableStateOf(TxAssetKind.JETTON) }
      var uiMaster by remember { mutableStateOf<String?>(memeMaster.raw) }
      var uiAmount by remember { mutableStateOf("") }
      GramVaultTheme {
        SendFormScreen(
          activeNetwork = Network.MAINNET,
          balanceState = balanceState,
          recipientInput = "",
          amountInput = uiAmount,
          commentInput = "",
          isBusy = false,
          errorMessage = null,
          onRecipientChange = {},
          onAmountChange = {
            uiAmount = it
            amountText = it
          },
          onCommentChange = {},
          onValidateRecipient = { AddressCheckResult.Invalid("empty") },
          onReviewTransfer = {},
          onBack = {},
          selectedAsset = uiAsset,
          selectedJettonMaster = uiMaster,
          onAssetSelected = { kind, master ->
            uiAsset = kind
            uiMaster = master
          },
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("send_unverified_warning").assertIsDisplayed()
    composeTestRule.onNodeWithTag("send_amount_usd_estimate").assertDoesNotExist()
    composeTestRule.onNodeWithTag("send_max_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals("4200", amountText)

    // Switch chip to NOT and tap MAX
    composeTestRule
      .onNodeWithTag("send_asset_jetton_${notMaster.raw.takeLast(8)}")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("send_available_balance_text").assertTextContains("125.50 NOT", substring = true)
    composeTestRule.onNodeWithTag("send_max_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals("125.50", amountText)
  }

  @Test
  fun send_confirm_displays_unverified_warning_and_dynamic_symbol() {
    val owner = TonAddress("0:" + "11".repeat(32), Network.MAINNET)
    val memeMaster = TonAddress("0:" + "33".repeat(32), Network.MAINNET)
    val rates = FiatRates(gramUsdPrice = BigDecimal("5.00"), usdtUsdPrice = BigDecimal.ONE)
    val recipient = TonAddress.parseDetailed("0:" + "88".repeat(32), Network.MAINNET).getOrThrow()
    val preview =
      TransferPreview(
        network = Network.MAINNET,
        fromAddress = owner,
        destination = recipient,
        destinationStatus = AccountOnChainStatus.ACTIVE,
        effectiveBounce = true,
        amount = Money(BigInteger.valueOf(4200L), 0),
        estimatedFee = Money.ofNano(5_000_000L),
        totalDebit = Money(BigInteger.valueOf(4200L), 0),
        comment = null,
        seqno = 1,
        senderNeedsStateInit = false,
        validUntilEpochSec = 1_800_000_000L,
        assetKind = TxAssetKind.JETTON,
        jettonMasterRaw = memeMaster.raw,
        attachedGramNano = Money.ofNano(55_000_000L),
        jettonSymbol = "MEME",
        jettonName = "Community Meme",
        jettonImageUrl = null,
        jettonVerification = JettonVerificationStatus.UNVERIFIED,
      )
    composeTestRule.setContent {
      GramVaultTheme {
        SendConfirmScreen(
          preview = preview,
          isBusy = false,
          errorMessage = null,
          onConfirmAndSend = {},
          onBack = {},
          fiatRates = rates,
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("confirm_verified_usdt_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText("Unverified Jetton (MEME)", substring = true).assertIsDisplayed()
    composeTestRule.onAllNodesWithText("4200 MEME", substring = true).assertCountEquals(2)
  }

  private class MultiTestAuthGate : AuthGate {
    override fun isDeviceSecure(): Boolean = true

    override suspend fun authenticate(reason: AuthReason): AuthResult = AuthResult.Authenticated
  }

  private class MultiTestCipherProvider : KeystoreCipherProvider {
    private var authKeyBytes: ByteArray? = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var hwKeyBytes: ByteArray? = ByteArray(32).also { SecureRandom().nextBytes(it) }
    var isAuthenticated: Boolean = true
    var keyInvalidated: Boolean = false

    override fun ensureKeyExists(requireUserAuth: Boolean) {
      if (requireUserAuth && authKeyBytes == null) {
        authKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
      }
      if (!requireUserAuth && hwKeyBytes == null) {
        hwKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
      }
    }

    override fun encrypt(plaintext: ByteArray, requireUserAuth: Boolean): ByteArray {
      if (keyInvalidated) throw VaultKeyInvalidatedException()
      if (requireUserAuth && !isAuthenticated) throw VaultAuthRequiredException()
      ensureKeyExists(requireUserAuth)
      val key = if (requireUserAuth) authKeyBytes else hwKeyBytes
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
      return AndroidKeyStoreCipherProvider.encodeEnvelope(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(envelope: ByteArray, requireUserAuth: Boolean): ByteArray {
      if (keyInvalidated) throw VaultKeyInvalidatedException()
      if (requireUserAuth && !isAuthenticated) throw VaultAuthRequiredException()
      val key =
        (if (requireUserAuth) authKeyBytes else hwKeyBytes) ?: throw VaultKeyInvalidatedException()
      val (iv, ct) = AndroidKeyStoreCipherProvider.decodeEnvelope(envelope)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
      return cipher.doFinal(ct)
    }

    override fun deleteKey(requireUserAuth: Boolean) {
      if (requireUserAuth) authKeyBytes = null else hwKeyBytes = null
    }
  }

  private class FakeMultiJettonGateway(
    private val nowProvider: () -> Long,
  ) : ChainGateway {
    var senderAddressRaw: String = ""
    var senderBalance: Money = Money.ofNano(10_000_000_000L)
    var senderStatus: AccountOnChainStatus = AccountOnChainStatus.ACTIVE
    var senderSeqno: Int = 0
    var holdings: List<JettonHolding> = emptyList()
    var historyTransfers: List<RemoteChainTransfer> = emptyList()
    var allJettonHistoryTransfers: List<RemoteJettonTransfer> = emptyList()
    var sendBocCount: Int = 0

    override suspend fun fetchAccountBalance(address: TonAddress): Result<RemoteAccountBalance> =
      Result.success(
        RemoteAccountBalance(
          address = address,
          balance = if (address.raw == senderAddressRaw) senderBalance else Money.zero(),
          status =
            if (address.raw == senderAddressRaw) senderStatus else AccountOnChainStatus.ACTIVE,
          providerName = "TonAPI",
          fetchedAtEpochMs = nowProvider(),
        )
      )

    override suspend fun fetchJettonBalance(
      ownerAddress: TonAddress,
      spec: AllowedJettonSpec,
    ): Result<RemoteJettonBalance> {
      val match = holdings.firstOrNull { it.masterRaw == spec.masterAddress.raw }
      return Result.success(
        RemoteJettonBalance(
          ownerAddress = ownerAddress,
          jettonMaster = spec.masterAddress,
          jettonWalletAddress = match?.jettonWalletAddress,
          balance = match?.balance ?: Money.zero(spec.decimals),
          providerName = "TonAPI",
          fetchedAtEpochMs = nowProvider(),
        )
      )
    }

    override suspend fun fetchAllJettons(
      ownerAddress: TonAddress
    ): Result<RemoteJettonHoldingsPage> =
      Result.success(
        RemoteJettonHoldingsPage(
          holdings = holdings,
          providerName = "TonAPI",
          fetchedAtEpochMs = nowProvider(),
        )
      )

    override suspend fun fetchWalletInfo(address: TonAddress): Result<RemoteWalletInfo> =
      Result.success(
        RemoteWalletInfo(
          address = address,
          balance = if (address.raw == senderAddressRaw) senderBalance else Money.zero(),
          status =
            if (address.raw == senderAddressRaw) senderStatus else AccountOnChainStatus.ACTIVE,
          seqno = if (address.raw == senderAddressRaw) senderSeqno else 0,
          providerName = "TonAPI",
          fetchedAtEpochMs = nowProvider(),
        )
      )

    override suspend fun estimateExternalMessageFee(
      address: TonAddress,
      bocBase64: String,
    ): Result<RemoteFeeEstimate> =
      Result.success(RemoteFeeEstimate(Money.ofNano(5_000_000L), "TonAPI"))

    override suspend fun sendBoc(network: Network, bocBase64: String): Result<BroadcastResult> {
      sendBocCount++
      return Result.success(BroadcastResult("multi_hash_$sendBocCount", "TonAPI"))
    }

    override suspend fun lookupMessageStatus(
      network: Network,
      messageHashHex: String,
      normalizedHashHex: String,
    ): Result<RemoteTxLookup> =
      Result.success(RemoteTxLookup(OnChainMessageStatus.PENDING, null, null, null, "TonAPI"))

    override suspend fun lookupJettonTraceStatus(
      network: Network,
      messageHashHex: String,
      normalizedHashHex: String,
    ): Result<RemoteTxLookup> =
      Result.success(RemoteTxLookup(OnChainMessageStatus.PENDING, null, null, null, "TonAPI"))

    override suspend fun fetchAccountHistory(
      address: TonAddress,
      afterLt: String?,
      limit: Int,
    ): Result<RemoteTxHistoryPage> =
      Result.success(RemoteTxHistoryPage(historyTransfers.take(limit), "TonAPI"))

    override suspend fun fetchJettonHistory(
      ownerAddress: TonAddress,
      spec: AllowedJettonSpec,
      limit: Int,
    ): Result<RemoteJettonHistoryPage> =
      Result.success(
        RemoteJettonHistoryPage(
          allJettonHistoryTransfers.filter { it.jettonMasterRaw == spec.masterAddress.raw }.take(limit),
          "TonAPI",
        )
      )

    override suspend fun fetchAllJettonHistory(
      ownerAddress: TonAddress,
      limit: Int,
    ): Result<RemoteJettonHistoryPage> =
      Result.success(RemoteJettonHistoryPage(allJettonHistoryTransfers.take(limit), "TonAPI"))
  }
}
