package com.example.core.model

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import org.ton.cell.Cell

enum class SlippageTolerance(
  val label: String,
  val fraction: Double,
  val bps: Int,
) {
  BPS_50("0.5%", 0.005, 50),
  BPS_100("1%", 0.01, 100),
  BPS_300("3%", 0.03, 300),
  BPS_500("5%", 0.05, 500),
}

data class SwapAsset(
  val addressOrNative: String,
  val rawAddress: String?,
  val symbol: String,
  val name: String,
  val decimals: Int,
  val imageUrl: String? = null,
  val verification: JettonVerificationStatus = JettonVerificationStatus.UNVERIFIED,
  val priceUsd: BigDecimal? = null,
) {
  val isNative: Boolean
    get() = addressOrNative.equals(NATIVE_TOKEN_ADDRESS, ignoreCase = true)

  val id: String
    get() = if (isNative) NATIVE_TOKEN_ADDRESS else (rawAddress ?: addressOrNative)

  companion object {
    const val NATIVE_TOKEN_ADDRESS = "native"
  }
}

data class SwapQuote(
  val inputAsset: SwapAsset,
  val outputAsset: SwapAsset,
  val inputAmount: Money,
  val expectedOutputAmount: Money,
  val minReceivedAmount: Money,
  val slippage: SlippageTolerance,
  val inputUsd: Double? = null,
  val outputUsd: Double? = null,
  val priceImpactPercent: Double? = null,
  val recommendedGas: Money,
  val routeDexLabels: List<String> = emptyList(),
  val rawPathsJson: String,
  val fetchedAtEpochMs: Long,
) {
  fun formatExchangeRate(): String {
    if (inputAmount.units <= BigInteger.ZERO || expectedOutputAmount.units <= BigInteger.ZERO) {
      return "--"
    }
    val inCoins = BigDecimal(inputAmount.units).movePointLeft(inputAmount.decimals)
    val outCoins = BigDecimal(expectedOutputAmount.units).movePointLeft(expectedOutputAmount.decimals)
    if (inCoins <= BigDecimal.ZERO) return "--"
    val rate = outCoins.divide(inCoins, 6, RoundingMode.HALF_UP).stripTrailingZeros()
    val plain = if (rate.scale() <= 0) rate.toPlainString() else rate.toPlainString()
    return "1 ${inputAsset.symbol} ≈ $plain ${outputAsset.symbol}"
  }
}

data class SwapInternalMessage(
  val destination: TonAddress,
  val attachedTonNano: BigInteger,
  val bounce: Boolean = true,
  val payloadCell: Cell,
  val sendMode: Int = 3,
)

data class SwapPreview(
  val network: Network,
  val fromAddress: TonAddress,
  val quote: SwapQuote,
  val internalMessages: List<SwapInternalMessage>,
  val totalAttachedTon: Money,
  val estimatedNetworkFee: Money,
  val totalTonRequired: Money,
  val seqno: Int,
  val senderNeedsStateInit: Boolean,
  val validUntilEpochSec: Long,
  val feeIsEstimateFallback: Boolean = false,
  val senderJettonBalance: Money? = null,
) {
  val isNativeInput: Boolean
    get() = quote.inputAsset.isNative

  val routeSummary: String
    get() =
      if (quote.routeDexLabels.isNotEmpty()) {
        quote.routeDexLabels.joinToString(" • ")
      } else {
        "swap.coffee Aggregator"
      }
}
