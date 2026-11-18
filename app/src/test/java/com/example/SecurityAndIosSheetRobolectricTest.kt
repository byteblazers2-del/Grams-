package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.WalletCoreFactory
import com.example.core.data.GramVaultDatabase
import com.example.core.keystore.AndroidKeyStoreCipherProvider
import com.example.core.keystore.AuthGate
import com.example.core.keystore.KeystoreCipherProvider
import com.example.core.keystore.SecureKeyStore
import com.example.core.keystore.VaultAuthRequiredException
import com.example.core.keystore.VaultKeyInvalidatedException
import com.example.core.keystore.VaultWrongPinException
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
import com.example.core.model.RemoteJettonHoldingsPage
import com.example.core.model.TonAddress
import com.example.core.model.USDT_DECIMALS
import com.example.core.model.VaultMetadata
import com.example.core.model.VaultSecurityMode
import com.example.core.network.BroadcastResult
import com.example.core.network.ChainGateway
import com.example.core.network.OnChainMessageStatus
import com.example.core.network.RemoteFeeEstimate
import com.example.core.network.RemoteTxHistoryPage
import com.example.core.network.RemoteTxLookup
import com.example.core.network.RemoteWalletInfo
import com.example.core.tx.TxManager
import com.example.core.wallet.VaultMetadataStore
import com.example.core.wallet.WalletCoreImpl
import com.example.ui.screens.WalletHomeScreen
import com.example.ui.theme.GramVaultTheme
import java.io.File
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
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SecurityAndIosSheetRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun wallet_home_ios_sheet_host_opens_receive_and_settings_sheets() {
    val metadata =
      VaultMetadata(
        publicKeyHex = "11".repeat(32),
        mainnetRaw = "0:" + "11".repeat(32),
        testnetRaw = "0:" + "11".repeat(32),
        isBackedUp = true,
        createdAtEpochMs = 1_700_000_000_000L,
        securityMode = VaultSecurityMode.APP_PIN_WITH_KEYSTORE,
      )
    val balanceState =
      BalanceState(
        network = Network.MAINNET,
        address = metadata.addressFor(Network.MAINNET),
        gramBalance = Money.ofNano(3_500_000_000L),
      )
    var setPinClicked = 0
    var useBiometricClicked = 0

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
          isDeviceSecure = true,
          onSetOrChangePin = { setPinClicked++ },
          onSwitchToDeviceBiometrics = { useBiometricClicked++ },
        )
      }
    }

    composeTestRule.onNodeWithTag("receive_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("receive_full_address_text").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("sheet_copy_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("open_settings_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_set_pin_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_use_biometric_button").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("settings_set_pin_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, setPinClicked)

    composeTestRule.onNodeWithTag("open_settings_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_use_biometric_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, useBiometricClicked)
  }

  @Test
  fun set_change_pin_and_switch_to_biometrics_enforce_auth_across_unlock_backup_and_send() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    File(context.filesDir, "vault").deleteRecursively()
    val db =
      Room.inMemoryDatabaseBuilder(context, GramVaultDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val txScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
      val nowMs = 1_795_000_000_000L
      val authGate = RecordingAuthGate(deviceSecure = true, nextResult = AuthResult.Authenticated)
      val cipher = TestAesCipherProvider().apply { isAuthenticated = true }
      val keyStore = SecureKeyStore(context, cipher)
      val metaStore = VaultMetadataStore(context)
      val gateway = StubSecurityGateway { nowMs }
      val txManager =
        TxManager(
          txDao = db.txDao(),
          chainGateway = gateway,
          clockMs = { nowMs },
          scope = txScope,
        )
      val core =
        WalletCoreImpl(
          applicationContext = context,
          authGate = authGate,
          secureKeyStore = keyStore,
          metadataStore = metaStore,
          txManager = txManager,
          devSkipAuth = false,
          clockMs = { nowMs },
        )

      val created = core.createWallet(Network.MAINNET).getOrThrow()
      created.mnemonic.wipe()
      gateway.senderAddressRaw = created.mainnetAddress.raw
      assertEquals(VaultSecurityMode.DEVICE_KEYSTORE, core.activeSecurityMode())
      assertEquals(listOf(AuthReason.CREATE_WALLET), authGate.reasons)

      // Switch from Device Biometrics -> Custom Vault PIN
      val pinMeta = core.setAppPin(newPin = "135790".toCharArray()).getOrThrow()
      assertEquals(VaultSecurityMode.APP_PIN_WITH_KEYSTORE, pinMeta.securityMode)
      assertEquals(AuthReason.CHANGE_SECURITY, authGate.reasons.last())

      // Lock & unlock: null PIN must require auth; wrong PIN rejected; valid PIN unlocks
      core.lockWallet()
      assertTrue(core.unlockWallet(Network.MAINNET, appPin = null).exceptionOrNull() is VaultAuthRequiredException)
      assertTrue(core.unlockWallet(Network.MAINNET, appPin = "000000".toCharArray()).exceptionOrNull() is VaultWrongPinException)
      assertEquals(VaultSecurityMode.APP_PIN_WITH_KEYSTORE, core.unlockWallet(Network.MAINNET, "135790".toCharArray()).getOrThrow().securityMode)

      // Reveal backup enforces PIN explicitly
      assertTrue(core.revealRecoveryPhrase(appPin = null).exceptionOrNull() is VaultAuthRequiredException)
      val revealed = core.revealRecoveryPhrase(appPin = "135790".toCharArray()).getOrThrow()
      assertEquals(12, revealed.wordCount)
      revealed.wipe()

      // Send enforces PIN explicitly
      val dest = TonAddress("0:" + "55".repeat(32), Network.MAINNET).toFriendly(true)
      val preview = core.prepareTransfer(dest, "0.5", null).getOrThrow()
      assertTrue(core.confirmAndSend(preview, appPin = null).exceptionOrNull() is VaultAuthRequiredException)

      // Verify & change PIN -> new PIN works, old PIN fails
      assertTrue(core.verifyAppPin("135790".toCharArray()).isSuccess)
      assertTrue(core.verifyAppPin("999999".toCharArray()).exceptionOrNull() is VaultWrongPinException)
      core.setAppPin(newPin = "246810".toCharArray(), currentPin = "135790".toCharArray()).getOrThrow()
      val sentWithNewPin = core.confirmAndSend(preview, appPin = "246810".toCharArray()).getOrThrow()
      assertTrue(sentWithNewPin.seqno >= 0)

      // Switch back to Phone Biometrics / Screen Lock -> requires current PIN + CHANGE_SECURITY prompt
      val reasonCountBefore = authGate.reasons.size
      val bioMeta = core.switchToDeviceBiometrics(currentPin = "246810".toCharArray()).getOrThrow()
      assertEquals(VaultSecurityMode.DEVICE_KEYSTORE, bioMeta.securityMode)
      assertEquals(reasonCountBefore + 1, authGate.reasons.size)
      assertEquals(AuthReason.CHANGE_SECURITY, authGate.reasons.last())

      // Biometric enforced again for unlock and backup reveal
      core.lockWallet()
      core.unlockWallet(Network.MAINNET).getOrThrow()
      assertEquals(AuthReason.UNLOCK_WALLET, authGate.reasons.last())
      core.revealRecoveryPhrase().getOrThrow().wipe()
      assertEquals(AuthReason.VIEW_RECOVERY_PHRASE, authGate.reasons.last())
    } finally {
      txScope.cancel()
      db.close()
    }
  }

  @Test
  fun wallet_core_factory_enforces_auth_and_autolock_on_non_emulator_hardware() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val deps =
      WalletCoreFactory.createDependencies(
        applicationContext = context,
        authGate = RecordingAuthGate(deviceSecure = true, nextResult = AuthResult.Authenticated),
      )
    assertFalse("Non-emulator devices must never bypass authentication", deps.walletCore.isDevAuthBypassed)
    val settings = deps.appSettingsStore.settingsFlow.first()
    assertTrue("Auto-lock on background must default ON on non-emulator devices", settings.autoLockOnBackground)
  }

  private class RecordingAuthGate(
    var deviceSecure: Boolean,
    var nextResult: AuthResult,
  ) : AuthGate {
    val reasons = mutableListOf<AuthReason>()

    override fun isDeviceSecure(): Boolean = deviceSecure

    override suspend fun authenticate(reason: AuthReason): AuthResult {
      reasons += reason
      return nextResult
    }
  }

  private class TestAesCipherProvider : KeystoreCipherProvider {
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

  private class StubSecurityGateway(
    private val nowProvider: () -> Long,
  ) : ChainGateway {
    var senderAddressRaw: String = ""

    override suspend fun fetchAccountBalance(address: TonAddress): Result<RemoteAccountBalance> =
      Result.success(
        RemoteAccountBalance(
          address = address,
          balance = Money.ofNano(5_000_000_000L),
          status = AccountOnChainStatus.ACTIVE,
          providerName = "TonAPI",
          fetchedAtEpochMs = nowProvider(),
        )
      )

    override suspend fun fetchJettonBalance(
      ownerAddress: TonAddress,
      spec: AllowedJettonSpec,
    ): Result<RemoteJettonBalance> =
      Result.success(
        RemoteJettonBalance(
          ownerAddress = ownerAddress,
          jettonMaster = spec.masterAddress,
          jettonWalletAddress = null,
          balance = Money.zero(USDT_DECIMALS),
          providerName = "TonAPI",
          fetchedAtEpochMs = nowProvider(),
        )
      )

    override suspend fun fetchAllJettons(ownerAddress: TonAddress): Result<RemoteJettonHoldingsPage> =
      Result.success(RemoteJettonHoldingsPage(emptyList<JettonHolding>(), "TonAPI", nowProvider()))

    override suspend fun fetchWalletInfo(address: TonAddress): Result<RemoteWalletInfo> =
      Result.success(
        RemoteWalletInfo(
          address = address,
          balance = Money.ofNano(5_000_000_000L),
          status = AccountOnChainStatus.ACTIVE,
          seqno = 1,
          providerName = "TonAPI",
          fetchedAtEpochMs = nowProvider(),
        )
      )

    override suspend fun estimateExternalMessageFee(address: TonAddress, bocBase64: String) =
      Result.success(RemoteFeeEstimate(Money.ofNano(5_000_000L), "TonAPI"))

    override suspend fun sendBoc(network: Network, bocBase64: String): Result<BroadcastResult> =
      Result.success(BroadcastResult("stub_hash", "TonAPI"))

    override suspend fun lookupMessageStatus(
      network: Network,
      messageHashHex: String,
      normalizedHashHex: String,
    ) = Result.success(RemoteTxLookup(OnChainMessageStatus.PENDING, null, null, null, "TonAPI"))

    override suspend fun lookupJettonTraceStatus(
      network: Network,
      messageHashHex: String,
      normalizedHashHex: String,
    ) = Result.success(RemoteTxLookup(OnChainMessageStatus.PENDING, null, null, null, "TonAPI"))

    override suspend fun fetchAccountHistory(
      address: TonAddress,
      afterLt: String?,
      limit: Int,
    ): Result<RemoteTxHistoryPage> =
      Result.success(RemoteTxHistoryPage(emptyList(), "TonAPI"))

    override suspend fun fetchJettonHistory(
      ownerAddress: TonAddress,
      spec: AllowedJettonSpec,
      limit: Int,
    ): Result<RemoteJettonHistoryPage> =
      Result.success(RemoteJettonHistoryPage(emptyList(), "TonAPI"))
  }
}
