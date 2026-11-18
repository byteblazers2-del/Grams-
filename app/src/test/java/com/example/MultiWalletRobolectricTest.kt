package com.example

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.GramVaultDatabase
import com.example.core.keystore.AndroidKeyStoreCipherProvider
import com.example.core.keystore.AuthGate
import com.example.core.keystore.KeystoreCipherProvider
import com.example.core.keystore.SecureKeyStore
import com.example.core.keystore.VaultAuthRequiredException
import com.example.core.keystore.VaultKeyInvalidatedException
import com.example.core.model.AuthReason
import com.example.core.model.AuthResult
import com.example.core.model.BalanceState
import com.example.core.model.Money
import com.example.core.model.Network
import com.example.core.model.TonAddress
import com.example.core.model.VaultMetadata
import com.example.core.model.VaultSecurityMode
import com.example.core.model.VaultState
import com.example.core.wallet.VaultMetadataStore
import com.example.core.wallet.WalletCoreImpl
import com.example.ui.screens.WalletHomeScreen
import com.example.ui.theme.GramVaultTheme
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
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
class MultiWalletRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun multi_wallet_watch_only_switch_rename_delete_and_readonly_guards() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    File(context.filesDir, "vault").deleteRecursively()
    val db =
      Room.inMemoryDatabaseBuilder(context, GramVaultDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    try {
      val nowMs = 1_800_000_000_000L
      val authGate = StubAuthGate(deviceSecure = true, nextResult = AuthResult.Authenticated)
      val cipher = InMemoryAesCipherProvider()
      val keyStore = SecureKeyStore(context, cipher)
      val metaStore = VaultMetadataStore(context)
      val core =
        WalletCoreImpl(
          applicationContext = context,
          authGate = authGate,
          secureKeyStore = keyStore,
          metadataStore = metaStore,
          devSkipAuth = false,
          clockMs = { nowMs },
        )

      val created = core.createWallet(Network.MAINNET).getOrThrow()
      created.mnemonic.wipe()
      val firstWalletId = (core.vaultState.value as VaultState.Unlocked).metadata.walletId
      assertEquals(1, core.wallets.value.size)

      // Add second wallet as Watch-Only
      val watchFriendly = TonAddress("0:" + "77".repeat(32), Network.MAINNET).toFriendly(bounceable = false)
      val watchMeta = core.addWatchOnlyWallet(watchFriendly, "Friend Vault").getOrThrow()
      assertTrue(watchMeta.isWatchOnly)
      assertEquals("Friend Vault", watchMeta.walletName)
      assertEquals(2, core.wallets.value.size)
      assertEquals(watchMeta.walletId, (core.vaultState.value as VaultState.Unlocked).metadata.walletId)

      // Duplicate watch-only address is rejected
      assertTrue(core.addWatchOnlyWallet(watchFriendly, "Duplicate").isFailure)

      // Watch-Only strictly blocks signing / recovery / security edits
      val dest = TonAddress("0:" + "88".repeat(32), Network.MAINNET).toFriendly(true)
      assertTrue(core.prepareTransfer(dest, "1.0", null).isFailure)
      assertTrue(core.revealRecoveryPhrase().isFailure)
      assertTrue(core.setAppPin("123456".toCharArray()).isFailure)
      assertTrue(core.switchToDeviceBiometrics().isFailure)

      // Rename Watch-Only wallet
      val renamed = core.renameWallet(watchMeta.walletId, "Cold Tracker").getOrThrow()
      assertEquals("Cold Tracker", renamed.walletName)
      assertEquals("Cold Tracker", (core.vaultState.value as VaultState.Unlocked).metadata.walletName)

      // Switch back to first Personal Wallet and confirm its phrase is intact in isolated keystore
      val switchedPersonal = core.switchWallet(firstWalletId).getOrThrow()
      assertFalse(switchedPersonal.isWatchOnly)
      val revealedPhrase = core.revealRecoveryPhrase().getOrThrow()
      assertEquals(12, revealedPhrase.wordCount)
      revealedPhrase.wipe()

      // Switch back to Watch-Only without biometric prompt
      val authCountBeforeWatch = authGate.reasons.size
      val switchedWatch = core.switchWallet(watchMeta.walletId).getOrThrow()
      assertTrue(switchedWatch.isWatchOnly)
      assertEquals(authCountBeforeWatch, authGate.reasons.size)

      // Delete active Watch-Only -> falls back cleanly to remaining wallet
      val afterDeleteWatch = core.deleteWallet(watchMeta.walletId).getOrThrow()
      assertEquals(1, core.wallets.value.size)
      assertTrue(afterDeleteWatch is VaultState.Unlocked || afterDeleteWatch is VaultState.Locked)

      // Delete last remaining wallet -> Uninitialized
      val afterDeleteLast = core.deleteWallet(firstWalletId).getOrThrow()
      assertEquals(VaultState.Uninitialized, afterDeleteLast)
      assertTrue(core.wallets.value.isEmpty())
    } finally {
      db.close()
    }
  }

  @Test
  fun header_wallet_manager_modal_offers_personal_vs_watch_only_and_watch_only_ui() {
    val personal =
      VaultMetadata(
        publicKeyHex = "11".repeat(32),
        mainnetRaw = "0:" + "11".repeat(32),
        testnetRaw = "0:" + "11".repeat(32),
        isBackedUp = true,
        createdAtEpochMs = 1_700_000_000_000L,
        securityMode = VaultSecurityMode.DEVICE_KEYSTORE,
        walletId = "default",
        walletName = "Main Wallet",
        isWatchOnly = false,
      )
    val watchTargetFriendly =
      TonAddress("0:" + "66".repeat(32), Network.MAINNET).toFriendly(bounceable = false)
    var capturedWatchAddress = ""
    var capturedWatchLabel = ""

    composeTestRule.setContent {
      GramVaultTheme {
        WalletHomeScreen(
          metadata = personal,
          wallets = listOf(personal),
          activeNetwork = Network.MAINNET,
          bannerMessage = null,
          onClearBanner = {},
          onOpenBackup = {},
          onLockWallet = {},
          balanceState =
            BalanceState(
              network = Network.MAINNET,
              address = personal.addressFor(Network.MAINNET),
              gramBalance = Money.ofNano(2_000_000_000L),
            ),
          onAddWatchOnlyWallet = { addr, label, onSuccess ->
            capturedWatchAddress = addr
            capturedWatchLabel = label
            onSuccess()
          },
        )
      }
    }

    // Open Wallet Manager modal from header switcher button
    composeTestRule.onNodeWithTag("open_wallet_manager_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("wallet_item_default").assertIsDisplayed()
    composeTestRule.onNodeWithTag("wallet_manager_add_button").performClick()
    composeTestRule.waitForIdle()

    // Shows Personal Wallet & Watch-Only Wallet choices
    composeTestRule.onNodeWithTag("add_personal_wallet_option").assertIsDisplayed()
    composeTestRule.onNodeWithTag("add_watch_only_option").assertIsDisplayed()

    // Tapping Personal Wallet reveals Create New + Import Recovery Phrase
    composeTestRule.onNodeWithTag("add_personal_wallet_option").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("add_personal_create_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("add_personal_import_button").assertIsDisplayed()

    // Choose Watch-Only -> prompts ONLY address (+ optional label)
    composeTestRule.onNodeWithTag("add_watch_only_option").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("watch_only_address_input").performTextInput(watchTargetFriendly)
    composeTestRule.onNodeWithTag("watch_only_name_input").performTextInput("Whale Watch")
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("watch_only_submit_button").assertIsEnabled().performClick()
    composeTestRule.waitForIdle()

    assertEquals(watchTargetFriendly, capturedWatchAddress)
    assertEquals("Whale Watch", capturedWatchLabel)
  }

  @Test
  fun watch_only_home_screen_hides_send_backup_and_pin_controls() {
    val watchWallet =
      VaultMetadata(
        publicKeyHex = "",
        mainnetRaw = "0:" + "44".repeat(32),
        testnetRaw = "0:" + "44".repeat(32),
        isBackedUp = true,
        createdAtEpochMs = 1_700_000_000_000L,
        securityMode = VaultSecurityMode.DEVICE_KEYSTORE,
        walletId = "w_watch1",
        walletName = "Tracked Whale",
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
              gramBalance = Money.ofNano(9_000_000_000L),
            ),
        )
      }
    }

    composeTestRule.onNodeWithTag("watch_only_info_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("watch_only_badge").assertIsDisplayed()
    composeTestRule.onAllNodesWithTag("send_button").assertCountEquals(0)
    composeTestRule.onAllNodesWithTag("backup_phrase_button").assertCountEquals(0)

    composeTestRule.onNodeWithTag("open_settings_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithTag("settings_set_pin_button").assertCountEquals(0)
    composeTestRule.onAllNodesWithTag("settings_use_biometric_button").assertCountEquals(0)
  }

  private class StubAuthGate(
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
}
