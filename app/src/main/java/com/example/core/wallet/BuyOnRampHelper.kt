package com.example.core.wallet

import com.example.core.model.Network
import com.example.core.model.TonAddress
import com.gramvault.wallet.BuildConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class BuyProvider(
  val id: String,
  val secretFieldName: String,
) {
  MERCURYO("mercuryo", "MERCURYO_WIDGET_ID"),
  ONRAMPER("onramper", "ONRAMPER_API_KEY"),
}

enum class BuyAssetOption(
  val tickerLabel: String,
  val mercuryoCurrency: String,
  val onramperCryptoCode: String,
) {
  TON("TON (GRAM)", "TON", "TON"),
  USDT("USDT (TON)", "USDTTON", "USDT_TON"),
}

object BuyOnRampHelper {

  fun isProviderConfigured(
    provider: BuyProvider,
    mercuryoWidgetId: String = BuildConfig.MERCURYO_WIDGET_ID,
    onramperApiKey: String = BuildConfig.ONRAMPER_API_KEY,
  ): Boolean =
    when (provider) {
      BuyProvider.MERCURYO -> isUsableSecret(mercuryoWidgetId)
      BuyProvider.ONRAMPER -> isUsableSecret(onramperApiKey)
    }

  fun buildCheckoutUrl(
    provider: BuyProvider,
    walletAddress: TonAddress,
    asset: BuyAssetOption = BuyAssetOption.TON,
    mercuryoWidgetId: String = BuildConfig.MERCURYO_WIDGET_ID,
    onramperApiKey: String = BuildConfig.ONRAMPER_API_KEY,
  ): String? {
    require(walletAddress.network == Network.MAINNET) {
      "Fiat on-ramps only support TON Mainnet addresses"
    }
    val friendlyNonBounceable = walletAddress.toFriendly(bounceable = false)
    val encodedAddress = encodeParam(friendlyNonBounceable)

    return when (provider) {
      BuyProvider.MERCURYO -> {
        val widgetId = mercuryoWidgetId.trim()
        if (!isUsableSecret(widgetId)) return null
        val encodedWidget = encodeParam(widgetId)
        val encodedCurrency = encodeParam(asset.mercuryoCurrency)
        "https://exchange.mercuryo.io/?widget_id=$encodedWidget&type=buy&currency=$encodedCurrency&fix_currency=true&address=$encodedAddress"
      }
      BuyProvider.ONRAMPER -> {
        val apiKey = onramperApiKey.trim()
        if (!isUsableSecret(apiKey)) return null
        val encodedKey = encodeParam(apiKey)
        val encodedCrypto = encodeParam(asset.onramperCryptoCode)
        val walletPair = encodeParam("${asset.onramperCryptoCode}:$friendlyNonBounceable")
        "https://buy.onramper.com/?apiKey=$encodedKey&mode=buy&defaultCrypto=$encodedCrypto&wallets=$walletPair"
      }
    }
  }

  private fun isUsableSecret(raw: String): Boolean {
    val clean = raw.trim().trim('"')
    if (clean.isEmpty()) return false
    if (clean.startsWith("YOUR_", ignoreCase = true) || clean.startsWith("MY_", ignoreCase = true)) {
      return false
    }
    return true
  }

  private fun encodeParam(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
