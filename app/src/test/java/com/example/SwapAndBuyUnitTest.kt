package com.example

import com.example.core.model.GRAM_DECIMALS
import com.example.core.model.JettonAllowList
import com.example.core.model.JettonVerificationStatus
import com.example.core.model.Money
import com.example.core.model.Network
import com.example.core.model.SlippageTolerance
import com.example.core.model.SwapAsset
import com.example.core.model.TonAddress
import com.example.core.model.WalletVersion
import com.example.core.network.InvalidPayloadException
import com.example.core.network.SwapCoffeeService
import com.example.core.wallet.BuyAssetOption
import com.example.core.wallet.BuyOnRampHelper
import com.example.core.wallet.BuyProvider
import com.example.core.wallet.RawInternalMessage
import com.example.core.wallet.TransferBuilder
import java.math.BigInteger
import java.util.Base64
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.boc.BagOfCells
import org.ton.cell.CellBuilder

class SwapAndBuyUnitTest {

  private fun tonAsset(): SwapAsset =
    SwapCoffeeService.curatedDefaultAssets().first { it.isNative }

  private fun usdtAsset(): SwapAsset =
    SwapCoffeeService.curatedDefaultAssets().first { it.symbol == "USDT" }

  @Test
  fun buy_onramp_helper_checks_secrets_and_builds_encoded_mainnet_urls() {
    assertFalse(BuyOnRampHelper.isProviderConfigured(BuyProvider.MERCURYO, mercuryoWidgetId = "", onramperApiKey = ""))
    assertFalse(
      BuyOnRampHelper.isProviderConfigured(
        BuyProvider.MERCURYO,
        mercuryoWidgetId = "YOUR_MERCURYO_WIDGET_ID",
        onramperApiKey = "YOUR_ONRAMPER_API_KEY",
      )
    )
    assertFalse(
      BuyOnRampHelper.isProviderConfigured(
        BuyProvider.ONRAMPER,
        mercuryoWidgetId = "widget123",
        onramperApiKey = "YOUR_ONRAMPER_API_KEY",
      )
    )
    assertTrue(
      BuyOnRampHelper.isProviderConfigured(
        BuyProvider.MERCURYO,
        mercuryoWidgetId = "widget-prod-99",
        onramperApiKey = "",
      )
    )
    assertTrue(
      BuyOnRampHelper.isProviderConfigured(
        BuyProvider.ONRAMPER,
        mercuryoWidgetId = "",
        onramperApiKey = "pk_live_abc123",
      )
    )

    val mainnetAddr = TonAddress("0:" + "ab".repeat(32), Network.MAINNET)
    val friendly = mainnetAddr.toFriendly(bounceable = false)

    assertNull(
      BuyOnRampHelper.buildCheckoutUrl(
        provider = BuyProvider.MERCURYO,
        walletAddress = mainnetAddr,
        asset = BuyAssetOption.TON,
        mercuryoWidgetId = "YOUR_MERCURYO_WIDGET_ID",
        onramperApiKey = "",
      )
    )

    val mercuryoUsdtUrl =
      BuyOnRampHelper.buildCheckoutUrl(
        provider = BuyProvider.MERCURYO,
        walletAddress = mainnetAddr,
        asset = BuyAssetOption.USDT,
        mercuryoWidgetId = "w_gramvault",
        onramperApiKey = "",
      )
    assertNotNull(mercuryoUsdtUrl)
    assertTrue(mercuryoUsdtUrl!!.startsWith("https://exchange.mercuryo.io/?"))
    assertTrue(mercuryoUsdtUrl.contains("widget_id=w_gramvault"))
    assertTrue(mercuryoUsdtUrl.contains("currency=USDTTON"))
    assertTrue(mercuryoUsdtUrl.contains("fix_currency=true"))
    assertTrue(mercuryoUsdtUrl.contains("address="))

    val onramperTonUrl =
      BuyOnRampHelper.buildCheckoutUrl(
        provider = BuyProvider.ONRAMPER,
        walletAddress = mainnetAddr,
        asset = BuyAssetOption.TON,
        mercuryoWidgetId = "",
        onramperApiKey = "pk_prod_42",
      )
    assertNotNull(onramperTonUrl)
    assertTrue(onramperTonUrl!!.startsWith("https://buy.onramper.com/?"))
    assertTrue(onramperTonUrl.contains("apiKey=pk_prod_42"))
    assertTrue(onramperTonUrl.contains("defaultCrypto=TON"))
    assertTrue(onramperTonUrl.contains(friendly.take(8)))

    // Testnet addresses must be rejected by fiat on-ramps
    val testnetAddr = TonAddress("0:" + "ab".repeat(32), Network.TESTNET)
    val failedOnTestnet =
      runCatching {
        BuyOnRampHelper.buildCheckoutUrl(
          provider = BuyProvider.MERCURYO,
          walletAddress = testnetAddr,
          asset = BuyAssetOption.TON,
          mercuryoWidgetId = "w_gramvault",
          onramperApiKey = "",
        )
      }
    assertTrue(failedOnTestnet.isFailure)
  }

