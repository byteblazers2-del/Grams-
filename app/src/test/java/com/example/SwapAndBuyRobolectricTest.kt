package com.example

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.BalanceRepository
import com.example.core.data.GramVaultDatabase
import com.example.core.keystore.AndroidKeyStoreCipherProvider
import com.example.core.keystore.AuthGate
import com.example.core.keystore.KeystoreCipherProvider
import com.example.core.keystore.SecureKeyStore
import com.example.core.keystore.VaultKeyInvalidatedException
import com.example.core.model.AccountOnChainStatus
import com.example.core.model.AllowedJettonSpec
import com.example.core.model.AuthReason
import com.example.core.model.AuthResult
import com.example.core.model.BalanceState
import com.example.core.model.JettonHolding
import com.example.core.model.JettonVerificationStatus
import com.example.core.model.Money
import com.example.core.model.Network
import com.example.core.model.RemoteAccountBalance
import com.example.core.model.RemoteJettonBalance
import com.example.core.model.RemoteJettonHistoryPage
import com.example.core.model.SlippageTolerance
import com.example.core.model.SwapAsset
import com.example.core.model.SwapInternalMessage
import com.example.core.model.SwapPreview
import com.example.core.model.SwapQuote
import com.example.core.model.TonAddress
import com.example.core.model.TxAssetKind
import com.example.core.model.USDT_DECIMALS
import com.example.core.model.VaultMetadata
import com.example.core.model.VaultSecurityMode
import com.example.core.model.VaultState
import com.example.core.network.BroadcastResult
import com.example.core.network.ChainGateway
import com.example.core.network.OnChainMessageStatus
import com.example.core.network.RemoteFeeEstimate
import com.example.core.network.RemoteTxHistoryPage
import com.example.core.network.RemoteTxLookup
import com.example.core.network.RemoteWalletInfo
import com.example.core.network.SwapCoffeeService
import com.example.core.network.SwapEngine
import com.example.core.tx.TxManager
import com.example.core.wallet.VaultMetadataStore
import com.example.core.wallet.WalletCoreImpl
import com.example.ui.screens.SwapConfirmScreen
import com.example.ui.screens.SwapFormScreen
import com.example.ui.screens.WalletHomeScreen
import com.example.ui.theme.GramVaultTheme
import java.io.File
import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.ton.cell.Cell
import org.ton.cell.CellBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SwapAndBuyRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun home_screen_displays_swap_and_buy_buttons_and_opens_buy_sheet() {
    val wallet =
      VaultMetadata(
        publicKeyHex = "ab".repeat(32),
        mainnetRaw = "0:" + "11".repeat(32),
        testnetRaw = "0:" + "11".repeat(32),
        isBackedUp = true,
        createdAtEpochMs = 1_700_000_000_000L,
        securityMode = VaultSecurityMode.DEVICE_KEYSTORE,
        walletId = "w_personal",
        walletName = "Main Wallet",
        isWatchOnly = false,
      )
    var swapOpened = false

    composeTestRule.setContent {
      GramVaultTheme {
        WalletHomeScreen(
          metadata = wallet,
          wallets = listOf(wallet),
          activeNetwork = Network.MAINNET,
          bannerMessage = null,
          onClearBanner = {},
          onOpenBackup = {},
          onLockWallet = {},
          onOpenSwap = { swapOpened = true },
          balanceState =
            BalanceState(
              network = Network.MAINNET,
              address = wallet.addressFor(Network.MAINNET),
              gramBalance = Money.ofNano(15_000_000_000L),
            ),
        )
      }
    }

    composeTestRule.onNodeWithTag("send_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("receive_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("swap_button").assertIsDisplayed().performClick()
    assertTrue(swapOpened)

    // Open Buy sheet and verify TON/USDT toggle, Mercuryo/Onramper cards, and Copy address
    composeTestRule.onNodeWithTag("buy_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("buy_sheet_content").assertIsDisplayed()
    composeTestRule.onNodeWithTag("buy_asset_ton").assertIsDisplayed()
    composeTestRule.onNodeWithTag("buy_asset_usdt").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("buy_provider_mercuryo").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("buy_provider_onramper").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("buy_copy_address_button").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun watch_only_home_screen_hides_swap_and_keeps_buy_button() {
    val watchWallet =
      VaultMetadata(
        publicKeyHex = "",
        mainnetRaw = "0:" + "44".repeat(32),
        testnetRaw = "0:" + "44".repeat(32),
        isBackedUp = true,
        createdAtEpochMs = 1_700_000_000_000L,
        securityMode = VaultSecurityMode.DEVICE_KEYSTORE,
        walletId = "w_watch",
        walletName = "Watch Wallet",
        isWatchOnly = true,
      )

    composeTestRule.setContent {
      GramVaultTheme {
        WalletHomeScreen(
          metadata = watchWallet,
          wallets = listOf(watchWallet),
          activeNetwork = Network.MAINNET,
          bannerMessage = null,
          onClearBanner = {},
          onOpenBackup = {},
          onLockWallet = {},
          balanceState =
            BalanceState(
              network = Network.MAINNET,
              address = watchWallet.addressFor(Network.MAINNET),
              gramBalance = Money.ofNano(5_000_000_000L),
            ),
        )
      }
    }

    composeTestRule.onAllNodesWithTag("send_button").assertCountEquals(0)
    composeTestRule.onAllNodesWithTag("swap_button").assertCountEquals(0)
    composeTestRule.onAllNodesWithTag("backup_phrase_button").assertCountEquals(0)
    composeTestRule.onNodeWithTag("receive_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("buy_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("copy_address_button").assertIsDisplayed()
  }

  @Test
  fun swap_form_and_confirm_screens_render_quote_slippage_impact_warning_and_submit() {
    val defaults = SwapCoffeeService.curatedDefaultAssets()
    val ton = defaults.first { it.isNative }
    val usdt = defaults.first { it.symbol == "USDT" }
    val walletAddr = TonAddress("0:" + "22".repeat(32), Network.MAINNET)

    val highImpactQuote =
      SwapQuote(
        inputAsset = ton,
        outputAsset = usdt,
        inputAmount = Money.ofNano(5_000_000_000L),
        expectedOutputAmount = Money(BigInteger.valueOf(15_400_000L), USDT_DECIMALS),
        minReceivedAmount = Money(BigInteger.valueOf(14_938_000L), USDT_DECIMALS),
        slippage = SlippageTolerance.BPS_300,
        inputUsd = 17.50,
        outputUsd = 15.40,
        priceImpactPercent = 4.2,
        recommendedGas = Money.ofNano(250_000_000L),
        routeDexLabels = listOf("STON.fi", "DeDust"),
        rawPathsJson = "[]",
        fetchedAtEpochMs = 1_750_000_000_000L,
      )

    var selectedSlippage = SlippageTolerance.BPS_300
    var flipped = false
    var reviewClicked = false
    var showConfirm = false
    var confirmClicked = false

    val preview =
      SwapPreview(
        network = Network.MAINNET,
        fromAddress = walletAddr,
        quote = highImpactQuote,
        internalMessages =
          listOf(
            SwapInternalMessage(
              destination = TonAddress("0:" + "99".repeat(32), Network.MAINNET),
              attachedTonNano = BigInteger.valueOf(5_250_000_000L),
              bounce = true,
              payloadCell = Cell.empty(),
              sendMode = 3,
            )
          ),
        totalAttachedTon = Money.ofNano(5_250_000_000L),
        estimatedNetworkFee = Money.ofNano(15_000_000L),
        totalTonRequired = Money.ofNano(5_265_000_000L),
        seqno = 7,
        senderNeedsStateInit = false,
        validUntilEpochSec = 1_900_000_060L,
      )

    composeTestRule.setContent {
      GramVaultTheme {
        if (!showConfirm) {
          SwapFormScreen(
            balanceState =
              BalanceState(
                network = Network.MAINNET,
                address = walletAddr,
                gramBalance = Money.ofNano(12_000_000_000L),
                usdtBalance = Money(BigInteger.valueOf(25_000_000L), USDT_DECIMALS),
              ),
            tokens = defaults,
            inputAsset = ton,
            outputAsset = usdt,
            amountInput = "5",
            slippage = selectedSlippage,
            quote = highImpactQuote,
            isQuoting = false,
            isBusy = false,
            errorMessage = null,
            onSelectInputAsset = {},
            onSelectOutputAsset = {},
            onFlipPair = { flipped = true },
            onAmountChange = {},
            onSlippageChange = { selectedSlippage = it },
            onRefreshQuote = {},
            onReviewSwap = { reviewClicked = true },
            onBack = {},
          )
        } else {
          SwapConfirmScreen(
            preview = preview,
            isBusy = false,
            errorMessage = null,
            onConfirmAndSwap = { confirmClicked = true },
            onBack = {},
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("swap_amount_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("swap_output_amount").assertIsDisplayed()
    composeTestRule.onNodeWithTag("swap_high_impact_warning").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("swap_slippage_500").performScrollTo().performClick()
    assertEquals(SlippageTolerance.BPS_500, selectedSlippage)

    composeTestRule.onNodeWithTag("swap_flip_button").performScrollTo().performClick()
    assertTrue(flipped)

    composeTestRule.onNodeWithTag("swap_review_button").assertIsEnabled().performClick()
    assertTrue(reviewClicked)
  }

  @Test
  fun swap_confirm_screen_displays_details_and_executes_confirm_callback() {
    val defaults = SwapCoffeeService.curatedDefaultAssets()
    val ton = defaults.first { it.isNative }
    val usdt = defaults.first { it.symbol == "USDT" }
    val walletAddr = TonAddress("0:" + "22".repeat(32), Network.MAINNET)

    val quote =
      SwapQuote(
        inputAsset = ton,
        outputAsset = usdt,
        inputAmount = Money.ofNano(2_000_000_000L),
        expectedOutputAmount = Money(BigInteger.valueOf(6_800_000L), USDT_DECIMALS),
        minReceivedAmount = Money(BigInteger.valueOf(6_732_000L), USDT_DECIMALS),
        slippage = SlippageTolerance.BPS_100,
        priceImpactPercent = 0.4,
        recommendedGas = Money.ofNano(200_000_000L),
        routeDexLabels = listOf("STON.fi"),
        rawPathsJson = "[]",
        fetchedAtEpochMs = 1_750_000_000_000L,
      )
    val preview =
      SwapPreview(
        network = Network.MAINNET,
        fromAddress = walletAddr,
        quote = quote,
        internalMessages =
          listOf(
            SwapInternalMessage(
              destination = TonAddress("0:" + "99".repeat(32), Network.MAINNET),
              attachedTonNano = BigInteger.valueOf(2_200_000_000L),
              bounce = true,
              payloadCell = Cell.empty(),
              sendMode = 3,
            )
          ),
        totalAttachedTon = Money.ofNano(2_200_000_000L),
        estimatedNetworkFee = Money.ofNano(12_000_000L),
        totalTonRequired = Money.ofNano(2_212_000_000L),
        seqno = 11,
        senderNeedsStateInit = false,
        validUntilEpochSec = 1_900_000_060L,
      )
    var confirmed = false

    composeTestRule.setContent {
      GramVaultTheme {
        SwapConfirmScreen(
          preview = preview,
          isBusy = false,
          errorMessage = null,
          onConfirmAndSwap = { confirmed = true },
          onBack = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("swap_confirm_pair_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("#11").assertIsDisplayed()
    composeTestRule.onNodeWithText("STON.fi").assertIsDisplayed()
    composeTestRule.onNodeWithTag("swap_confirm_button").assertIsEnabled().performClick()
    assertTrue(confirmed)
  }

  @Test
  fun wallet_core_executes_end_to_end_swap_and_guards_seqno_and_watch_only() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    File(context.filesDir, "vault").deleteRecursively()
    val db =
      Room.inMemoryDatabaseBuilder(context, GramVaultDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    try {
      val nowMs = 1_800_000_000_000L
      val authGate = StubAuthGate(deviceSecure = true, nextResult = AuthResult.Authenticated)
      val keyStore = SecureKeyStore(context, InMemoryAesCipherProvider())
      val metaStore = VaultMetadataStore(context)
      val gateway = FakeSwapChainGateway()
      val swapEngine = FakeSwapEngine()
      val txManager = TxManager(txDao = db.txDao(), chainGateway = gateway, clockMs = { nowMs })

      val core =
        WalletCoreImpl(
          applicationContext = context,
          authGate = authGate,
          secureKeyStore = keyStore,
          metadataStore = metaStore,
          txManager = txManager,
          swapEngine = swapEngine,
          devSkipAuth = false,
          clockMs = { nowMs },
        )

      val created = core.createWallet(Network.MAINNET).getOrThrow()
      created.mnemonic.wipe()
      gateway.senderAddressRaw = created.mainnetAddress.raw
      gateway.senderBalance = Money.ofNano(10_000_000_000L)
      gateway.senderSeqno = 4

      val defaults = core.fetchSwapTokens()
      val ton = defaults.first { it.isNative }
      val usdt = defaults.first { it.symbol == "USDT" }

      val quote = core.quoteSwap(ton, usdt, "3.0", SlippageTolerance.BPS_100).getOrThrow()
      val preview = core.prepareSwapReview(quote, SlippageTolerance.BPS_100).getOrThrow()
      assertEquals(4, preview.seqno)
      assertEquals(2, preview.internalMessages.size)

      // Seqno mismatch safeguard aborts before decrypting seed
      gateway.senderSeqno = 5
      val staleResult = core.confirmAndExecuteSwap(preview)
      assertTrue(staleResult.isFailure)

      // Restore seqno and execute swap cleanly
      gateway.senderSeqno = 4
      val txRecord = core.confirmAndExecuteSwap(preview).getOrThrow()
      assertEquals(TxAssetKind.GRAM, txRecord.kind)
      assertTrue(txRecord.comment?.contains("Swap") == true)
      assertEquals(1, gateway.sentBocs.size)

      // Watch-Only cannot prepare or confirm swaps
      val watchFriendly = TonAddress("0:" + "77".repeat(32), Network.MAINNET).toFriendly(false)
      core.addWatchOnlyWallet(watchFriendly, "Watch").getOrThrow()
      assertTrue(core.prepareSwapReview(quote, SlippageTolerance.BPS_100).isFailure)
      assertTrue(core.confirmAndExecuteSwap(preview).isFailure)
    } finally {
      db.close()
    }
  }

  private class FakeSwapEngine : SwapEngine {
    override suspend fun fetchAvailableTokens(userHoldings: List<JettonHolding>): List<SwapAsset> =
      SwapCoffeeService.curatedDefaultAssets()

    override suspend fun fetchQuote(
      inputAsset: SwapAsset,
      outputAsset: SwapAsset,
      inputAmount: Money,
      slippage: SlippageTolerance,
    ): Result<SwapQuote> =
      Result.success(
        SwapQuote(
          inputAsset = inputAsset,
          outputAsset = outputAsset,
          inputAmount = inputAmount,
          expectedOutputAmount = Money(BigInteger.valueOf(10_000_000L), outputAsset.decimals),
          minReceivedAmount = Money(BigInteger.valueOf(9_900_000L), outputAsset.decimals),
          slippage = slippage,
          recommendedGas = Money.ofNano(250_000_000L),
          routeDexLabels = listOf("STON.fi", "DeDust"),
          rawPathsJson = "[{\"dex\":\"stonfi\"}]",
          fetchedAtEpochMs = 1_800_000_000_000L,
        )
      )

    override suspend fun buildSwapMessages(
      senderAddress: TonAddress,
      quote: SwapQuote,
      slippage: SlippageTolerance,
    ): Result<List<SwapInternalMessage>> {
      val routerA = TonAddress("0:" + "81".repeat(32), Network.MAINNET)
      val routerB = TonAddress("0:" + "82".repeat(32), Network.MAINNET)
      val payload = CellBuilder.createCell { storeUInt(0xABCDEF01L, 32) }
      return Result.success(
        listOf(
          SwapInternalMessage(
            destination = routerA,
            attachedTonNano = BigInteger.valueOf(1_600_000_000L),
            bounce = true,
            payloadCell = payload,
          ),
          SwapInternalMessage(
            destination = routerB,
            attachedTonNano = BigInteger.valueOf(1_600_000_000L),
            bounce = true,
            payloadCell = payload,
          ),
        )
      )
    }
  }

  private class FakeSwapChainGateway : ChainGateway {
    var senderAddressRaw: String = ""
    var senderBalance: Money = Money.ofNano(10_000_000_000L)
    var senderStatus: AccountOnChainStatus = AccountOnChainStatus.ACTIVE
    var senderSeqno: Int = 0
    val sentBocs = mutableListOf<String>()

    override suspend fun fetchAccountBalance(address: TonAddress) =
      Result.success(RemoteAccountBalance(address, senderBalance, senderStatus, "Fake", 0L))

    override suspend fun fetchJettonBalance(ownerAddress: TonAddress, spec: AllowedJettonSpec) =
      Result.success(
        RemoteJettonBalance(ownerAddress, spec.masterAddress, null, Money.zero(spec.decimals), "Fake", 0L)
      )

    override suspend fun fetchWalletInfo(address: TonAddress) =
      Result.success(RemoteWalletInfo(address, senderBalance, senderStatus, senderSeqno, "Fake", 0L))

    override suspend fun estimateExternalMessageFee(address: TonAddress, bocBase64: String) =
      Result.success(RemoteFeeEstimate(Money.ofNano(15_000_000L), "Fake"))

    override suspend fun sendBoc(network: Network, bocBase64: String): Result<BroadcastResult> {
      sentBocs += bocBase64
      return Result.success(BroadcastResult("hash_${sentBocs.size}", "Fake"))
    }

    override suspend fun lookupMessageStatus(network: Network, messageHashHex: String, normalizedHashHex: String) =
      Result.success(RemoteTxLookup(OnChainMessageStatus.PENDING, null, null, null, "Fake"))

    override suspend fun lookupJettonTraceStatus(network: Network, messageHashHex: String, normalizedHashHex: String) =
      Result.success(RemoteTxLookup(OnChainMessageStatus.PENDING, null, null, null, "Fake"))

    override suspend fun fetchAccountHistory(address: TonAddress, afterLt: String?, limit: Int) =
      Result.success(RemoteTxHistoryPage(emptyList(), "Fake"))

    override suspend fun fetchJettonHistory(ownerAddress: TonAddress, spec: AllowedJettonSpec, limit: Int) =
      Result.success(RemoteJettonHistoryPage(emptyList(), "Fake"))
  }

  private class StubAuthGate(
    var deviceSecure: Boolean,
    var nextResult: AuthResult,
  ) : AuthGate {
    override fun isDeviceSecure(): Boolean = deviceSecure
    override suspend fun authenticate(reason: AuthReason): AuthResult = nextResult
  }

  private class InMemoryAesCipherProvider : KeystoreCipherProvider {
    private var authKeyBytes: ByteArray? = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var hwKeyBytes: ByteArray? = ByteArray(32).also { SecureRandom().nextBytes(it) }

    override fun ensureKeyExists(requireUserAuth: Boolean) {
      if (requireUserAuth && authKeyBytes == null) {
        authKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
      }
      if (!requireUserAuth && hwKeyBytes == null) {
        hwKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
      }
    }

    override fun encrypt(plaintext: ByteArray, requireUserAuth: Boolean): ByteArray {
      ensureKeyExists(requireUserAuth)
      val key = if (requireUserAuth) authKeyBytes else hwKeyBytes
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
      return AndroidKeyStoreCipherProvider.encodeEnvelope(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(envelope: ByteArray, requireUserAuth: Boolean): ByteArray {
      val key = (if (requireUserAuth) authKeyBytes else hwKeyBytes) ?: throw VaultKeyInvalidatedException()
      val (iv, ct) = AndroidKeyStoreCipherProvider.decodeEnvelope(envelope)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
      return cipher.doFinal(ct)
    }

    override fun deleteKey(requireUserAuth: Boolean) {
      if (requireUserAuth) authKeyBytes = null else hwKeyBytes = null
    }
  }
}