  @Test
  fun swap_coffee_parseRouteResponse_computes_min_received_rate_impact_and_dex_labels() {
    val service =
      SwapCoffeeService(
        okHttpClient = OkHttpClient(),
        apiKey = "YOUR_SWAP_COFFEE_API_KEY",
        referralName = "YOUR_SWAP_COFFEE_REFERRAL_NAME",
        clockMs = { 1_750_000_000_000L },
      )
    val routeJson =
      """
      {
        "input_amount": "10.0",
        "output_amount": "32.5",
        "input_usd": 35.0,
        "output_usd": 34.4,
        "price_impact": -0.017,
        "recommended_gas": 0.25,
        "paths": [
          {
            "dex": "stonfi_v2",
            "input_amount": "5.0",
            "output_amount": "16.25"
          },
          {
            "dex": "dedust",
            "input_amount": "5.0",
            "output_amount": "16.25",
            "next": [
              { "dex": "tonco" }
            ]
          }
        ]
      }
      """.trimIndent()

    val quote =
      service.parseRouteResponse(
        jsonBody = routeJson,
        inputAsset = tonAsset(),
        outputAsset = usdtAsset(),
        inputAmount = Money.ofNano(10_000_000_000L),
        slippage = SlippageTolerance.BPS_100, // 1%
      )

    // 32.5 USDT = 32_500_000 units; 1% slippage => 32_175_000 units = 32.175 USDT
    assertEquals(BigInteger.valueOf(32_500_000L), quote.expectedOutputAmount.units)
    assertEquals(BigInteger.valueOf(32_175_000L), quote.minReceivedAmount.units)
    assertEquals(BigInteger.valueOf(250_000_000L), quote.recommendedGas.units)
    assertEquals(1.7, quote.priceImpactPercent!!, 0.01)
    assertTrue(quote.routeDexLabels.containsAll(listOf("STON.fi", "DeDust", "TONCO")))
    assertTrue(quote.formatExchangeRate().contains("3.25"))
  }

  @Test
  fun swap_coffee_parseTransactionsResponse_decodes_bocs_and_enforces_gas_safety_guard() {
    val service = SwapCoffeeService(okHttpClient = OkHttpClient(), clockMs = { 1_750_000_000_000L })
    val emptyPayloadCell = CellBuilder.createCell { storeUInt(0x12345678L, 32) }
    val payloadB64 = Base64.getEncoder().encodeToString(BagOfCells(emptyPayloadCell).toByteArray())

    val dest1 = TonAddress("0:" + "12".repeat(32), Network.MAINNET).toFriendly(bounceable = true)
    val dest2 = TonAddress("0:" + "34".repeat(32), Network.MAINNET).toFriendly(bounceable = true)

    val dummyQuote =
      service.parseRouteResponse(
        jsonBody = """{"output_amount":"15.0","recommended_gas":0.2,"paths":[{"dex":"stonfi"}]}""",
        inputAsset = tonAsset(),
        outputAsset = usdtAsset(),
        inputAmount = Money.ofNano(5_000_000_000L),
        slippage = SlippageTolerance.BPS_100,
      )

    // Valid split route with 2 messages totaling 5.2 TON (5 TON swap + 0.2 TON gas)
    val validTxJson =
      """
      {
        "transactions": [
          { "address": "$dest1", "value": "3100000000", "cell": "$payloadB64", "send_mode": 3 },
          { "address": "$dest2", "value": "2100000000", "cell": "$payloadB64", "send_mode": 3 }
        ]
      }
      """.trimIndent()
    val msgs = service.parseTransactionsResponse(validTxJson, dummyQuote)
    assertEquals(2, msgs.size)
    assertEquals(BigInteger.valueOf(3_100_000_000L), msgs[0].attachedTonNano)
    assertEquals(BigInteger.valueOf(2_100_000_000L), msgs[1].attachedTonNano)

    // Safety guard rejects excessive TON attachment (> input + MAX_SWAP_GAS_GUARD_NANO)
    val maliciousTxJson =
      """
      {
        "transactions": [
          { "address": "$dest1", "value": "9500000000", "cell": "$payloadB64", "send_mode": 3 }
        ]
      }
      """.trimIndent()
    val excessiveErr =
      runCatching { service.parseTransactionsResponse(maliciousTxJson, dummyQuote) }.exceptionOrNull()
    assertTrue(excessiveErr is InvalidPayloadException)

    // Rejects corrupted BOC payload
    val corruptBocJson =
      """
      {
        "transactions": [
          { "address": "$dest1", "value": "5200000000", "cell": "not_valid_base64_boc!!!", "send_mode": 3 }
        ]
      }
      """.trimIndent()
    assertTrue(runCatching { service.parseTransactionsResponse(corruptBocJson, dummyQuote) }.isFailure)
  }

  @Test
  fun swap_coffee_parseStonFiAssetsResponse_filters_blacklisted_unverified_and_scam_tokens() {
    val service = SwapCoffeeService(okHttpClient = OkHttpClient())
    val notMaster = "EQAvlWFDxGF2lXm67y4yzC17wYKD9A0guwPkMs1gOsM__NOT"
    val blacklistMaster = TonAddress("0:" + "aa".repeat(32), Network.MAINNET).toFriendly(true)
    val scamMaster = TonAddress("0:" + "bb".repeat(32), Network.MAINNET).toFriendly(true)
    val unverifiedMaster = TonAddress("0:" + "cc".repeat(32), Network.MAINNET).toFriendly(true)

    val stonJson =
      """
      {
        "asset_list": [
          {
            "kind": "ton",
            "contract_address": "EQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAM9c",
            "symbol": "TON",
            "display_name": "Toncoin",
            "decimals": 9,
            "dex_price_usd": "3.45"
          },
          {
            "kind": "jetton",
            "contract_address": "$notMaster",
            "symbol": "NOT",
            "display_name": "Notcoin",
            "decimals": 9,
            "blacklisted": false,
            "deprecated": false,
            "community": false,
            "default_symbol": true,
            "dex_price_usd": "0.008"
          },
          {
            "kind": "jetton",
            "contract_address": "$blacklistMaster",
            "symbol": "EVIL",
            "display_name": "Evil Token",
            "decimals": 9,
            "blacklisted": true,
            "default_symbol": true
          },
          {
            "kind": "jetton",
            "contract_address": "$scamMaster",
            "symbol": "TONGIFT",
            "display_name": "Claim AIRDROP at ton-gift.io",
            "decimals": 9,
            "blacklisted": false,
            "default_symbol": true
          },
          {
            "kind": "jetton",
            "contract_address": "$unverifiedMaster",
            "symbol": "RAND",
            "display_name": "Random Meme",
            "decimals": 9,
            "blacklisted": false,
            "community": true,
            "default_symbol": false
          }
        ]
      }
      """.trimIndent()

    val assets = service.parseStonFiAssetsResponse(stonJson)
    assertEquals(2, assets.size)
    assertTrue(assets.any { it.isNative && it.symbol == "GRAM" })
    assertTrue(assets.any { it.symbol == "NOT" && it.verification == JettonVerificationStatus.VERIFIED })
  }

  @Test
  fun transfer_builder_builds_valid_multi_message_bocs_across_wallet_versions() {
    val seed = ByteArray(32) { idx -> (idx + 7).toByte() }
    val pubKey = org.ton.kotlin.crypto.PrivateKeyEd25519(seed).publicKey().key.toByteArray()
    val d1 = TonAddress("0:" + "11".repeat(32), Network.MAINNET)
    val d2 = TonAddress("0:" + "22".repeat(32), Network.MAINNET)
    val body1 = CellBuilder.createCell { storeUInt(0x1111L, 32) }
    val body2 = CellBuilder.createCell { storeUInt(0x2222L, 32) }
    val msgs =
      listOf(
        RawInternalMessage(d1, BigInteger.valueOf(1_000_000_000L), bounce = true, bodyCell = body1),
        RawInternalMessage(d2, BigInteger.valueOf(2_000_000_000L), bounce = true, bodyCell = body2),
      )

    for (version in listOf(WalletVersion.V5R1, WalletVersion.V4R2, WalletVersion.V3R2, WalletVersion.V3R1)) {
      val sender =
        com.example.core.wallet.AddressService.deriveAddress(pubKey, version, Network.MAINNET)
      val signed =
        TransferBuilder.buildSignedMultiTransfer(
          seed32 = seed,
          publicKey32 = pubKey,
          senderAddress = sender,
          walletVersion = version,
          messages = msgs,
          seqno = 3,
          validUntilEpochSec = 1_900_000_060L,
          needsStateInit = true,
        )
      assertTrue(signed.externalBocBase64.isNotBlank())
      val decoded = BagOfCells(Base64.getDecoder().decode(signed.externalBocBase64))
      assertTrue(decoded.roots.isNotEmpty())
      assertEquals(64, signed.messageHashHex.length)
      assertEquals(64, signed.normalizedHashHex.length)
    }

    // Rejects > 4 messages
    val fiveMsgs = List(5) { msgs.first() }
    val v5Sender =
      com.example.core.wallet.AddressService.deriveAddress(pubKey, WalletVersion.V5R1, Network.MAINNET)
    assertTrue(
      runCatching {
        TransferBuilder.buildSignedMultiTransfer(
          seed32 = seed,
          publicKey32 = pubKey,
          senderAddress = v5Sender,
          walletVersion = WalletVersion.V5R1,
          messages = fiveMsgs,
          seqno = 0,
          validUntilEpochSec = 1_900_000_060L,
          needsStateInit = false,
        )
      }.isFailure
    )
  }

  @Test
  fun curated_gram_asset_uses_official_ton_logo_url() {
    val gram = tonAsset()
    assertTrue(gram.isNative)
    assertEquals("GRAM", gram.symbol)
    assertEquals(SwapCoffeeService.OFFICIAL_TON_LOGO_URL, gram.imageUrl)
  }
}

